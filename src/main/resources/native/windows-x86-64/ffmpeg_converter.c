#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/opt.h>
#include <libavutil/imgutils.h>
#include <libavutil/channel_layout.h>
#include <libavutil/audio_fifo.h>
#include <libavutil/mathematics.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

/* ------------------------------------------------------------------ */
/*  Opções de conversão escolhidas pelo usuário (Java)                 */
/* ------------------------------------------------------------------ */
typedef struct {
    char video_codec[32];   /* "copy", "libx264", "libx265", "libvpx-vp9", "mpeg4" */
    char audio_codec[32];   /* "copy", "aac", "libmp3lame", "libopus" */

    int  out_width;         /* 0 = manter original */
    int  out_height;        /* 0 = manter original */

    int64_t video_bitrate;  /* em bits/s, 0 = usar CRF/padrão do encoder */
    int64_t audio_bitrate;  /* em bits/s, 0 = padrão */

    int  crf;               /* qualidade (x264/x265), -1 = não usar */
    char preset[16];        /* "ultrafast".."veryslow", vazio = "medium" */

    int  max_threads;       /* 0 = auto (sem limite), N = limita a N threads */
} ConvertOptions;

/* ------------------------------------------------------------------ */
/*  Contexto principal da conversão                                    */
/* ------------------------------------------------------------------ */
typedef struct {
    AVFormatContext *in_fmt_ctx;
    AVFormatContext *out_fmt_ctx;

    /* Vídeo */
    int video_stream_idx_in;
    int video_stream_idx_out;
    AVCodecContext *video_dec_ctx;   /* decoder (NULL se "copy") */
    AVCodecContext *video_enc_ctx;   /* encoder (NULL se "copy") */
    struct SwsContext *sws_ctx;      /* conversão de resolução/pixfmt */
    AVFrame *dec_frame;
    AVFrame *scaled_frame;

    /* Áudio */
    int audio_stream_idx_in;
    int audio_stream_idx_out;
    AVCodecContext *audio_dec_ctx;   /* decoder (NULL se "copy") */
    AVCodecContext *audio_enc_ctx;   /* encoder (NULL se "copy") */
    SwrContext *swr_ctx;
    AVFrame *audio_dec_frame;
    int audio_frame_size;
    int64_t audio_pts_counter;
    AVAudioFifo *audio_fifo;         /* buffer para montar frames de tamanho fixo */

    AVPacket *packet;

    ConvertOptions opts;

    int64_t duration_total_us;   /* duração de entrada, para progresso */

    /* Pts do último pacote processado de CADA stream, em suas timebases
       ORIGINAIS de entrada. Mantidos separados para nunca misturar
       escalas de tempo diferentes no cálculo de progresso. */
    int64_t last_video_pts;
    int64_t last_audio_pts;

    int finished;
    int error;
    char error_msg[256];
} ConvertContext;

static ConvertContext *get_cctx(jlong ptr) {
    return (ConvertContext *)(uintptr_t)ptr;
}

static void set_error(ConvertContext *ctx, const char *msg) {
    ctx->error = 1;
    snprintf(ctx->error_msg, sizeof(ctx->error_msg), "%s", msg);
    fprintf(stderr, "[CONVERTER] Erro: %s\n", msg);
}

/* ------------------------------------------------------------------ */
/*  Helpers para obter formatos suportados (API nova, substitui        */
/*  os campos deprecated codec->pix_fmts / codec->sample_fmts)         */
/* ------------------------------------------------------------------ */
static enum AVPixelFormat get_default_pix_fmt(const AVCodec *codec) {
    const enum AVPixelFormat *fmts = NULL;
    int n = 0;

    if (avcodec_get_supported_config(NULL, codec, AV_CODEC_CONFIG_PIX_FORMAT, 0,
                                      (const void **)&fmts, &n) == 0 && fmts && n > 0) {
        /* Prefere yuv420p se o encoder suportar (maior compatibilidade) */
        for (int i = 0; i < n; i++) {
            if (fmts[i] == AV_PIX_FMT_YUV420P) return AV_PIX_FMT_YUV420P;
        }
        return fmts[0];
    }
    return AV_PIX_FMT_YUV420P;
}

static enum AVSampleFormat get_default_sample_fmt(const AVCodec *codec) {
    const enum AVSampleFormat *fmts = NULL;
    int n = 0;

    if (avcodec_get_supported_config(NULL, codec, AV_CODEC_CONFIG_SAMPLE_FORMAT, 0,
                                      (const void **)&fmts, &n) == 0 && fmts && n > 0) {
        return fmts[0];
    }
    return AV_SAMPLE_FMT_FLTP;
}

/* ------------------------------------------------------------------ */
/*  Abre o decoder de uma stream de entrada (vídeo ou áudio)           */
/* ------------------------------------------------------------------ */
static AVCodecContext *open_decoder(AVFormatContext *fmt_ctx, int stream_idx, int max_threads) {
    AVCodecParameters *par = fmt_ctx->streams[stream_idx]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) return NULL;

    AVCodecContext *dec_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(dec_ctx, par);
    dec_ctx->pkt_timebase = fmt_ctx->streams[stream_idx]->time_base;

    /* Limita threads do decoder (0 = deixa o FFmpeg decidir automaticamente) */
    dec_ctx->thread_count = max_threads > 0 ? max_threads : 0;

    if (avcodec_open2(dec_ctx, codec, NULL) < 0) {
        avcodec_free_context(&dec_ctx);
        return NULL;
    }
    return dec_ctx;
}

/* ------------------------------------------------------------------ */
/*  Escolhe o sample rate suportado pelo encoder mais proximo do       */
/*  original. Necessario porque alguns codecs (ex: libopus) so aceitam */
/*  um conjunto fixo de taxas (8000/12000/16000/24000/48000).          */
/* ------------------------------------------------------------------ */
static int get_supported_sample_rate(const AVCodec *codec, int desired_rate) {
    const int *rates = NULL;
    int n = 0;

    if (avcodec_get_supported_config(NULL, codec, AV_CODEC_CONFIG_SAMPLE_RATE, 0,
                                      (const void **)&rates, &n) != 0 || !rates || n <= 0) {
        return desired_rate; /* codec sem restricao conhecida, mantem original */
    }

    for (int i = 0; i < n; i++) {
        if (rates[i] == desired_rate) return desired_rate;
    }

    int best = rates[0];
    int best_diff = abs(rates[0] - desired_rate);
    for (int i = 1; i < n; i++) {
        int diff = abs(rates[i] - desired_rate);
        if (diff < best_diff) {
            best = rates[i];
            best_diff = diff;
        }
    }
    return best;
}
/* ------------------------------------------------------------------ */
/*  Ajusta o layout de canais para um suportado pelo encoder.          */
/*  Necessario porque alguns codecs (ex: libmp3lame) so aceitam        */
/*  mono/estereo, mesmo que a fonte seja 5.1/7.1.                      */
/* ------------------------------------------------------------------ */
static void clamp_channel_layout(const AVCodec *codec,
                                  const AVChannelLayout *desired,
                                  AVChannelLayout *out) {
    const AVChannelLayout *layouts = NULL;
    int n = 0;

    if (avcodec_get_supported_config(NULL, codec, AV_CODEC_CONFIG_CHANNEL_LAYOUT, 0,
                                      (const void **)&layouts, &n) != 0 || !layouts || n <= 0) {
        av_channel_layout_copy(out, desired); /* sem restricao conhecida */
        return;
    }

    /* Match exato */
    for (int i = 0; i < n; i++) {
        if (av_channel_layout_compare(&layouts[i], desired) == 0) {
            av_channel_layout_copy(out, desired);
            return;
        }
    }

    /* Sem match: prefere estereo se o encoder suportar */
    for (int i = 0; i < n; i++) {
        if (layouts[i].nb_channels == 2) {
            av_channel_layout_copy(out, &layouts[i]);
            return;
        }
    }

    /* Ultimo recurso: primeiro layout suportado pelo encoder */
    av_channel_layout_copy(out, &layouts[0]);
}

/* ------------------------------------------------------------------ */
/*  Configura o encoder de vídeo com base nas opções do usuário        */
/* ------------------------------------------------------------------ */
static AVCodecContext *setup_video_encoder(ConvertContext *ctx,
                                            AVCodecContext *dec_ctx,
                                            AVFormatContext *ofmt_ctx) {
    const AVCodec *enc = avcodec_find_encoder_by_name(ctx->opts.video_codec);
    if (!enc) { set_error(ctx, "Codec de video de saida nao encontrado"); return NULL; }

    AVCodecContext *enc_ctx = avcodec_alloc_context3(enc);

    int out_w = ctx->opts.out_width  > 0 ? ctx->opts.out_width  : dec_ctx->width;
    int out_h = ctx->opts.out_height > 0 ? ctx->opts.out_height : dec_ctx->height;
    out_w -= out_w % 2;
    out_h -= out_h % 2;

    enc_ctx->width     = out_w;
    enc_ctx->height    = out_h;
    enc_ctx->pix_fmt   = get_default_pix_fmt(enc);
    enc_ctx->time_base = (AVRational){1, 25}; /* fallback neutro */

    /* ── CORREÇÃO: reduz a fracao do framerate para caber no limite de
       denominador de codecs antigos como MPEG-4 (max 65535) ── */
    AVRational fr = dec_ctx->framerate;
    if (fr.num > 0 && fr.den > 0) {
        int out_num, out_den;
        av_reduce(&out_num, &out_den, fr.den, fr.num, 65535);
        if (out_num > 0 && out_den > 0) {
            enc_ctx->time_base = (AVRational){out_num, out_den};
        }
    }

    if (ctx->opts.video_bitrate > 0) {
        enc_ctx->bit_rate = ctx->opts.video_bitrate;
    }
    if (ctx->opts.crf >= 0) {
        av_opt_set_int(enc_ctx->priv_data, "crf", ctx->opts.crf, 0);
    }
    if (ctx->opts.preset[0]) {
        av_opt_set(enc_ctx->priv_data, "preset", ctx->opts.preset, 0);
    }

    if (ctx->opts.max_threads > 0) {
        enc_ctx->thread_count = ctx->opts.max_threads;
        enc_ctx->thread_type = FF_THREAD_SLICE;
        av_opt_set_int(enc_ctx->priv_data, "threads", ctx->opts.max_threads, 0);
    }

    if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        enc_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

    if (avcodec_open2(enc_ctx, enc, NULL) < 0) {
        set_error(ctx, "Falha ao abrir encoder de video");
        avcodec_free_context(&enc_ctx);
        return NULL;
    }
    return enc_ctx;
}


/* ------------------------------------------------------------------ */
/*  Configura o encoder de áudio com base nas opções do usuário        */
/* ------------------------------------------------------------------ */
static AVCodecContext *setup_audio_encoder(ConvertContext *ctx,
                                            AVCodecContext *dec_ctx,
                                            AVFormatContext *ofmt_ctx) {
    const AVCodec *enc = avcodec_find_encoder_by_name(ctx->opts.audio_codec);
    if (!enc) { set_error(ctx, "Codec de audio de saida nao encontrado"); return NULL; }

    AVCodecContext *enc_ctx = avcodec_alloc_context3(enc);

    /* ── CORREÇÃO: escolhe um sample rate que o encoder de fato suporta
       (ex: libopus so aceita 8k/12k/16k/24k/48k) ── */
    enc_ctx->sample_rate = get_supported_sample_rate(enc, dec_ctx->sample_rate);

    clamp_channel_layout(enc, &dec_ctx->ch_layout, &enc_ctx->ch_layout);
    enc_ctx->sample_fmt  = get_default_sample_fmt(enc);
    enc_ctx->time_base   = (AVRational){1, enc_ctx->sample_rate};

    if (ctx->opts.audio_bitrate > 0) enc_ctx->bit_rate = ctx->opts.audio_bitrate;

    if (ofmt_ctx->oformat->flags & AVFMT_GLOBALHEADER)
        enc_ctx->flags |= AV_CODEC_FLAG_GLOBAL_HEADER;

    if (avcodec_open2(enc_ctx, enc, NULL) < 0) {
        set_error(ctx, "Falha ao abrir encoder de audio");
        avcodec_free_context(&enc_ctx);
        return NULL;
    }
    return enc_ctx;
}

/* ------------------------------------------------------------------ */
/*  openConverter — prepara entrada, saída, decoders e encoders        */
/* ------------------------------------------------------------------ */
JNIEXPORT jlong JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_openConverter(
        JNIEnv *env, jobject obj,
        jstring jin, jstring jout,
        jstring jVideoCodec, jstring jAudioCodec,
        jint outWidth, jint outHeight,
        jlong videoBitrate, jlong audioBitrate,
        jint crf, jstring jPreset,
        jint maxThreads) {

    const char *in_path     = (*env)->GetStringUTFChars(env, jin, NULL);
    const char *out_path    = (*env)->GetStringUTFChars(env, jout, NULL);
    const char *video_codec = (*env)->GetStringUTFChars(env, jVideoCodec, NULL);
    const char *audio_codec = (*env)->GetStringUTFChars(env, jAudioCodec, NULL);
    const char *preset      = jPreset ? (*env)->GetStringUTFChars(env, jPreset, NULL) : "";

    ConvertContext *ctx = calloc(1, sizeof(ConvertContext));
    snprintf(ctx->opts.video_codec, sizeof(ctx->opts.video_codec), "%s", video_codec);
    snprintf(ctx->opts.audio_codec, sizeof(ctx->opts.audio_codec), "%s", audio_codec);
    snprintf(ctx->opts.preset,      sizeof(ctx->opts.preset),      "%s", preset);
    ctx->opts.out_width     = outWidth;
    ctx->opts.out_height    = outHeight;
    ctx->opts.video_bitrate = videoBitrate;
    ctx->opts.audio_bitrate = audioBitrate;
    ctx->opts.crf           = crf;
    ctx->opts.max_threads   = maxThreads;

    /* ── Abrir entrada ── */
    if (avformat_open_input(&ctx->in_fmt_ctx, in_path, NULL, NULL) < 0) {
        set_error(ctx, "Nao foi possivel abrir o arquivo de entrada");
        goto cleanup_strings;
    }
    avformat_find_stream_info(ctx->in_fmt_ctx, NULL);
    ctx->duration_total_us = ctx->in_fmt_ctx->duration;

    ctx->video_stream_idx_in = av_find_best_stream(ctx->in_fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    ctx->audio_stream_idx_in = av_find_best_stream(ctx->in_fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);

    /* ── Criar contexto de saída (formato inferido pela extensão) ── */
    avformat_alloc_output_context2(&ctx->out_fmt_ctx, NULL, NULL, out_path);
    if (!ctx->out_fmt_ctx) {
        set_error(ctx, "Formato de saida nao suportado / extensao invalida");
        goto cleanup_output;
    }

    /* ── Stream de vídeo de saída ── */
    if (ctx->video_stream_idx_in >= 0) {
        AVStream *out_stream = avformat_new_stream(ctx->out_fmt_ctx, NULL);
        ctx->video_stream_idx_out = out_stream->index;

        if (strcmp(ctx->opts.video_codec, "copy") == 0) {
            avcodec_parameters_copy(out_stream->codecpar,
                ctx->in_fmt_ctx->streams[ctx->video_stream_idx_in]->codecpar);
            out_stream->codecpar->codec_tag = 0;
            out_stream->time_base = ctx->in_fmt_ctx->streams[ctx->video_stream_idx_in]->time_base;
        } else {
            ctx->video_dec_ctx = open_decoder(ctx->in_fmt_ctx, ctx->video_stream_idx_in, ctx->opts.max_threads);
            if (!ctx->video_dec_ctx) { set_error(ctx, "Falha ao abrir decoder de video"); goto cleanup_output; }

            ctx->video_enc_ctx = setup_video_encoder(ctx, ctx->video_dec_ctx, ctx->out_fmt_ctx);
            if (!ctx->video_enc_ctx) goto cleanup_output;

            avcodec_parameters_from_context(out_stream->codecpar, ctx->video_enc_ctx);
            out_stream->time_base = ctx->video_enc_ctx->time_base;

            /* Necessário se a resolução ou pixfmt mudarem */
            ctx->sws_ctx = sws_getContext(
                ctx->video_dec_ctx->width, ctx->video_dec_ctx->height, ctx->video_dec_ctx->pix_fmt,
                ctx->video_enc_ctx->width, ctx->video_enc_ctx->height, ctx->video_enc_ctx->pix_fmt,
                SWS_BILINEAR, NULL, NULL, NULL);

            ctx->dec_frame = av_frame_alloc();
            ctx->scaled_frame = av_frame_alloc();
            ctx->scaled_frame->format = ctx->video_enc_ctx->pix_fmt;
            ctx->scaled_frame->width  = ctx->video_enc_ctx->width;
            ctx->scaled_frame->height = ctx->video_enc_ctx->height;
            av_frame_get_buffer(ctx->scaled_frame, 0);
        }
    } else {
        ctx->video_stream_idx_out = -1;
    }

    /* ── Stream de áudio de saída ── */
    if (ctx->audio_stream_idx_in >= 0) {
        AVStream *out_stream = avformat_new_stream(ctx->out_fmt_ctx, NULL);
        ctx->audio_stream_idx_out = out_stream->index;

        if (strcmp(ctx->opts.audio_codec, "copy") == 0) {
            avcodec_parameters_copy(out_stream->codecpar,
                ctx->in_fmt_ctx->streams[ctx->audio_stream_idx_in]->codecpar);
            out_stream->codecpar->codec_tag = 0;
            out_stream->time_base = ctx->in_fmt_ctx->streams[ctx->audio_stream_idx_in]->time_base;
        } else {
            ctx->audio_dec_ctx = open_decoder(ctx->in_fmt_ctx, ctx->audio_stream_idx_in, ctx->opts.max_threads);
            if (!ctx->audio_dec_ctx) { set_error(ctx, "Falha ao abrir decoder de audio"); goto cleanup_output; }

            ctx->audio_enc_ctx = setup_audio_encoder(ctx, ctx->audio_dec_ctx, ctx->out_fmt_ctx);
            if (!ctx->audio_enc_ctx) goto cleanup_output;

            avcodec_parameters_from_context(out_stream->codecpar, ctx->audio_enc_ctx);
            out_stream->time_base = ctx->audio_enc_ctx->time_base;

            swr_alloc_set_opts2(&ctx->swr_ctx,
                &ctx->audio_enc_ctx->ch_layout, ctx->audio_enc_ctx->sample_fmt, ctx->audio_enc_ctx->sample_rate,
                &ctx->audio_dec_ctx->ch_layout, ctx->audio_dec_ctx->sample_fmt, ctx->audio_dec_ctx->sample_rate,
                0, NULL);
            swr_init(ctx->swr_ctx);

            ctx->audio_dec_frame  = av_frame_alloc();
            ctx->audio_frame_size = ctx->audio_enc_ctx->frame_size > 0 ? ctx->audio_enc_ctx->frame_size : 1024;

            /* Fifo para acumular amostras ate formar frames do tamanho
               exato exigido pelo encoder (essencial para nao dessincronizar) */
            ctx->audio_fifo = av_audio_fifo_alloc(
                ctx->audio_enc_ctx->sample_fmt,
                ctx->audio_enc_ctx->ch_layout.nb_channels, 1);
        }
    } else {
        ctx->audio_stream_idx_out = -1;
    }

    /* ── Abrir arquivo físico de saída e escrever header ── */
    if (!(ctx->out_fmt_ctx->oformat->flags & AVFMT_NOFILE)) {
        if (avio_open(&ctx->out_fmt_ctx->pb, out_path, AVIO_FLAG_WRITE) < 0) {
            set_error(ctx, "Nao foi possivel criar o arquivo de saida");
            goto cleanup_output;
        }
    }
    if (avformat_write_header(ctx->out_fmt_ctx, NULL) < 0) {
        set_error(ctx, "Falha ao escrever cabecalho do arquivo de saida");
        goto cleanup_output;
    }

    ctx->packet = av_packet_alloc();

cleanup_output:
cleanup_strings:
    (*env)->ReleaseStringUTFChars(env, jin, in_path);
    (*env)->ReleaseStringUTFChars(env, jout, out_path);
    (*env)->ReleaseStringUTFChars(env, jVideoCodec, video_codec);
    (*env)->ReleaseStringUTFChars(env, jAudioCodec, audio_codec);
    if (jPreset) (*env)->ReleaseStringUTFChars(env, jPreset, preset);

    return (jlong)(uintptr_t)ctx;
}

/* ------------------------------------------------------------------ */
/*  Codifica e escreve um frame de vídeo já escalado                   */
/* ------------------------------------------------------------------ */
static void encode_and_write_video(ConvertContext *ctx, AVFrame *frame) {
    avcodec_send_frame(ctx->video_enc_ctx, frame);
    AVPacket *out_pkt = av_packet_alloc();
    while (avcodec_receive_packet(ctx->video_enc_ctx, out_pkt) == 0) {
        out_pkt->stream_index = ctx->video_stream_idx_out;
        av_packet_rescale_ts(out_pkt, ctx->video_enc_ctx->time_base,
                              ctx->out_fmt_ctx->streams[ctx->video_stream_idx_out]->time_base);
        av_interleaved_write_frame(ctx->out_fmt_ctx, out_pkt);
        av_packet_unref(out_pkt);
    }
    av_packet_free(&out_pkt);
}

/* ------------------------------------------------------------------ */
/*  Codifica e escreve frames de áudio prontos (formato do encoder)    */
/* ------------------------------------------------------------------ */
static void encode_and_write_audio(ConvertContext *ctx, AVFrame *frame) {
    avcodec_send_frame(ctx->audio_enc_ctx, frame);
    AVPacket *out_pkt = av_packet_alloc();
    while (avcodec_receive_packet(ctx->audio_enc_ctx, out_pkt) == 0) {
        out_pkt->stream_index = ctx->audio_stream_idx_out;
        av_packet_rescale_ts(out_pkt, ctx->audio_enc_ctx->time_base,
                              ctx->out_fmt_ctx->streams[ctx->audio_stream_idx_out]->time_base);
        av_interleaved_write_frame(ctx->out_fmt_ctx, out_pkt);
        av_packet_unref(out_pkt);
    }
    av_packet_free(&out_pkt);
}

/* ------------------------------------------------------------------ */
/*  Drena o fifo de audio em frames de tamanho fixo (frame_size)       */
/*  flush_all=1: usado no EOF, envia tambem o ultimo bloco parcial     */
/* ------------------------------------------------------------------ */
static void encode_audio_from_fifo(ConvertContext *ctx, int flush_all) {
    int frame_size = ctx->audio_frame_size;

    while (av_audio_fifo_size(ctx->audio_fifo) >= frame_size ||
           (flush_all && av_audio_fifo_size(ctx->audio_fifo) > 0)) {

        int n = FFMIN(frame_size, av_audio_fifo_size(ctx->audio_fifo));
        if (n <= 0) break;

        AVFrame *frame = av_frame_alloc();
        frame->format      = ctx->audio_enc_ctx->sample_fmt;
        frame->sample_rate  = ctx->audio_enc_ctx->sample_rate;
        av_channel_layout_copy(&frame->ch_layout, &ctx->audio_enc_ctx->ch_layout);
        frame->nb_samples = n;
        av_frame_get_buffer(frame, 0);

        av_audio_fifo_read(ctx->audio_fifo, (void **)frame->data, n);

        /* pts continuo, contado em amostras de saida — bate exatamente
           com audio_enc_ctx->time_base = {1, sample_rate} */
        frame->pts = ctx->audio_pts_counter;
        ctx->audio_pts_counter += n;

        encode_and_write_audio(ctx, frame);
        av_frame_free(&frame);

        if (!flush_all && av_audio_fifo_size(ctx->audio_fifo) < frame_size) break;
    }
}



/* ------------------------------------------------------------------ */
/*  processNextPacket — processa UM pacote de entrada por chamada      */
/*  O Java chama isso em loop ate isFinished() retornar true.          */
/*  Retorna false apos EOF + flush completos.                          */
/* ------------------------------------------------------------------ */
JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_processNextPacket(
        JNIEnv *env, jobject obj, jlong ptr) {

    ConvertContext *ctx = get_cctx(ptr);
    if (!ctx || ctx->error || ctx->finished) return JNI_FALSE;

    int ret = av_read_frame(ctx->in_fmt_ctx, ctx->packet);
    if (ret < 0) {
        /* EOF: drena o fifo de audio pendente, depois faz flush dos encoders */
        if (ctx->audio_fifo) encode_audio_from_fifo(ctx, 1);
        if (ctx->video_enc_ctx) encode_and_write_video(ctx, NULL);
        if (ctx->audio_enc_ctx) encode_and_write_audio(ctx, NULL);
        av_write_trailer(ctx->out_fmt_ctx);
        ctx->finished = 1;
        return JNI_FALSE;
    }

    /* ── Atualiza o pts correto, separado por tipo de stream ──
       (nunca mistura timebase de video com a de audio) */
    if (ctx->packet->stream_index == ctx->video_stream_idx_in &&
        ctx->packet->pts != AV_NOPTS_VALUE) {
        ctx->last_video_pts = ctx->packet->pts;
    } else if (ctx->packet->stream_index == ctx->audio_stream_idx_in &&
               ctx->packet->pts != AV_NOPTS_VALUE) {
        ctx->last_audio_pts = ctx->packet->pts;
    }

    /* ── Processamento de pacote de vídeo ── */
    if (ctx->packet->stream_index == ctx->video_stream_idx_in) {

        if (strcmp(ctx->opts.video_codec, "copy") == 0) {
            AVStream *in_s  = ctx->in_fmt_ctx->streams[ctx->video_stream_idx_in];
            AVStream *out_s = ctx->out_fmt_ctx->streams[ctx->video_stream_idx_out];
            av_packet_rescale_ts(ctx->packet, in_s->time_base, out_s->time_base);
            ctx->packet->stream_index = ctx->video_stream_idx_out;
            ctx->packet->pos = -1;
            av_interleaved_write_frame(ctx->out_fmt_ctx, ctx->packet);

        } else if (avcodec_send_packet(ctx->video_dec_ctx, ctx->packet) >= 0) {
            while (avcodec_receive_frame(ctx->video_dec_ctx, ctx->dec_frame) == 0) {

                sws_scale(ctx->sws_ctx,
                          (const uint8_t * const *)ctx->dec_frame->data, ctx->dec_frame->linesize,
                          0, ctx->video_dec_ctx->height,
                          ctx->scaled_frame->data, ctx->scaled_frame->linesize);

                /* ── CORREÇÃO: reescala o pts da timebase de ENTRADA
                   para a timebase do ENCODER antes de codificar ── */
                if (ctx->dec_frame->pts != AV_NOPTS_VALUE) {
                    ctx->scaled_frame->pts = av_rescale_q(
                        ctx->dec_frame->pts,
                        ctx->in_fmt_ctx->streams[ctx->video_stream_idx_in]->time_base,
                        ctx->video_enc_ctx->time_base);
                } else {
                    ctx->scaled_frame->pts = AV_NOPTS_VALUE;
                }

                encode_and_write_video(ctx, ctx->scaled_frame);
                av_frame_unref(ctx->dec_frame);
            }
        }
    }
    /* ── Processamento de pacote de áudio ── */
    else if (ctx->packet->stream_index == ctx->audio_stream_idx_in) {

        if (strcmp(ctx->opts.audio_codec, "copy") == 0) {
            AVStream *in_s  = ctx->in_fmt_ctx->streams[ctx->audio_stream_idx_in];
            AVStream *out_s = ctx->out_fmt_ctx->streams[ctx->audio_stream_idx_out];
            av_packet_rescale_ts(ctx->packet, in_s->time_base, out_s->time_base);
            ctx->packet->stream_index = ctx->audio_stream_idx_out;
            ctx->packet->pos = -1;
            av_interleaved_write_frame(ctx->out_fmt_ctx, ctx->packet);

        } else if (ctx->swr_ctx && avcodec_send_packet(ctx->audio_dec_ctx, ctx->packet) >= 0) {
            while (avcodec_receive_frame(ctx->audio_dec_ctx, ctx->audio_dec_frame) == 0) {

                int out_samples = swr_get_out_samples(ctx->swr_ctx, ctx->audio_dec_frame->nb_samples);

                AVFrame *conv = av_frame_alloc();
                conv->format = ctx->audio_enc_ctx->sample_fmt;
                conv->sample_rate = ctx->audio_enc_ctx->sample_rate;
                av_channel_layout_copy(&conv->ch_layout, &ctx->audio_enc_ctx->ch_layout);
                conv->nb_samples = out_samples;
                av_frame_get_buffer(conv, 0);

                int converted = swr_convert(
                    ctx->swr_ctx, conv->data, out_samples,
                    (const uint8_t **)ctx->audio_dec_frame->data,
                    ctx->audio_dec_frame->nb_samples);

                if (converted > 0) {
                    /* ── CORREÇÃO: em vez de codificar direto com tamanho
                       variavel, empilha no fifo e so codifica em blocos
                       do tamanho exato exigido pelo encoder ── */
                  if (av_audio_fifo_realloc(ctx->audio_fifo,
                          av_audio_fifo_size(ctx->audio_fifo) + converted) < 0) {
                      set_error(ctx, "Falha ao realocar buffer de audio (memoria insuficiente)");
                      av_frame_free(&conv);
                      av_frame_unref(ctx->audio_dec_frame);
                      break; /* sai do while (avcodec_receive_frame...) */
                  }
                  av_audio_fifo_write(ctx->audio_fifo, (void **)conv->data, converted);

                    encode_audio_from_fifo(ctx, 0);
                }

                av_frame_free(&conv);
                av_frame_unref(ctx->audio_dec_frame);
            }
        }
    }

    av_packet_unref(ctx->packet);
    return JNI_TRUE;
}

/* ------------------------------------------------------------------ */
/*  Progresso (0.0 a 1.0) — usa SEMPRE o pts do stream primario         */
/*  (video, se existir; senao audio), nunca misturando timebases       */
/* ------------------------------------------------------------------ */
JNIEXPORT jdouble JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_getProgress(
        JNIEnv *env, jobject obj, jlong ptr) {
    ConvertContext *ctx = get_cctx(ptr);
    if (!ctx || ctx->duration_total_us <= 0) return 0.0;
    if (ctx->finished) return 1.0;

    int64_t pts;
    AVRational base;

    if (ctx->video_stream_idx_in >= 0) {
        pts  = ctx->last_video_pts;
        base = ctx->in_fmt_ctx->streams[ctx->video_stream_idx_in]->time_base;
    } else if (ctx->audio_stream_idx_in >= 0) {
        pts  = ctx->last_audio_pts;
        base = ctx->in_fmt_ctx->streams[ctx->audio_stream_idx_in]->time_base;
    } else {
        return 0.0;
    }

    double current_us = pts * av_q2d(base) * AV_TIME_BASE;
    double pct = current_us / (double)ctx->duration_total_us;

    if (pct < 0) pct = 0;
    if (pct > 1) pct = 1;
    return pct;
}

JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_isFinished(
        JNIEnv *env, jobject obj, jlong ptr) {
    ConvertContext *ctx = get_cctx(ptr);
    return (!ctx || ctx->finished || ctx->error) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_hasError(
        JNIEnv *env, jobject obj, jlong ptr) {
    ConvertContext *ctx = get_cctx(ptr);
    return (ctx && ctx->error) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_getErrorMessage(
        JNIEnv *env, jobject obj, jlong ptr) {
    ConvertContext *ctx = get_cctx(ptr);
    return (*env)->NewStringUTF(env, ctx ? ctx->error_msg : "Contexto invalido");
}

/* ------------------------------------------------------------------ */
/*  closeConverter — libera tudo                                        */
/* ------------------------------------------------------------------ */
JNIEXPORT void JNICALL
Java_com_esl_searchforfiles_Video_FFmpegConverter_closeConverter(
        JNIEnv *env, jobject obj, jlong ptr) {
    ConvertContext *ctx = get_cctx(ptr);
    if (!ctx) return;

    if (ctx->sws_ctx) sws_freeContext(ctx->sws_ctx);
    if (ctx->swr_ctx) swr_free(&ctx->swr_ctx);
    if (ctx->audio_fifo) av_audio_fifo_free(ctx->audio_fifo);

    if (ctx->video_dec_ctx) avcodec_free_context(&ctx->video_dec_ctx);
    if (ctx->video_enc_ctx) avcodec_free_context(&ctx->video_enc_ctx);
    if (ctx->audio_dec_ctx) avcodec_free_context(&ctx->audio_dec_ctx);
    if (ctx->audio_enc_ctx) avcodec_free_context(&ctx->audio_enc_ctx);

    if (ctx->dec_frame)       av_frame_free(&ctx->dec_frame);
    if (ctx->scaled_frame)    av_frame_free(&ctx->scaled_frame);
    if (ctx->audio_dec_frame) av_frame_free(&ctx->audio_dec_frame);
    if (ctx->packet)          av_packet_free(&ctx->packet);

    if (ctx->in_fmt_ctx) avformat_close_input(&ctx->in_fmt_ctx);
    if (ctx->out_fmt_ctx) {
        if (ctx->out_fmt_ctx->pb && !(ctx->out_fmt_ctx->oformat->flags & AVFMT_NOFILE))
            avio_closep(&ctx->out_fmt_ctx->pb);
        avformat_free_context(ctx->out_fmt_ctx);
    }

    free(ctx);
}
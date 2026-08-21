#include "video_thumbnail.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/mathematics.h>
#include <libavutil/time.h>
#include <libswscale/swscale.h>

#define MAX_PACKETS_PER_ATTEMPT 200

/* ------------------------------------------------------------------ */
/*  RNG thread-safe: xorshift32 com seed por ponteiro de stack         */
/* ------------------------------------------------------------------ */
static uint32_t xorshift32(uint32_t *state) {
    uint32_t x = *state;
    x ^= x << 13;
    x ^= x >> 17;
    x ^= x << 5;
    *state = x;
    return x;
}

static double random_ratio(double lo, double hi) {
    /* Seed única por chamada: combina tempo + endereço de variável local */
    uint32_t seed = (uint32_t)(uintptr_t)&seed
                  ^ (uint32_t)(av_gettime() & 0xFFFFFFFF);
    if (seed == 0) seed = 0xDEADBEEF;
    uint32_t r = xorshift32(&seed);
    return lo + ((double)r / (double)UINT32_MAX) * (hi - lo);
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                             */
/* ------------------------------------------------------------------ */
static void set_error(ThumbnailResult *r, const char *msg) {
    strncpy(r->error, msg, sizeof(r->error) - 1);
    r->error[sizeof(r->error) - 1] = '\0';
}

static int decode_one_frame(AVFormatContext *fmt,
                             AVCodecContext  *codec_ctx,
                             int              video_idx,
                             AVFrame         *frame,
                             AVPacket        *pkt) {
    int packets_read = 0;
    int ret;

    avcodec_flush_buffers(codec_ctx);

    while (packets_read < MAX_PACKETS_PER_ATTEMPT) {
        ret = av_read_frame(fmt, pkt);
        if (ret < 0) return ret;

        if (pkt->stream_index != video_idx) {
            av_packet_unref(pkt);
            continue;
        }

        packets_read++;

        if (avcodec_send_packet(codec_ctx, pkt) < 0) {
            av_packet_unref(pkt);
            continue;
        }
        av_packet_unref(pkt);

        ret = avcodec_receive_frame(codec_ctx, frame);
        if (ret == 0)               return 0;
        if (ret != AVERROR(EAGAIN)) return ret;
    }
    return AVERROR(EAGAIN);
}

/* ------------------------------------------------------------------ */
/*  thumbnail_default_options                                           */
/* ------------------------------------------------------------------ */
ThumbnailOptions thumbnail_default_options(void) {
    ThumbnailOptions o = {0};
    o.position_ratio = 0.0;   /* 0 = sortear em capture */
    o.out_width      = 0;
    o.out_height     = 0;
    o.max_attempts   = 5;
    o.scale_flags    = SWS_BILINEAR;
    return o;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_get_duration                                              */
/* ------------------------------------------------------------------ */
double thumbnail_get_duration(const char *filepath) {
    AVFormatContext *fmt = NULL;
    if (avformat_open_input(&fmt, filepath, NULL, NULL) < 0) return -1.0;
    if (avformat_find_stream_info(fmt, NULL) < 0) {
        avformat_close_input(&fmt);
        return -1.0;
    }
    double dur = (fmt->duration != AV_NOPTS_VALUE)
                 ? (double)fmt->duration / AV_TIME_BASE : -1.0;
    avformat_close_input(&fmt);
    return dur;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_capture                                                   */
/* ------------------------------------------------------------------ */
ThumbnailResult *thumbnail_capture(const char *filepath,
                                   const ThumbnailOptions *opts) {
    /* Alocar resultado — toda saída passa por aqui */
    ThumbnailResult *result = calloc(1, sizeof(ThumbnailResult));
    if (!result) return NULL;

    /* Opções efetivas — cópia local, sem estado global */
    ThumbnailOptions eff = opts ? *opts : thumbnail_default_options();

    if (eff.position_ratio <= 0.0)
        eff.position_ratio = random_ratio(0.33, 0.66); /* thread-safe */

    if (eff.position_ratio < 0.01) eff.position_ratio = 0.01;
    if (eff.position_ratio > 0.99) eff.position_ratio = 0.99;
    if (eff.max_attempts   < 1)    eff.max_attempts   = 1;

    /* ── Abrir container (contexto local, sem estado global) ── */
    AVFormatContext *fmt = NULL;
    if (avformat_open_input(&fmt, filepath, NULL, NULL) < 0) {
        set_error(result, "Não foi possível abrir o arquivo");
        return result;
    }
    if (avformat_find_stream_info(fmt, NULL) < 0) {
        set_error(result, "Não foi possível ler informações do stream");
        avformat_close_input(&fmt);
        return result;
    }

    /* ── Stream de vídeo ── */
    int video_idx = av_find_best_stream(
        fmt, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    if (video_idx < 0) {
        set_error(result, "Nenhum stream de vídeo encontrado");
        avformat_close_input(&fmt);
        return result;
    }

    AVCodecParameters *par   = fmt->streams[video_idx]->codecpar;
    const AVCodec     *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) {
        set_error(result, "Decoder não encontrado");
        avformat_close_input(&fmt);
        return result;
    }

    /* ── Contexto de codec — um por chamada, nunca compartilhado ── */
    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    if (!codec_ctx) {
        set_error(result, "Falha ao alocar AVCodecContext");
        avformat_close_input(&fmt);
        return result;
    }
    avcodec_parameters_to_context(codec_ctx, par);

    /* Limitar threads para não saturar o pool com muitas capturas */
    codec_ctx->thread_count = 2;
    codec_ctx->thread_type  = FF_THREAD_SLICE;

    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        set_error(result, "Falha ao abrir o decoder");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt);
        return result;
    }

    /* ── Duração e posição alvo ── */
    double duration_s = (fmt->duration != AV_NOPTS_VALUE)
                        ? (double)fmt->duration / AV_TIME_BASE : 0.0;

    /* ── Dimensões ── */
    int src_w = codec_ctx->width;
    int src_h = codec_ctx->height;
    int dst_w = (eff.out_width  > 0) ? eff.out_width  : src_w;
    int dst_h = (eff.out_height > 0) ? eff.out_height : src_h;

    if (eff.out_width > 0 && eff.out_height == 0 && src_h > 0)
        dst_h = (int)((double)src_h * dst_w / src_w);
    if (eff.out_height > 0 && eff.out_width == 0 && src_w > 0)
        dst_w = (int)((double)src_w * dst_h / src_h);

    /* Garantir dimensões pares (exigido por muitos codecs) */
    dst_w = (dst_w + 1) & ~1;
    dst_h = (dst_h + 1) & ~1;

    if (dst_w <= 0 || dst_h <= 0) {
        set_error(result, "Dimensões inválidas");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt);
        return result;
    }

    /* ── SwsContext — local por chamada ── */
    struct SwsContext *sws = sws_getContext(
        src_w, src_h, codec_ctx->pix_fmt,
        dst_w, dst_h, AV_PIX_FMT_RGB24,
        eff.scale_flags, NULL, NULL, NULL);

    if (!sws) {
        set_error(result, "Falha ao criar SwsContext");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt);
        return result;
    }

    /* ── Buffers locais ── */
    AVFrame  *src_frame = av_frame_alloc();
    AVFrame  *rgb_frame = av_frame_alloc();
    AVPacket *pkt       = av_packet_alloc();

    if (!src_frame || !rgb_frame || !pkt) {
        set_error(result, "Falha ao alocar frames/packet");
        goto cleanup;
    }

    /* Buffer RGB: alocado com malloc para ser liberado com free() */
    int      rgb_size = dst_w * dst_h * 3;
    uint8_t *rgb_buf  = malloc(rgb_size);  /* ← malloc, não av_malloc */
    if (!rgb_buf) {
        set_error(result, "Falha ao alocar buffer RGB");
        goto cleanup;
    }

    /* Preencher rgb_frame com o buffer acima */
    {
        uint8_t *planes[4]  = { rgb_buf, NULL, NULL, NULL };
        int      strides[4] = { dst_w * 3, 0, 0, 0 };
        rgb_frame->data[0]     = planes[0];
        rgb_frame->linesize[0] = strides[0];
    }

    /* ── Tentativas de captura ── */
    int success = 0;

    for (int attempt = 0; attempt < eff.max_attempts && !success; attempt++) {

        double offset = attempt * 0.05 * (attempt % 2 == 0 ? 1.0 : -1.0);
        double pos    = eff.position_ratio + offset;
        if (pos < 0.01) pos = 0.01;
        if (pos > 0.99) pos = 0.99;

        int64_t seek_ts = (int64_t)(duration_s * pos * AV_TIME_BASE);

        /* Seek: tentar no índice geral primeiro, depois no stream */
        if (av_seek_frame(fmt, -1, seek_ts, AVSEEK_FLAG_BACKWARD) < 0) {
            AVStream *vs = fmt->streams[video_idx];
            int64_t  ts  = av_rescale_q(seek_ts,
                               AV_TIME_BASE_Q, vs->time_base);
            av_seek_frame(fmt, video_idx, ts, AVSEEK_FLAG_BACKWARD);
        }

        if (decode_one_frame(fmt, codec_ctx, video_idx,
                             src_frame, pkt) == 0) {

            sws_scale(sws,
                      (const uint8_t * const *)src_frame->data,
                      src_frame->linesize, 0, src_h,
                      rgb_frame->data, rgb_frame->linesize);

            AVStream *vs   = fmt->streams[video_idx];
            double    pts  = (src_frame->pts != AV_NOPTS_VALUE)
                             ? src_frame->pts * av_q2d(vs->time_base)
                             : pos * duration_s;

            /* Preencher resultado — buffer RGB já é malloc, transfere
               a posse para o caller, que libera via thumbnail_free() */
            result->data        = rgb_buf;
            result->width       = dst_w;
            result->height      = dst_h;
            result->timestamp_s = pts;
            rgb_buf  = NULL; /* evitar double-free no cleanup */
            success  = 1;
        }

        av_frame_unref(src_frame);
    }

    if (!success) {
        set_error(result, "Não foi possível decodificar um frame válido");
        free(rgb_buf); /* liberar se não foi transferido */
    }

cleanup:
    if (src_frame) av_frame_free(&src_frame);
    if (rgb_frame) {
        rgb_frame->data[0] = NULL; /* não deixar o av_frame_free liberar
                                      um buffer que não é dele */
        av_frame_free(&rgb_frame);
    }
    if (pkt)       av_packet_free(&pkt);
    if (sws)       sws_freeContext(sws);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt);

    return result;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_save                                                      */
/* ------------------------------------------------------------------ */
int thumbnail_save(const ThumbnailResult *result, const char *out_path) {
    if (!result || !result->data || result->error[0] != '\0') return -1;

    const char *ext    = strrchr(out_path, '.');
    int         is_png = ext && (strcmp(ext, ".png") == 0
                              || strcmp(ext, ".PNG") == 0);
    int        is_jpeg = ext && (strcmp(ext, ".jpg")  == 0
                              || strcmp(ext, ".jpeg") == 0
                              || strcmp(ext, ".JPG")  == 0);

    if (!is_png && !is_jpeg) {
        fprintf(stderr, "[thumbnail] Formato não suportado: %s\n", ext);
        return -1;
    }

    AVFormatContext *out_fmt = NULL;
    avformat_alloc_output_context2(&out_fmt, NULL, "image2", out_path);
    if (!out_fmt) return -1;

    enum AVCodecID  codec_id = is_png ? AV_CODEC_ID_PNG : AV_CODEC_ID_MJPEG;
    const AVCodec  *enc      = avcodec_find_encoder(codec_id);
    AVStream       *stream   = avformat_new_stream(out_fmt, enc);
    AVCodecContext *enc_ctx  = avcodec_alloc_context3(enc);

    enc_ctx->width     = result->width;
    enc_ctx->height    = result->height;
    enc_ctx->pix_fmt   = is_png ? AV_PIX_FMT_RGB24 : AV_PIX_FMT_YUVJ420P;
    enc_ctx->time_base = (AVRational){1, 1};
    if (is_jpeg) enc_ctx->flags |= AV_CODEC_FLAG_QSCALE;

    stream->time_base = enc_ctx->time_base;
    avcodec_open2(enc_ctx, enc, NULL);
    avcodec_parameters_from_context(stream->codecpar, enc_ctx);

    AVFrame *frame = av_frame_alloc();
    frame->width   = result->width;
    frame->height  = result->height;
    frame->format  = enc_ctx->pix_fmt;
    av_frame_get_buffer(frame, 1);

    if (is_jpeg) {
        struct SwsContext *sws = sws_getContext(
            result->width, result->height, AV_PIX_FMT_RGB24,
            result->width, result->height, AV_PIX_FMT_YUVJ420P,
            SWS_BILINEAR, NULL, NULL, NULL);
        const uint8_t *src[1]    = { result->data };
        int            stride[1] = { result->width * 3 };
        sws_scale(sws, src, stride, 0, result->height,
                  frame->data, frame->linesize);
        sws_freeContext(sws);
    } else {
        for (int y = 0; y < result->height; y++) {
            memcpy(frame->data[0] + y * frame->linesize[0],
                   result->data   + y * result->width * 3,
                   result->width * 3);
        }
    }
    frame->pts = 0;

    if (avio_open(&out_fmt->pb, out_path, AVIO_FLAG_WRITE) < 0) {
        av_frame_free(&frame);
        avcodec_free_context(&enc_ctx);
        avformat_free_context(out_fmt);
        return -1;
    }
    if (avformat_write_header(out_fmt, NULL) < 0) {
        av_frame_free(&frame);
        avcodec_free_context(&enc_ctx);
        avio_closep(&out_fmt->pb);
        avformat_free_context(out_fmt);
        return -1;
    }

    AVPacket *pkt = av_packet_alloc();
    avcodec_send_frame(enc_ctx, frame);
    if (avcodec_receive_packet(enc_ctx, pkt) == 0)
        av_write_frame(out_fmt, pkt);
    av_write_trailer(out_fmt);

    av_packet_free(&pkt);
    av_frame_free(&frame);
    avcodec_free_context(&enc_ctx);
    avio_closep(&out_fmt->pb);
    avformat_free_context(out_fmt);

    return 0;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_free                                                      */
/* ------------------------------------------------------------------ */
void thumbnail_free(ThumbnailResult *result) {
    if (!result) return;
    free(result->data); /* malloc no capture, free aqui — consistente */
    result->data = NULL;
    free(result);
}
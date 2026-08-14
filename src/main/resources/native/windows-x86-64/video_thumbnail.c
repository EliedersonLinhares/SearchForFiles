
/* ================================================================== */
/*  video_thumbnail.c                                                   */
/* ================================================================== */
#include "video_thumbnail.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/mathematics.h>
#include <libswscale/swscale.h>

/* Número máximo de pacotes lidos ao tentar decodificar 1 frame */
#define MAX_PACKETS_PER_ATTEMPT 200

/* ------------------------------------------------------------------ */
/*  Helpers internos                                                    */
/* ------------------------------------------------------------------ */
static void set_error(ThumbnailResult *r, const char *msg) {
    strncpy(r->error, msg, sizeof(r->error) - 1);
    r->error[sizeof(r->error) - 1] = '\0';
}

/* Decode de um único frame a partir da posição atual do arquivo */
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
        if (ret < 0) return ret; /* EOF ou erro */

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
        if (ret == 0)  return 0;  /* frame decodificado com sucesso */
        if (ret != AVERROR(EAGAIN)) return ret;
    }
    return AVERROR(EAGAIN);
}

/* ------------------------------------------------------------------ */
/*  thumbnail_default_options                                           */
/* ------------------------------------------------------------------ */
ThumbnailOptions thumbnail_default_options(void) {
    ThumbnailOptions o = {0};
    o.position_ratio = 0.0;   /* será sorteado em thumbnail_capture */
    o.out_width      = 0;     /* manter resolução original           */
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
                 ? (double)fmt->duration / AV_TIME_BASE
                 : -1.0;
    avformat_close_input(&fmt);
    return dur;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_capture                                                   */
/* ------------------------------------------------------------------ */
ThumbnailResult *thumbnail_capture(const char *filepath,
                                   const ThumbnailOptions *opts) {
    ThumbnailResult *result = calloc(1, sizeof(ThumbnailResult));
    if (!result) return NULL;

    /* Opções efetivas */
    ThumbnailOptions eff = opts ? *opts : thumbnail_default_options();

    /* Se position_ratio == 0.0, sortear entre 0.33 e 0.66 */
    if (eff.position_ratio <= 0.0) {
        srand((unsigned)time(NULL));
        double lo = 0.33, hi = 0.66;
        eff.position_ratio = lo + ((double)rand() / RAND_MAX) * (hi - lo);
    }
    /* Garantir range válido */
    if (eff.position_ratio < 0.01) eff.position_ratio = 0.01;
    if (eff.position_ratio > 0.99) eff.position_ratio = 0.99;
    if (eff.max_attempts  < 1)     eff.max_attempts   = 1;

    /* ── Abrir container ── */
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

    AVCodecParameters *par  = fmt->streams[video_idx]->codecpar;
    const AVCodec     *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) {
        set_error(result, "Decoder não encontrado para o codec do vídeo");
        avformat_close_input(&fmt);
        return result;
    }

    AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(codec_ctx, par);
    codec_ctx->thread_count = 0; /* auto */
    if (avcodec_open2(codec_ctx, codec, NULL) < 0) {
        set_error(result, "Falha ao abrir o decoder");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt);
        return result;
    }

    /* ── Duração e posição alvo ── */
    double duration_s = (fmt->duration != AV_NOPTS_VALUE)
                        ? (double)fmt->duration / AV_TIME_BASE
                        : 0.0;

    double target_s = duration_s * eff.position_ratio;
    int64_t seek_ts = (int64_t)(target_s * AV_TIME_BASE);

    /* ── Dimensões de saída ── */
    int src_w = codec_ctx->width;
    int src_h = codec_ctx->height;
    int dst_w = (eff.out_width  > 0) ? eff.out_width  : src_w;
    int dst_h = (eff.out_height > 0) ? eff.out_height : src_h;

    /* Manter proporção se só um lado foi definido */
    if (eff.out_width > 0 && eff.out_height == 0)
        dst_h = (int)((double)src_h * dst_w / src_w);
    if (eff.out_height > 0 && eff.out_width == 0)
        dst_w = (int)((double)src_w * dst_h / src_h);

    /* ── Contexto de escala ── */
    struct SwsContext *sws = sws_getContext(
        src_w, src_h, codec_ctx->pix_fmt,
        dst_w, dst_h, AV_PIX_FMT_RGB24,
        eff.scale_flags, NULL, NULL, NULL);

    if (!sws) {
        set_error(result, "Falha ao criar contexto de escala (sws)");
        avcodec_free_context(&codec_ctx);
        avformat_close_input(&fmt);
        return result;
    }

    AVFrame  *frame   = av_frame_alloc();
    AVFrame  *rgb     = av_frame_alloc();
    AVPacket *pkt     = av_packet_alloc();

    int rgb_size = av_image_get_buffer_size(AV_PIX_FMT_RGB24, dst_w, dst_h, 1);
    uint8_t *rgb_buf = av_malloc(rgb_size);
    av_image_fill_arrays(rgb->data, rgb->linesize,
                         rgb_buf, AV_PIX_FMT_RGB24, dst_w, dst_h, 1);

    /* ── Tentativas de captura ── */
    int success = 0;
    for (int attempt = 0; attempt < eff.max_attempts && !success; attempt++) {

        /* A cada tentativa nova, variar levemente a posição */
        double adjusted = eff.position_ratio
                        + (attempt * 0.05 * (attempt % 2 == 0 ? 1 : -1));
        if (adjusted < 0.01) adjusted = 0.01;
        if (adjusted > 0.99) adjusted = 0.99;

        seek_ts = (int64_t)(duration_s * adjusted * AV_TIME_BASE);

        int sr = av_seek_frame(fmt, -1, seek_ts, AVSEEK_FLAG_BACKWARD);
        if (sr < 0 && attempt == 0) {
            /* Fallback: tentar seek no stream específico */
            AVStream *vs    = fmt->streams[video_idx];
            int64_t ts_stream = av_rescale_q(seek_ts,
                AV_TIME_BASE_Q, vs->time_base);
            av_seek_frame(fmt, video_idx, ts_stream, AVSEEK_FLAG_BACKWARD);
        }

        if (decode_one_frame(fmt, codec_ctx, video_idx, frame, pkt) == 0) {

            sws_scale(sws,
                      (const uint8_t * const *)frame->data, frame->linesize,
                      0, src_h,
                      rgb->data, rgb->linesize);

            /* Calcular timestamp real do frame */
            AVStream *vs = fmt->streams[video_idx];
            double pts_s = (frame->pts != AV_NOPTS_VALUE)
                           ? frame->pts * av_q2d(vs->time_base)
                           : adjusted * duration_s;

            /* Copiar para buffer de resultado */
            result->data        = malloc(rgb_size);
            result->width       = dst_w;
            result->height      = dst_h;
            result->timestamp_s = pts_s;
            memcpy(result->data, rgb_buf, rgb_size);
            success = 1;
        }

        av_frame_unref(frame);
    }

    if (!success)
        set_error(result, "Não foi possível decodificar um frame válido");

    /* ── Cleanup ── */
    av_free(rgb_buf);
    av_frame_free(&frame);
    av_frame_free(&rgb);
    av_packet_free(&pkt);
    sws_freeContext(sws);
    avcodec_free_context(&codec_ctx);
    avformat_close_input(&fmt);

    return result;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_save                                                      */
/* ------------------------------------------------------------------ */
int thumbnail_save(const ThumbnailResult *result, const char *out_path) {
    if (!result || !result->data || result->error[0] != '\0') return -1;

    /* Detectar formato pela extensão */
    const char *ext = strrchr(out_path, '.');
    int is_png  = ext && (strcmp(ext, ".png")  == 0
                       || strcmp(ext, ".PNG")  == 0);
    int is_jpeg = ext && (strcmp(ext, ".jpg")  == 0
                       || strcmp(ext, ".jpeg") == 0
                       || strcmp(ext, ".JPG")  == 0);

    if (!is_png && !is_jpeg) {
        fprintf(stderr, "[thumbnail] Formato não suportado: %s\n", ext);
        return -1;
    }

    /* Usar FFmpeg para salvar */
    AVFormatContext *out_fmt = NULL;
    avformat_alloc_output_context2(&out_fmt, NULL,
        is_png ? "image2" : "image2", out_path);
    if (!out_fmt) return -1;

    enum AVCodecID codec_id = is_png ? AV_CODEC_ID_PNG : AV_CODEC_ID_MJPEG;
    const AVCodec *enc      = avcodec_find_encoder(codec_id);
    AVStream      *stream   = avformat_new_stream(out_fmt, enc);
    AVCodecContext *enc_ctx = avcodec_alloc_context3(enc);

    enc_ctx->width     = result->width;
    enc_ctx->height    = result->height;
    enc_ctx->pix_fmt   = is_png ? AV_PIX_FMT_RGB24 : AV_PIX_FMT_YUVJ420P;
    enc_ctx->time_base = (AVRational){1, 1};

    if (is_jpeg) enc_ctx->flags |= AV_CODEC_FLAG_QSCALE;

    stream->time_base = enc_ctx->time_base;
    avcodec_open2(enc_ctx, enc, NULL);
    avcodec_parameters_from_context(stream->codecpar, enc_ctx);

    /* Converter RGB24 → formato do encoder se necessário */
    AVFrame *frame = av_frame_alloc();
    frame->width   = result->width;
    frame->height  = result->height;
    frame->format  = enc_ctx->pix_fmt;
    av_frame_get_buffer(frame, 1);

    if (is_jpeg) {
        /* RGB24 → YUVJ420P para JPEG */
        struct SwsContext *sws = sws_getContext(
            result->width, result->height, AV_PIX_FMT_RGB24,
            result->width, result->height, AV_PIX_FMT_YUVJ420P,
            SWS_BILINEAR, NULL, NULL, NULL);

        const uint8_t *src[1] = { result->data };
        int src_stride[1]     = { result->width * 3 };
        sws_scale(sws, src, src_stride, 0, result->height,
                  frame->data, frame->linesize);
        sws_freeContext(sws);
    } else {
        /* PNG: copiar RGB diretamente */
        for (int y = 0; y < result->height; y++) {
            memcpy(frame->data[0] + y * frame->linesize[0],
                   result->data  + y * result->width * 3,
                   result->width * 3);
        }
    }

    frame->pts = 0;

    avio_open(&out_fmt->pb, out_path, AVIO_FLAG_WRITE);
    avformat_write_header(out_fmt, NULL);

    AVPacket *pkt = av_packet_alloc();
    avcodec_send_frame(enc_ctx, frame);
    if (avcodec_receive_packet(enc_ctx, pkt) == 0) {
        av_write_frame(out_fmt, pkt);
    }
    av_write_trailer(out_fmt);

    /* Cleanup */
    av_packet_free(&pkt);
    av_frame_free(&frame);
    avcodec_free_context(&enc_ctx);
    avio_closep(&out_fmt->pb);
    avformat_free_context(out_fmt);

    printf("[thumbnail] Salvo: %s (%dx%d @ %.1fs)\n",
           out_path, result->width, result->height, result->timestamp_s);
    return 0;
}

/* ------------------------------------------------------------------ */
/*  thumbnail_free                                                      */
/* ------------------------------------------------------------------ */
void thumbnail_free(ThumbnailResult *result) {
    if (!result) return;
    if (result->data) { free(result->data); result->data = NULL; }
    free(result);
}
/* ================================================================== */
/*  image_codec.c  — corrigido para C puro e FFmpeg moderno            */
/* ================================================================== */
#include "image_codec.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/opt.h>
#include <libswscale/swscale.h>

/* ------------------------------------------------------------------ */
/*  Tabela de formatos                                                  */
/* ------------------------------------------------------------------ */
static const ICFormatInfo FORMAT_TABLE[] = {
 { ".png",   "image/png",               "Portable Network Graphics",      1, 1, 1, 0 },
 { ".jpg",   "image/jpeg",              "JPEG",                           0, 0, 0, 0 },
 { ".jpeg",  "image/jpeg",              "JPEG",                           0, 0, 0, 0 },
 { ".bmp",   "image/bmp",               "Bitmap",                         1, 0, 1, 0 },
 { ".tiff",  "image/tiff",              "TIFF",                           1, 1, 1, 0 },
 { ".tif",   "image/tiff",              "TIFF",                           1, 1, 1, 0 },
 { ".webp",  "image/webp",              "WebP",                           1, 0, 1, 1 },
 { ".gif",   "image/gif",               "GIF",                            1, 0, 1, 1 },
 { ".tga",   "image/x-targa",           "Truevision TGA",                 1, 0, 1, 0 },
 { ".ppm",   "image/x-portable-pixmap", "Portable Pixmap",                0, 0, 1, 0 },
 { ".pgm",   "image/x-portable-graymap","Portable Graymap",               0, 1, 1, 0 },
 { ".pbm",   "image/x-portable-bitmap", "Portable Bitmap",                0, 0, 1, 0 },
 { ".hdr",   "image/vnd.radiance",      "Radiance HDR",                   0, 1, 1, 0 },
 { ".exr",   "image/x-exr",             "OpenEXR",                        1, 1, 1, 0 },
 { ".heic",  "image/heic",              "HEIC",                           1, 0, 0, 0 },
 { ".heif",  "image/heif",              "HEIF",                           1, 0, 0, 0 },
 { ".avif",  "image/avif",              "AVIF",                           1, 1, 1, 0 },
 { ".jxl",   "image/jxl",              "JPEG XL",                         1, 1, 1, 0 },
 { ".dds",   "image/vnd.ms-dds",        "DirectDraw Surface",              1, 0, 1, 0 },
 { ".pcx",   "image/x-pcx",             "PCX",                             0, 0, 1, 0 },
 { ".sgi",   "image/sgi",               "SGI",                             1, 1, 1, 0 },
 { ".xwd",   "image/x-xwindowdump",     "X Window Dump",                   0, 0, 1, 0 },
 { ".jp2",   "image/jp2",               "JPEG 2000",                       1, 1, 1, 0 },
 { ".j2k",   "image/j2k",               "JPEG 2000 Codestream",            1, 1, 1, 0 },
 { ".ico",   "image/x-icon",            "Windows Icon",                    1, 0, 1, 0 },
};
#define FORMAT_COUNT ((int)(sizeof(FORMAT_TABLE)/sizeof(FORMAT_TABLE[0])))

/* ------------------------------------------------------------------ */
/*  Mapeamento extensão → AVCodecID                                     */
/*  Verificados contra FFmpeg 6.x / 7.x                                */
/* ------------------------------------------------------------------ */
typedef struct { const char *ext; enum AVCodecID id; } ExtCodec;

static const ExtCodec CODEC_MAP[] = {
    { ".png",  AV_CODEC_ID_PNG      },
    { ".jpg",  AV_CODEC_ID_MJPEG   },
    { ".jpeg", AV_CODEC_ID_MJPEG   },
    { ".bmp",  AV_CODEC_ID_BMP     },
    { ".tiff", AV_CODEC_ID_TIFF    },
    { ".tif",  AV_CODEC_ID_TIFF    },
    { ".webp", AV_CODEC_ID_WEBP    },
    { ".gif",  AV_CODEC_ID_GIF     },
    { ".tga",  AV_CODEC_ID_TARGA   },
    { ".ppm",  AV_CODEC_ID_PPM     },
    { ".pgm",  AV_CODEC_ID_PGM     },
    { ".pbm",  AV_CODEC_ID_PBM     },
    { ".hdr",  AV_CODEC_ID_RPZA    }, /* fallback — veja nota abaixo  */
    { ".exr",  AV_CODEC_ID_EXR     },
    { ".dds",  AV_CODEC_ID_DDS     },
    { ".pcx",  AV_CODEC_ID_PCX     },
    { ".sgi",  AV_CODEC_ID_SGI     },
    { ".xwd",  AV_CODEC_ID_XWD     },
    { ".jp2",  AV_CODEC_ID_JPEG2000},
    { ".j2k",  AV_CODEC_ID_JPEG2000},
    { ".ico",  AV_CODEC_ID_BMP     },
    /* AVIF e JXL: suporte depende do build do FFmpeg                  */
#ifdef AV_CODEC_ID_AV1
    { ".avif", AV_CODEC_ID_AV1     },
#endif
#ifdef AV_CODEC_ID_JPEGXL
    { ".jxl",  AV_CODEC_ID_JPEGXL },
#endif
};
#define CODEC_MAP_COUNT ((int)(sizeof(CODEC_MAP)/sizeof(CODEC_MAP[0])))

/* ------------------------------------------------------------------ */
/*  HDR: lido pelo demuxer "hdr" do FFmpeg, não por codec ID direto.   */
/*  Usamos avformat_open_input normalmente — o demuxer detecta sozinho. */
/* ------------------------------------------------------------------ */

/* ------------------------------------------------------------------ */
/*  Helpers                                                             */
/* ------------------------------------------------------------------ */
static void set_error(ICImage *img, const char *msg) {
    strncpy(img->error, msg, sizeof(img->error) - 1);
    img->error[sizeof(img->error) - 1] = '\0';
}

static void str_lower(char *dst, const char *src, int max) {
    int i = 0;
    while (src[i] && i < max - 1) {
        dst[i] = (char)tolower((unsigned char)src[i]);
        i++;
    }
    dst[i] = '\0';
}

static const char *get_extension(const char *path) {
    const char *dot = strrchr(path, '.');
    return dot ? dot : "";
}

static enum AVCodecID ext_to_codec_id(const char *ext) {
    char low[16];
    str_lower(low, ext, sizeof(low));
    for (int i = 0; i < CODEC_MAP_COUNT; i++)
        if (strcmp(CODEC_MAP[i].ext, low) == 0)
            return CODEC_MAP[i].id;
    return AV_CODEC_ID_NONE;
}

static enum AVPixelFormat ic_to_av(ICPixelFormat f) {
    switch (f) {
        case IC_FMT_RGB24:  return AV_PIX_FMT_RGB24;
        case IC_FMT_RGBA32: return AV_PIX_FMT_RGBA;
        case IC_FMT_GRAY8:  return AV_PIX_FMT_GRAY8;
        case IC_FMT_GRAY16: return AV_PIX_FMT_GRAY16BE;
        case IC_FMT_RGB48:  return AV_PIX_FMT_RGB48BE;
        case IC_FMT_RGBA64: return AV_PIX_FMT_RGBA64BE;
        default:            return AV_PIX_FMT_RGB24;
    }
}

static int ic_bpp(ICPixelFormat f) {
    switch (f) {
        case IC_FMT_RGB24:  return 3;
        case IC_FMT_RGBA32: return 4;
        case IC_FMT_GRAY8:  return 1;
        case IC_FMT_GRAY16: return 2;
        case IC_FMT_RGB48:  return 6;
        case IC_FMT_RGBA64: return 8;
        default:            return 3;
    }
}

static enum AVPixelFormat best_encode_fmt(enum AVCodecID id,
                                          ICPixelFormat src,
                                          int depth, int lossless) {
    int has_alpha = (src == IC_FMT_RGBA32 || src == IC_FMT_RGBA64);
    int is_16     = (src == IC_FMT_GRAY16 || src == IC_FMT_RGB48
                  || src == IC_FMT_RGBA64);
    switch (id) {
        case AV_CODEC_ID_PNG:
            if (has_alpha)            return AV_PIX_FMT_RGBA;
            if (depth == 16 || is_16) return AV_PIX_FMT_RGB48BE;
            return AV_PIX_FMT_RGB24;
        case AV_CODEC_ID_MJPEG:   return AV_PIX_FMT_YUVJ420P;
        case AV_CODEC_ID_WEBP:
            return lossless ? AV_PIX_FMT_BGR24 : AV_PIX_FMT_YUV420P;
        case AV_CODEC_ID_TIFF:
            if (has_alpha)            return AV_PIX_FMT_RGBA;
            if (depth == 16 || is_16) return AV_PIX_FMT_RGB48LE;
            return AV_PIX_FMT_RGB24;
        case AV_CODEC_ID_BMP:
            return has_alpha ? AV_PIX_FMT_BGRA : AV_PIX_FMT_BGR24;
        case AV_CODEC_ID_GIF:     return AV_PIX_FMT_PAL8;
        case AV_CODEC_ID_TARGA:   return AV_PIX_FMT_BGR24;
        case AV_CODEC_ID_PPM:     return AV_PIX_FMT_RGB24;
        case AV_CODEC_ID_PGM:     return AV_PIX_FMT_GRAY8;
        case AV_CODEC_ID_PBM:     return AV_PIX_FMT_MONOWHITE;
        case AV_CODEC_ID_EXR:     return AV_PIX_FMT_GBRPF32LE;
        case AV_CODEC_ID_SGI:     return AV_PIX_FMT_RGB24;
        case AV_CODEC_ID_PCX:     return AV_PIX_FMT_RGB24;
        case AV_CODEC_ID_XWD:     return AV_PIX_FMT_BGR24;
        case AV_CODEC_ID_JPEG2000:
            return has_alpha ? AV_PIX_FMT_RGBA : AV_PIX_FMT_RGB24;
        default:                  return AV_PIX_FMT_RGB24;
    }
}

/* ------------------------------------------------------------------ */
/*  Buffer em memória para ic_read_memory — funções estáticas (C puro) */
/* ------------------------------------------------------------------ */
typedef struct {
    const uint8_t *data;
    int            size;
    int            pos;
} MemBuf;

static int mem_read_packet(void *opaque, uint8_t *dst, int sz) {
    MemBuf *m     = (MemBuf *)opaque;
    int     avail = m->size - m->pos;
    if (avail <= 0) return AVERROR_EOF;
    int rd = avail < sz ? avail : sz;
    memcpy(dst, m->data + m->pos, rd);
    m->pos += rd;
    return rd;
}

static int64_t mem_seek(void *opaque, int64_t offset, int whence) {
    MemBuf *m = (MemBuf *)opaque;
    if (whence == AVSEEK_SIZE) return m->size;
    int64_t pos = (whence == SEEK_SET) ? offset
                : (whence == SEEK_CUR) ? m->pos + offset
                :                        m->size + offset;
    if (pos < 0 || pos > m->size) return -1;
    m->pos = (int)pos;
    return pos;
}

/* ------------------------------------------------------------------ */
/*  Sink de memória para escrita                                        */
/* ------------------------------------------------------------------ */
typedef struct {
    uint8_t *data;
    int      size;
    int      capacity;
} MemSink;

static int mem_write_packet(void *opaque, const uint8_t *buf, int sz) {
    MemSink *s = (MemSink *)opaque;
    if (s->size + sz > s->capacity) {
        s->capacity = (s->size + sz) * 2;
        uint8_t *tmp = realloc(s->data, s->capacity);
        if (!tmp) return AVERROR(ENOMEM);
        s->data = tmp;
    }
    memcpy(s->data + s->size, buf, sz);
    s->size += sz;
    return sz;
}

/* ------------------------------------------------------------------ */
/*  Núcleo de leitura                                                   */
/* ------------------------------------------------------------------ */
static ICImage *read_core(AVFormatContext *fmt, ICPixelFormat out_fmt) {
    ICImage *img = calloc(1, sizeof(ICImage));
    if (!img) return NULL;

    if (avformat_find_stream_info(fmt, NULL) < 0) {
        set_error(img, "Não foi possível ler informações do stream");
        avformat_close_input(&fmt);
        return img;
    }

    int video_idx = av_find_best_stream(
        fmt, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    if (video_idx < 0) {
        set_error(img, "Nenhum stream de imagem encontrado");
        avformat_close_input(&fmt);
        return img;
    }

    AVCodecParameters *par   = fmt->streams[video_idx]->codecpar;
    const AVCodec     *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) {
        set_error(img, "Decoder não encontrado para este formato");
        avformat_close_input(&fmt);
        return img;
    }

    snprintf(img->codec_name, sizeof(img->codec_name), "%s", codec->name);

    /* Buscar mime type */
    for (int i = 0; i < CODEC_MAP_COUNT; i++) {
        if (CODEC_MAP[i].id == par->codec_id) {
            for (int j = 0; j < FORMAT_COUNT; j++) {
                if (strcmp(FORMAT_TABLE[j].extension,
                           CODEC_MAP[i].ext) == 0) {
                    snprintf(img->mime_type, sizeof(img->mime_type),
                             "%s", FORMAT_TABLE[j].mime_type);
                    break;
                }
            }
            break;
        }
    }

    AVCodecContext *ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(ctx, par);
    ctx->thread_count = 2;
    if (avcodec_open2(ctx, codec, NULL) < 0) {
        set_error(img, "Falha ao abrir decoder");
        avcodec_free_context(&ctx);
        avformat_close_input(&fmt);
        return img;
    }

    AVPacket *pkt   = av_packet_alloc();
    AVFrame  *frame = av_frame_alloc();
    int       got   = 0;

    /* Leitura normal de pacotes */
    while (!got && av_read_frame(fmt, pkt) >= 0) {
        if (pkt->stream_index == video_idx) {
            if (avcodec_send_packet(ctx, pkt) >= 0) {
                int ret = avcodec_receive_frame(ctx, frame);
                if (ret == 0) {
                    got = 1;
                }
                /* EAGAIN = decoder precisa de mais dados ou de flush */
            }
        }
        av_packet_unref(pkt);
    }

    /* Flush: envia pacote nulo para forçar saída de frames pendentes */
    if (!got) {
        avcodec_send_packet(ctx, NULL);
        if (avcodec_receive_frame(ctx, frame) == 0)
            got = 1;
    }

    if (!got) {
        set_error(img, "Não foi possível decodificar a imagem");
        goto cleanup;
    }

    {
        enum AVPixelFormat dst_av = ic_to_av(out_fmt);
        int dst_w = frame->width, dst_h = frame->height;

        struct SwsContext *sws = sws_getContext(
            dst_w, dst_h, ctx->pix_fmt,
            dst_w, dst_h, dst_av,
            SWS_LANCZOS, NULL, NULL, NULL);
        if (!sws)
            sws = sws_getContext(dst_w, dst_h, ctx->pix_fmt,
                                 dst_w, dst_h, dst_av,
                                 SWS_BILINEAR, NULL, NULL, NULL);
        if (!sws) { set_error(img, "SwsContext falhou"); goto cleanup; }

        int      bpp    = ic_bpp(out_fmt);
        int      stride = dst_w * bpp;
        uint8_t *buf    = malloc(dst_h * stride);
        if (!buf) {
            sws_freeContext(sws);
            set_error(img, "Sem memória");
            goto cleanup;
        }

        uint8_t *dst_data[4]   = { buf, NULL, NULL, NULL };
        int      dst_stride[4] = { stride, 0, 0, 0 };
        sws_scale(sws,
                  (const uint8_t * const *)frame->data,
                  frame->linesize, 0, dst_h,
                  dst_data, dst_stride);
        sws_freeContext(sws);

        img->data            = buf;
        img->width           = dst_w;
        img->height          = dst_h;
        img->format          = out_fmt;
        img->bytes_per_pixel = bpp;
    }

cleanup:
    av_frame_free(&frame);
    av_packet_free(&pkt);
    avcodec_free_context(&ctx);
    avformat_close_input(&fmt);
    return img;
}

/* ------------------------------------------------------------------ */
/*  Detecta a extensão em minúsculas e retorna o demuxer forçado       */
/*  para formatos que o FFmpeg não identifica bem via probe automático  */
/* ------------------------------------------------------------------ */
static const AVInputFormat *force_demuxer(const char *filepath) {
    char ext[16] = {0};
    const char *dot = strrchr(filepath, '.');
    if (!dot) return NULL;
    str_lower(ext, dot, sizeof(ext));

    /* Formatos que precisam de demuxer explícito */
    const char *name = NULL;
    if      (strcmp(ext, ".png")  == 0) name = "png_pipe";
    else if (strcmp(ext, ".jpg")  == 0 ||
             strcmp(ext, ".jpeg") == 0) name = "jpeg_pipe";
    else if (strcmp(ext, ".bmp")  == 0) name = "bmp_pipe";
    else if (strcmp(ext, ".tga")  == 0) name = "tga_pipe";
    else if (strcmp(ext, ".webp") == 0) name = "webp_pipe";
    else if (strcmp(ext, ".hdr")  == 0) name = "hdr_pipe";
    else if (strcmp(ext, ".ppm")  == 0 ||
             strcmp(ext, ".pgm")  == 0 ||
             strcmp(ext, ".pbm")  == 0) name = "pnm_pipe";
    else if (strcmp(ext, ".tiff") == 0 ||
             strcmp(ext, ".tif")  == 0) name = "tiff_pipe";

    if (!name) return NULL;
    return av_find_input_format(name);
}

/* ------------------------------------------------------------------ */
/*  ic_read                                                             */
/* ------------------------------------------------------------------ */
ICImage *ic_read(const char *filepath, ICPixelFormat out_fmt) {
    AVFormatContext    *fmt  = NULL;
    const AVInputFormat *ifmt = force_demuxer(filepath);

    /* Opções: aumentar probe para formatos difíceis */
    AVDictionary *opts = NULL;
    av_dict_set(&opts, "probesize",       "5000000", 0);
    av_dict_set(&opts, "analyzeduration", "5000000", 0);

    if (avformat_open_input(&fmt, filepath, ifmt, &opts) < 0) {
        av_dict_free(&opts);
        /* Segunda tentativa sem forçar demuxer */
        if (ifmt && avformat_open_input(&fmt, filepath, NULL, NULL) < 0) {
            ICImage *err = calloc(1, sizeof(ICImage));
            snprintf(err->error, sizeof(err->error),
                     "Não foi possível abrir: %s", filepath);
            return err;
        }
    }
    av_dict_free(&opts);
    return read_core(fmt, out_fmt);
}

/* ------------------------------------------------------------------ */
/*  ic_read_memory  — sem lambdas C++, usando funções estáticas        */
/* ------------------------------------------------------------------ */
ICImage *ic_read_memory(const uint8_t *buf, int buf_size,
                        ICPixelFormat out_fmt) {
    MemBuf *mb = malloc(sizeof(MemBuf));
    if (!mb) {
        ICImage *err = calloc(1, sizeof(ICImage));
        snprintf(err->error, sizeof(err->error), "Sem memória");
        return err;
    }
    mb->data = buf;
    mb->size = buf_size;
    mb->pos  = 0;

    uint8_t         *iobuf = av_malloc(4096);
    AVIOContext     *avio  = avio_alloc_context(
        iobuf, 4096,
        0,           /* write_flag = 0 (leitura) */
        mb,          /* opaque                   */
        mem_read_packet,
        NULL,        /* write_packet             */
        mem_seek
    );

    AVFormatContext *fmt = avformat_alloc_context();
    fmt->pb = avio;

    if (avformat_open_input(&fmt, NULL, NULL, NULL) < 0) {
        ICImage *err = calloc(1, sizeof(ICImage));
        snprintf(err->error, sizeof(err->error),
                 "Falha ao abrir buffer de memória");
        av_freep(&iobuf);
        free(mb);
        return err;
    }

    /* mb é liberado quando avformat_close_input libera o avio context */
    return read_core(fmt, out_fmt);
}

/* ------------------------------------------------------------------ */
/*  Núcleo de escrita                                                   */
/* ------------------------------------------------------------------ */
static int write_core(const ICImage *img, const char *ext,
                      const ICWriteOptions *opts,
                      const char *filepath,
                      MemSink    *sink) {
    char low_ext[16];
    str_lower(low_ext, ext, sizeof(low_ext));

    enum AVCodecID codec_id = ext_to_codec_id(low_ext);
    if (codec_id == AV_CODEC_ID_NONE) {
        fprintf(stderr, "[ic] Formato não suportado: %s\n", ext);
        return -1;
    }

    const AVCodec    *enc     = avcodec_find_encoder(codec_id);
    if (!enc) {
        fprintf(stderr, "[ic] Encoder não encontrado para: %s\n", ext);
        return -1;
    }

    AVCodecContext   *enc_ctx = avcodec_alloc_context3(enc);
    enum AVPixelFormat dst_pix = best_encode_fmt(
        codec_id, img->format,
        opts ? opts->depth    : 8,
        opts ? opts->lossless : 0);

    enc_ctx->width     = img->width;
    enc_ctx->height    = img->height;
    enc_ctx->pix_fmt   = dst_pix;
    enc_ctx->time_base = (AVRational){1, 1};

    if (codec_id == AV_CODEC_ID_MJPEG) {
        int q = opts ? opts->quality : 90;
        enc_ctx->flags         |= AV_CODEC_FLAG_QSCALE;
        enc_ctx->global_quality = FF_QP2LAMBDA * (100 - q) / 10 + 1;
    }
    if (codec_id == AV_CODEC_ID_PNG) {
        int c = opts ? opts->compression : 6;
        av_opt_set_int(enc_ctx->priv_data, "compression_level", c, 0);
    }
    if (codec_id == AV_CODEC_ID_WEBP) {
        int lossless = opts ? opts->lossless : 0;
        int q        = opts ? opts->quality  : 90;
        av_opt_set_int(enc_ctx->priv_data, "lossless", lossless, 0);
        av_opt_set_int(enc_ctx->priv_data, "quality",  q,        0);
    }

    if (avcodec_open2(enc_ctx, enc, NULL) < 0) {
        fprintf(stderr, "[ic] Falha ao abrir encoder\n");
        avcodec_free_context(&enc_ctx);
        return -1;
    }

    /* Converter pixels para o formato do encoder */
    AVFrame *frame = av_frame_alloc();
    frame->width   = img->width;
    frame->height  = img->height;
    frame->format  = dst_pix;
    av_frame_get_buffer(frame, 1);

    enum AVPixelFormat src_av = ic_to_av(img->format);
    struct SwsContext *sws = sws_getContext(
        img->width, img->height, src_av,
        img->width, img->height, dst_pix,
        SWS_LANCZOS, NULL, NULL, NULL);
    if (!sws)
        sws = sws_getContext(img->width, img->height, src_av,
                             img->width, img->height, dst_pix,
                             SWS_BILINEAR, NULL, NULL, NULL);

    if (sws) {
        int            src_stride   = img->width * img->bytes_per_pixel;
        const uint8_t *src_data[4]  = { img->data, NULL, NULL, NULL };
        int            strides[4]   = { src_stride, 0, 0, 0 };
        sws_scale(sws, src_data, strides, 0, img->height,
                  frame->data, frame->linesize);
        sws_freeContext(sws);
    }
    frame->pts = 0;

    /* Container de saída */
    const char *fmt_name = (codec_id == AV_CODEC_ID_GIF) ? "gif" : "image2";
    AVFormatContext *out_fmt = NULL;
    avformat_alloc_output_context2(&out_fmt, NULL, fmt_name,
                                   filepath ? filepath : "out");
    if (!out_fmt) {
        av_frame_free(&frame);
        avcodec_free_context(&enc_ctx);
        return -1;
    }

    AVStream *stream = avformat_new_stream(out_fmt, enc);
    avcodec_parameters_from_context(stream->codecpar, enc_ctx);
    stream->time_base = enc_ctx->time_base;

    int ret = 0;

    if (filepath) {
        if (avio_open(&out_fmt->pb, filepath, AVIO_FLAG_WRITE) < 0) {
            ret = -1; goto wcleanup;
        }
    } else {
        /* Escrita em MemSink */
        uint8_t *iobuf   = av_malloc(65536);
        AVIOContext *avio = avio_alloc_context(
            iobuf, 65536,
            1,       /* write_flag = 1 */
            sink,
            NULL,    /* read_packet  */
            mem_write_packet,
            NULL     /* seek         */
        );
        avio->seekable   = 0;
        out_fmt->pb      = avio;
    }

    if (avformat_write_header(out_fmt, NULL) < 0) {
        ret = -1; goto wcleanup;
    }

    {
        AVPacket *pkt = av_packet_alloc();
        avcodec_send_frame(enc_ctx, frame);
        while (avcodec_receive_packet(enc_ctx, pkt) == 0) {
            av_write_frame(out_fmt, pkt);
            av_packet_unref(pkt);
        }
        av_write_trailer(out_fmt);
        av_packet_free(&pkt);
    }

wcleanup:
    if (filepath && out_fmt->pb) avio_closep(&out_fmt->pb);
    avformat_free_context(out_fmt);
    av_frame_free(&frame);
    avcodec_free_context(&enc_ctx);
    return ret;
}

/* ------------------------------------------------------------------ */
/*  API pública                                                         */
/* ------------------------------------------------------------------ */
ICWriteOptions ic_default_write_options(void) {
    ICWriteOptions o = {0};
    o.quality        = 90;
    o.compression    = 6;
    o.lossless       = 0;
    o.depth          = 8;
    o.preserve_alpha = 1;
    return o;
}

int ic_write(const ICImage *img, const char *filepath,
             const ICWriteOptions *opts) {
    if (!img || !img->data || img->error[0] != '\0') return -1;
    return write_core(img, get_extension(filepath), opts, filepath, NULL);
}

uint8_t *ic_write_memory(const ICImage *img, const char *ext,
                         const ICWriteOptions *opts, int *out_size) {
    if (!img || !img->data || img->error[0] != '\0') return NULL;
    MemSink sink = { malloc(65536), 0, 65536 };
    if (!sink.data) return NULL;
    if (write_core(img, ext, opts, NULL, &sink) < 0) {
        free(sink.data);
        return NULL;
    }
    *out_size = sink.size;
    return sink.data;
}

ICImage *ic_convert(const ICImage *src, ICPixelFormat dst_fmt) {
    if (!src || !src->data) return NULL;
    ICImage *dst = calloc(1, sizeof(ICImage));
    int bpp = ic_bpp(dst_fmt), stride = src->width * bpp;
    uint8_t *buf = malloc(src->height * stride);
    if (!buf) { free(dst); return NULL; }

    struct SwsContext *sws = sws_getContext(
        src->width, src->height, ic_to_av(src->format),
        src->width, src->height, ic_to_av(dst_fmt),
        SWS_LANCZOS, NULL, NULL, NULL);
    if (sws) {
        int            s     = src->width * src->bytes_per_pixel;
        const uint8_t *sd[4] = { src->data, NULL, NULL, NULL };
        int            ss[4] = { s, 0, 0, 0 };
        uint8_t       *dd[4] = { buf, NULL, NULL, NULL };
        int            ds[4] = { stride, 0, 0, 0 };
        sws_scale(sws, sd, ss, 0, src->height, dd, ds);
        sws_freeContext(sws);
    }
    dst->data = buf; dst->width = src->width; dst->height = src->height;
    dst->format = dst_fmt; dst->bytes_per_pixel = bpp;
    return dst;
}

ICImage *ic_resize(const ICImage *src, int new_w, int new_h, int filter) {
    if (!src || !src->data || new_w <= 0 || new_h <= 0) return NULL;
    int sws_flag = (filter == 2) ? SWS_LANCZOS :
                   (filter == 1) ? SWS_BICUBIC  : SWS_BILINEAR;
    ICImage *dst = calloc(1, sizeof(ICImage));
    int bpp = src->bytes_per_pixel, stride = new_w * bpp;
    uint8_t *buf = malloc(new_h * stride);
    if (!buf) { free(dst); return NULL; }

    enum AVPixelFormat pix = ic_to_av(src->format);
    struct SwsContext *sws = sws_getContext(
        src->width, src->height, pix,
        new_w,      new_h,       pix,
        sws_flag, NULL, NULL, NULL);
    if (sws) {
        int            s     = src->width * bpp;
        const uint8_t *sd[4] = { src->data, NULL, NULL, NULL };
        int            ss[4] = { s, 0, 0, 0 };
        uint8_t       *dd[4] = { buf, NULL, NULL, NULL };
        int            ds[4] = { stride, 0, 0, 0 };
        sws_scale(sws, sd, ss, 0, src->height, dd, ds);
        sws_freeContext(sws);
    }
    dst->data = buf; dst->width = new_w; dst->height = new_h;
    dst->format = src->format; dst->bytes_per_pixel = bpp;
    snprintf(dst->codec_name, sizeof(dst->codec_name), "%s", src->codec_name);
    snprintf(dst->mime_type,  sizeof(dst->mime_type),  "%s", src->mime_type);
    return dst;
}

const ICFormatInfo *ic_format_info(const char *extension) {
    char low[16]; str_lower(low, extension, sizeof(low));
    for (int i = 0; i < FORMAT_COUNT; i++)
        if (strcmp(FORMAT_TABLE[i].extension, low) == 0)
            return &FORMAT_TABLE[i];
    return NULL;
}

const ICFormatInfo **ic_supported_formats(int *count) {
    static const ICFormatInfo *ptrs[FORMAT_COUNT];
    for (int i = 0; i < FORMAT_COUNT; i++) ptrs[i] = &FORMAT_TABLE[i];
    *count = FORMAT_COUNT;
    return ptrs;
}

const char *ic_detect_format(const char *filepath) {
    AVFormatContext *fmt = NULL;
    if (avformat_open_input(&fmt, filepath, NULL, NULL) < 0) return NULL;
    const char *name = fmt->iformat ? fmt->iformat->name : NULL;
    const char *result = NULL;
    if (name) {
        if (strstr(name,"png"))              result = ".png";
        else if (strstr(name,"jpeg") ||
                 strstr(name,"mjpeg"))       result = ".jpg";
        else if (strstr(name,"bmp"))         result = ".bmp";
        else if (strstr(name,"tiff"))        result = ".tiff";
        else if (strstr(name,"webp"))        result = ".webp";
        else if (strstr(name,"gif"))         result = ".gif";
        else if (strstr(name,"targa"))       result = ".tga";
        else if (strstr(name,"hdr") ||
                 strstr(name,"rgbe"))        result = ".hdr";
        else if (strstr(name,"exr"))         result = ".exr";
        else if (strstr(name,"ppm"))         result = ".ppm";
        else if (strstr(name,"pgm"))         result = ".pgm";
        else if (strstr(name,"sgi"))         result = ".sgi";
        else if (strstr(name,"jpeg2000") ||
                 strstr(name,"j2k"))         result = ".jp2";
        else if (strstr(name,"dds"))         result = ".dds";
    }
    avformat_close_input(&fmt);
    return result;
}

int ic_can_read(const char *extension) {
    char low[16]; str_lower(low, extension, sizeof(low));
    /* PSD é tratado pelo psd_reader, não pelo FFmpeg */
    if (strcmp(low, ".psd") == 0 || strcmp(low, ".psb") == 0) return 1;
    enum AVCodecID id = ext_to_codec_id(low);
    if (id == AV_CODEC_ID_NONE) return 0;
    return avcodec_find_decoder(id) != NULL ? 1 : 0;
}

int ic_can_write(const char *extension) {
    char low[16]; str_lower(low, extension, sizeof(low));
    if (strcmp(low, ".psd") == 0 || strcmp(low, ".psb") == 0) return 0;
    enum AVCodecID id = ext_to_codec_id(low);
    if (id == AV_CODEC_ID_NONE) return 0;
    return avcodec_find_encoder(id) != NULL ? 1 : 0;
}

void ic_free(ICImage *img) {
    if (!img) return;
    free(img->data);
    img->data = NULL;
    free(img);
}

void ic_free_buffer(uint8_t *buf) { free(buf); }
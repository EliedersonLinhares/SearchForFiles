
/* ================================================================== */
/*  psd_reader.c                                                        */
/* ================================================================== */
#include "psd_reader.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

/* ------------------------------------------------------------------ */
/*  Leitor de bytes big-endian com cursor                               */
/* ------------------------------------------------------------------ */
typedef struct {
    const uint8_t *data;
    size_t         size;
    size_t         pos;
    int            error;
} PSDStream;

static void stream_init(PSDStream *s, const uint8_t *d, size_t sz) {
    s->data = d; s->size = sz; s->pos = 0; s->error = 0;
}

static uint8_t read_u8(PSDStream *s) {
    if (s->pos >= s->size) { s->error = 1; return 0; }
    return s->data[s->pos++];
}

static uint16_t read_u16(PSDStream *s) {
    uint16_t v = (uint16_t)read_u8(s) << 8;
    return v | read_u8(s);
}

static uint32_t read_u32(PSDStream *s) {
    uint32_t v = (uint32_t)read_u16(s) << 16;
    return v | read_u16(s);
}

static uint64_t read_u64(PSDStream *s) {
    uint64_t v = (uint64_t)read_u32(s) << 32;
    return v | read_u32(s);
}

static void skip(PSDStream *s, size_t n) {
    if (s->pos + n > s->size) { s->error = 1; s->pos = s->size; return; }
    s->pos += n;
}

static void read_bytes(PSDStream *s, uint8_t *dst, size_t n) {
    if (s->pos + n > s->size) { s->error = 1; return; }
    memcpy(dst, s->data + s->pos, n);
    s->pos += n;
}

/* ------------------------------------------------------------------ */
/*  Descompressão PackBits (RLE usado pelo PSD)                         */
/* ------------------------------------------------------------------ */
static int packbits_decode(const uint8_t *src, int src_len,
                           uint8_t *dst, int dst_len) {
    int si = 0, di = 0;
    while (si < src_len && di < dst_len) {
        int8_t header = (int8_t)src[si++];
        if (header >= 0) {
            /* header+1 bytes literais */
            int count = header + 1;
            if (si + count > src_len) return -1;
            while (count-- > 0 && di < dst_len)
                dst[di++] = src[si++];
        } else if (header != -128) {
            /* repetir o próximo byte (-header+1) vezes */
            int count = -header + 1;
            if (si >= src_len) return -1;
            uint8_t val = src[si++];
            while (count-- > 0 && di < dst_len)
                dst[di++] = val;
        }
        /* header == -128: nop */
    }
    return di;
}

/* ------------------------------------------------------------------ */
/*  Conversão CMYK → RGB (simplificada, sem perfil ICC)                 */
/* ------------------------------------------------------------------ */
static void cmyk_to_rgb(uint8_t c, uint8_t m, uint8_t y, uint8_t k,
                        uint8_t *r, uint8_t *g, uint8_t *b) {
    /* PSD armazena CMYK invertido (255 = 0% de tinta) */
    float C = 1.0f - c / 255.0f;
    float M = 1.0f - m / 255.0f;
    float Y = 1.0f - y / 255.0f;
    float K = 1.0f - k / 255.0f;
    *r = (uint8_t)((1.0f - (C * (1.0f - K) + K)) * 255.0f);
    *g = (uint8_t)((1.0f - (M * (1.0f - K) + K)) * 255.0f);
    *b = (uint8_t)((1.0f - (Y * (1.0f - K) + K)) * 255.0f);
}

/* Converter Lab D50 → XYZ → sRGB (para modo Lab) */
static float lab_f_inv(float t) {
    return t > 0.206897f ? t*t*t : (t - 16.0f/116.0f) / 7.787f;
}
static void lab_to_rgb(uint8_t L8, uint8_t a8, uint8_t b8,
                       uint8_t *r, uint8_t *g, uint8_t *b) {
    float L = L8 * 100.0f / 255.0f;
    float a = a8 - 128.0f;
    float bv = b8 - 128.0f;

    float fy = (L + 16.0f) / 116.0f;
    float fx = a / 500.0f + fy;
    float fz = fy - bv / 200.0f;

    float X = 0.95047f * lab_f_inv(fx);
    float Y = 1.00000f * lab_f_inv(fy);
    float Z = 1.08883f * lab_f_inv(fz);

    float rf =  3.2406f*X - 1.5372f*Y - 0.4986f*Z;
    float gf = -0.9689f*X + 1.8758f*Y + 0.0415f*Z;
    float bf =  0.0557f*X - 0.2040f*Y + 1.0570f*Z;

    /* Gamma sRGB */
    #define SRGB(v) ((v)<=0.0031308f ? 12.92f*(v) : 1.055f*powf((v),1/2.4f)-0.055f)
    *r = (uint8_t)(fmaxf(0,fminf(1, SRGB(rf))) * 255.0f);
    *g = (uint8_t)(fmaxf(0,fminf(1, SRGB(gf))) * 255.0f);
    *b = (uint8_t)(fmaxf(0,fminf(1, SRGB(bf))) * 255.0f);
    #undef SRGB
}

/* ------------------------------------------------------------------ */
/*  Núcleo de leitura                                                   */
/* ------------------------------------------------------------------ */
static PSDImage *psd_read_stream(PSDStream *s) {
    PSDImage *img = calloc(1, sizeof(PSDImage));
    if (!img) return NULL;

    /* ── Header ── */
    uint8_t sig[4];
    read_bytes(s, sig, 4);
    if (s->error || memcmp(sig, "8BPS", 4) != 0) {
        snprintf(img->error, sizeof(img->error),
                 "Assinatura inválida (não é PSD/PSB)");
        return img;
    }

    uint16_t version = read_u16(s);
    if (version != 1 && version != 2) {
        snprintf(img->error, sizeof(img->error),
                 "Versão PSD não suportada: %d", version);
        return img;
    }
    int is_psb = (version == 2); /* PSB = Large Document */

    skip(s, 6); /* Reserved */

    int channels   = read_u16(s);  /* 1-56 */
    int height     = (int)read_u32(s);
    int width      = (int)read_u32(s);
    int depth      = read_u16(s);  /* bits por canal */
    int color_mode = read_u16(s);

    img->width      = width;
    img->height     = height;
    img->channels   = channels;
    img->depth      = depth;
    img->color_mode = (PSDColorMode)color_mode;

    /* Validações básicas */
    if (width <= 0 || height <= 0 || width > 300000 || height > 300000) {
        snprintf(img->error, sizeof(img->error),
                 "Dimensões inválidas: %dx%d", width, height);
        return img;
    }
    if (depth != 8 && depth != 16 && depth != 32) {
        snprintf(img->error, sizeof(img->error),
                 "Profundidade de bits não suportada: %d", depth);
        return img;
    }

    /* Modos suportados */
    if (color_mode != PSD_COLOR_RGB    &&
        color_mode != PSD_COLOR_CMYK   &&
        color_mode != PSD_COLOR_GRAYSCALE &&
        color_mode != PSD_COLOR_LAB) {
        snprintf(img->error, sizeof(img->error),
                 "Modo de cor não suportado: %d", color_mode);
        return img;
    }

    /* Quantos canais de cor esperamos */
    int color_channels = (color_mode == PSD_COLOR_CMYK) ? 4 :
                         (color_mode == PSD_COLOR_RGB)  ? 3 : 1;
    img->has_alpha = (channels > color_channels) ? 1 : 0;

    /* ── Color Mode Data ── */
    uint32_t cmd_len = read_u32(s);
    skip(s, cmd_len);

    /* ── Image Resources ── */
    uint32_t ird_len = read_u32(s);
    skip(s, ird_len);

    /* ── Layer and Mask Info ── */
    uint64_t lmi_len = is_psb ? read_u64(s) : read_u32(s);
    skip(s, (size_t)lmi_len);

    if (s->error) {
        snprintf(img->error, sizeof(img->error),
                 "Erro ao ler blocos de metadados do PSD");
        return img;
    }

    /* ── Image Data ── */
    uint16_t compression = read_u16(s);
    /*
     * compression:
     *   0 = Raw (sem compressão)
     *   1 = PackBits RLE
     *   2 = ZIP sem predição
     *   3 = ZIP com predição
     */
    if (compression > 1) {
        snprintf(img->error, sizeof(img->error),
                 "Compressão ZIP (%d) não suportada — "
                 "salve o PSD com compressão RLE ou sem compressão",
                 compression);
        return img;
    }

    int bytes_per_sample = depth / 8;
    int scan_size        = width * bytes_per_sample; /* bytes por linha por canal */
    int total_pixels     = width * height;

    /* Alocar planos: um por canal */
    uint8_t **planes = calloc(channels, sizeof(uint8_t *));
    if (!planes) {
        snprintf(img->error, sizeof(img->error), "Sem memória");
        return img;
    }
    for (int c = 0; c < channels; c++) {
        planes[c] = malloc(total_pixels * bytes_per_sample);
        if (!planes[c]) {
            for (int j = 0; j < c; j++) free(planes[j]);
            free(planes);
            snprintf(img->error, sizeof(img->error), "Sem memória para plano %d", c);
            return img;
        }
    }

    if (compression == 1) {
        /* PackBits: tabela de tamanhos de linhas primeiro */
        int n_counts = channels * height;
        uint16_t *row_sizes = malloc(n_counts * sizeof(uint16_t));
        if (!row_sizes) {
            snprintf(img->error, sizeof(img->error), "Sem memória (RLE)");
            goto cleanup;
        }
        for (int i = 0; i < n_counts; i++)
            row_sizes[i] = read_u16(s);

        /* Decodificar plano a plano, linha a linha */
        int ri = 0;
        for (int c = 0; c < channels && !s->error; c++) {
            uint8_t *plane = planes[c];
            for (int row = 0; row < height && !s->error; row++) {
                uint16_t rle_size = row_sizes[ri++];
                if (s->pos + rle_size > s->size) { s->error = 1; break; }

                int decoded = packbits_decode(
                    s->data + s->pos, rle_size,
                    plane + row * scan_size, scan_size);
                s->pos += rle_size;

                if (decoded < 0) {
                    s->error = 1;
                    snprintf(img->error, sizeof(img->error),
                             "Erro PackBits na linha %d canal %d", row, c);
                }
            }
        }
        free(row_sizes);

    } else {
        /* Raw: canais intercalados em planos separados */
        for (int c = 0; c < channels && !s->error; c++) {
            read_bytes(s, planes[c],
                       (size_t)total_pixels * bytes_per_sample);
        }
    }

    if (s->error && img->error[0] == '\0') {
        snprintf(img->error, sizeof(img->error),
                 "Erro ao ler dados de imagem");
        goto cleanup;
    }

    /* ── Montar buffer RGB24 / RGBA32 de saída ── */
    int out_channels = img->has_alpha ? 4 : 3;
    uint8_t *out = malloc(total_pixels * out_channels);
    if (!out) {
        snprintf(img->error, sizeof(img->error), "Sem memória (output)");
        goto cleanup;
    }

    for (int i = 0; i < total_pixels; i++) {

        /* Extrair sample de 8 bits (normalizar 16/32 → 8) */
        #define SAMPLE(plane, idx) (                                    \
            bytes_per_sample == 1 ? (plane)[idx] :                     \
            bytes_per_sample == 2 ?                                     \
                (uint8_t)(((uint16_t)(plane)[(idx)*2]<<8               \
                         |(plane)[(idx)*2+1]) >> 8) :                  \
                /* 32-bit float */                                       \
                (uint8_t)(fminf(1.0f, fmaxf(0.0f,                      \
                    *(float*)((plane)+(idx)*4))) * 255.0f)              \
        )

        uint8_t r, g, b, a = 255;

        switch (color_mode) {
            case PSD_COLOR_RGB:
                r = SAMPLE(planes[0], i);
                g = SAMPLE(planes[1], i);
                b = SAMPLE(planes[2], i);
                if (img->has_alpha && channels > 3)
                    a = SAMPLE(planes[3], i);
                break;

            case PSD_COLOR_CMYK: {
                uint8_t c = SAMPLE(planes[0], i);
                uint8_t m = SAMPLE(planes[1], i);
                uint8_t y = SAMPLE(planes[2], i);
                uint8_t k = SAMPLE(planes[3], i);
                cmyk_to_rgb(c, m, y, k, &r, &g, &b);
                if (img->has_alpha && channels > 4)
                    a = SAMPLE(planes[4], i);
                break;
            }

            case PSD_COLOR_GRAYSCALE: {
                uint8_t gray = SAMPLE(planes[0], i);
                r = g = b = gray;
                if (img->has_alpha && channels > 1)
                    a = SAMPLE(planes[1], i);
                break;
            }

            case PSD_COLOR_LAB: {
                uint8_t L = SAMPLE(planes[0], i);
                uint8_t av = SAMPLE(planes[1], i);
                uint8_t bv = SAMPLE(planes[2], i);
                lab_to_rgb(L, av, bv, &r, &g, &b);
                if (img->has_alpha && channels > 3)
                    a = SAMPLE(planes[3], i);
                break;
            }

            default: r = g = b = 0; break;
        }
        #undef SAMPLE

        if (out_channels == 4) {
            out[i*4]   = r;
            out[i*4+1] = g;
            out[i*4+2] = b;
            out[i*4+3] = a;
        } else {
            out[i*3]   = r;
            out[i*3+1] = g;
            out[i*3+2] = b;
        }
    }

    img->data = out;

cleanup:
    for (int c = 0; c < channels; c++) free(planes[c]);
    free(planes);
    return img;
}

/* ------------------------------------------------------------------ */
/*  API pública                                                         */
/* ------------------------------------------------------------------ */
PSDImage *psd_read(const char *filepath) {
    FILE *f = fopen(filepath, "rb");
    if (!f) {
        PSDImage *err = calloc(1, sizeof(PSDImage));
        snprintf(err->error, sizeof(err->error),
                 "Não foi possível abrir: %s", filepath);
        return err;
    }

    fseek(f, 0, SEEK_END);
    long fsize = ftell(f);
    fseek(f, 0, SEEK_SET);

    uint8_t *buf = malloc(fsize);
    if (!buf) {
        fclose(f);
        PSDImage *err = calloc(1, sizeof(PSDImage));
        snprintf(err->error, sizeof(err->error), "Sem memória (%ld bytes)", fsize);
        return err;
    }

    size_t n = fread(buf, 1, fsize, f);
    fclose(f);

    PSDStream s;
    stream_init(&s, buf, n);
    PSDImage *img = psd_read_stream(&s);
    free(buf);
    return img;
}

PSDImage *psd_read_memory(const uint8_t *buf, size_t size) {
    PSDStream s;
    stream_init(&s, buf, size);
    return psd_read_stream(&s);
}

void psd_free(PSDImage *img) {
    if (!img) return;
    free(img->data);
    img->data = NULL;
    free(img);
}
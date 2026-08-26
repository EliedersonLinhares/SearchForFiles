/* ================================================================== */
/*  image_codec.h                                                       */
/*  Biblioteca modular para leitura e escrita de imagens via FFmpeg     */
/*  Suporta: PNG, JPEG, BMP, TIFF, WebP, GIF, TGA, PBM/PGM/PPM,       */
/*           HDR, EXR, HEIF/HEIC, AVIF, JXL, DDS, PCX, XWD, SGI      */
/* ================================================================== */
#ifndef IMAGE_CODEC_H
#define IMAGE_CODEC_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Formatos de pixel suportados internamente ───────────────────── */
typedef enum {
    IC_FMT_RGB24  = 0,  /* 3 bytes por pixel: R G B                  */
    IC_FMT_RGBA32 = 1,  /* 4 bytes por pixel: R G B A                */
    IC_FMT_GRAY8  = 2,  /* 1 byte  por pixel: luminância             */
    IC_FMT_GRAY16 = 3,  /* 2 bytes por pixel: luminância 16-bit      */
    IC_FMT_RGB48  = 4,  /* 6 bytes por pixel: RGB 16-bit (HDR/EXR)   */
    IC_FMT_RGBA64 = 5,  /* 8 bytes por pixel: RGBA 16-bit            */
} ICPixelFormat;

/* ── Resultado de leitura ────────────────────────────────────────── */
typedef struct {
    uint8_t      *data;          /* buffer de pixels (heap)           */
    int           width;
    int           height;
    ICPixelFormat format;        /* formato do buffer acima           */
    int           bytes_per_pixel;
    char          mime_type[64]; /* ex: "image/png"                   */
    char          codec_name[64];/* ex: "png", "mjpeg", "webp"        */
    char          error[256];    /* vazio se ok                       */
} ICImage;

/* ── Opções de escrita ───────────────────────────────────────────── */
typedef struct {
    int quality;        /* JPEG/WebP: 1-100 (padrão 90)              */
    int compression;    /* PNG: 0-9  (padrão 6)                      */
    int lossless;       /* WebP/AVIF: 0=lossy 1=lossless             */
    int depth;          /* bits por canal: 8 ou 16 (PNG/TIFF/EXR)   */
    int preserve_alpha; /* 1 = manter canal alpha se disponível      */
} ICWriteOptions;

/* Retorna opções padrão */
ICWriteOptions ic_default_write_options(void);

/* ── Informações de formato ──────────────────────────────────────── */
typedef struct {
    const char *extension;   /* ex: ".png"                           */
    const char *mime_type;   /* ex: "image/png"                      */
    const char *description; /* ex: "Portable Network Graphics"      */
    int         supports_alpha;
    int         supports_16bit;
    int         supports_lossless;
    int         supports_animation; /* GIF, WebP animado, APNG       */
} ICFormatInfo;

/* ── API principal ───────────────────────────────────────────────── */

/**
 * Lê uma imagem de qualquer formato suportado.
 * O formato é detectado automaticamente pelo conteúdo (não extensão).
 *
 * @param filepath  Caminho do arquivo
 * @param out_fmt   Formato de pixel desejado no buffer de saída
 * @return          ICImage alocado em heap — libere com ic_free()
 */
ICImage *ic_read(const char *filepath, ICPixelFormat out_fmt);

/**
 * Lê uma imagem a partir de buffer em memória (útil para streams/zip).
 */
ICImage *ic_read_memory(const uint8_t *buf, int buf_size,
                        ICPixelFormat out_fmt);

/**
 * Salva uma imagem no formato indicado pela extensão do caminho.
 *
 * @param img       Imagem a salvar
 * @param filepath  Caminho de saída (.png, .jpg, .webp, .tiff, etc.)
 * @param opts      Opções de escrita (NULL = padrão)
 * @return          0 = sucesso, -1 = erro
 */
int ic_write(const ICImage *img, const char *filepath,
             const ICWriteOptions *opts);

/**
 * Salva uma imagem em buffer em memória.
 *
 * @param img       Imagem a salvar
 * @param ext       Extensão do formato (".png", ".jpg", etc.)
 * @param opts      Opções de escrita (NULL = padrão)
 * @param out_size  Tamanho do buffer retornado
 * @return          Buffer alocado em heap — libere com ic_free_buffer()
 */
uint8_t *ic_write_memory(const ICImage *img, const char *ext,
                         const ICWriteOptions *opts, int *out_size);

/**
 * Converte o buffer de pixels para outro formato de pixel.
 * Retorna nova ICImage — a original não é modificada.
 */
ICImage *ic_convert(const ICImage *src, ICPixelFormat dst_fmt);

/**
 * Redimensiona a imagem.
 * filter: 0=bilinear, 1=bicúbico, 2=lanczos (melhor qualidade)
 */
ICImage *ic_resize(const ICImage *src, int new_w, int new_h, int filter);

/**
 * Retorna informações sobre um formato a partir da extensão.
 * Retorna NULL se o formato não for suportado.
 */
const ICFormatInfo *ic_format_info(const char *extension);

/**
 * Lista todos os formatos suportados.
 *
 * @param count  Recebe o número de formatos retornados
 * @return       Array estático (não liberar)
 */
const ICFormatInfo **ic_supported_formats(int *count);

/**
 * Detecta o formato de um arquivo pelo conteúdo (magic bytes).
 * Retorna a extensão detectada (ex: ".png") ou NULL se desconhecido.
 */
const char *ic_detect_format(const char *filepath);

/**
 * Verifica se um formato é suportado para leitura.
 */
int ic_can_read(const char *extension);

/**
 * Verifica se um formato é suportado para escrita.
 */
int ic_can_write(const char *extension);

/** Libera uma ICImage */
void ic_free(ICImage *img);

/** Libera buffer retornado por ic_write_memory */
void ic_free_buffer(uint8_t *buf);

#ifdef __cplusplus
}
#endif
#endif /* IMAGE_CODEC_H */
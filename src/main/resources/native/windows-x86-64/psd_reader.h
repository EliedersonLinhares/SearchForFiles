/* ================================================================== */
/*  psd_reader.h                                                        */
/*  Leitura de arquivos PSD/PSB (Adobe Photoshop) — flatten to RGB(A)  */
/*  Suporta: PSD v1 e PSB v2, 8/16/32 bits por canal,                  */
/*           RGB, RGBA, Grayscale, CMYK (convertido para RGB)           */
/* ================================================================== */
#ifndef PSD_READER_H
#define PSD_READER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum {
    PSD_COLOR_BITMAP    = 0,
    PSD_COLOR_GRAYSCALE = 1,
    PSD_COLOR_INDEXED   = 2,
    PSD_COLOR_RGB       = 3,
    PSD_COLOR_CMYK      = 4,
    PSD_COLOR_MULTICHANNEL = 7,
    PSD_COLOR_DUOTONE   = 8,
    PSD_COLOR_LAB       = 9,
} PSDColorMode;

typedef struct {
    uint8_t  *data;          /* buffer RGB24 ou RGBA32 (heap/malloc)  */
    int       width;
    int       height;
    int       channels;      /* canais originais do PSD               */
    int       depth;         /* bits por canal: 8, 16 ou 32           */
    int       has_alpha;     /* 1 se tem canal alpha                  */
    PSDColorMode color_mode;
    char      error[256];
} PSDImage;

/**
 * Lê um arquivo PSD/PSB e retorna os pixels achatados em RGB24 ou RGBA32.
 * Sempre converte para 8 bits por canal na saída.
 *
 * @param filepath  Caminho do arquivo .psd ou .psb
 * @return          PSDImage alocado em heap — libere com psd_free()
 */
PSDImage *psd_read(const char *filepath);

/**
 * Lê PSD de buffer em memória.
 */
PSDImage *psd_read_memory(const uint8_t *buf, size_t size);

/**
 * Libera PSDImage retornado por psd_read / psd_read_memory.
 */
void psd_free(PSDImage *img);

#ifdef __cplusplus
}
#endif
#endif /* PSD_READER_H */
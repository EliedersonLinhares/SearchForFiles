/* ================================================================== */
/*  video_thumbnail.h                                                   */
/*  Biblioteca modular para captura de frames de vídeo via FFmpeg       */
/* ================================================================== */
#ifndef VIDEO_THUMBNAIL_H
#define VIDEO_THUMBNAIL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ── Resultado da captura ─────────────────────────────────────────── */
typedef struct {
    uint8_t *data;        /* buffer RGB24: width * height * 3 bytes   */
    int      width;
    int      height;
    double   timestamp_s; /* segundo exato do frame capturado         */
    char     error[256];  /* mensagem de erro (vazio se ok)            */
} ThumbnailResult;

/* ── Opções de captura ────────────────────────────────────────────── */
typedef struct {
    /* Posição desejada (0.0 = início, 1.0 = fim).
       Padrão recomendado: 0.33 a 0.66 (meio do vídeo).              */
    double position_ratio;

    /* Dimensões do output. 0 = manter resolução original.            */
    int out_width;
    int out_height;

    /* Tentativas extras caso o frame na posição seja inválido.        */
    int max_attempts;

    /* Algoritmo de escala (SWS_BILINEAR, SWS_LANCZOS, etc.)          */
    int scale_flags;
} ThumbnailOptions;

/* Retorna opções padrão (meio do vídeo, resolução original) */
ThumbnailOptions thumbnail_default_options(void);

/* ── API principal ────────────────────────────────────────────────── */

/**
 * Captura um frame do vídeo na posição indicada pelas opções.
 *
 * @param filepath  Caminho do arquivo de vídeo
 * @param opts      Opções de captura (NULL = padrão)
 * @return          ThumbnailResult alocado em heap — libere com thumbnail_free()
 */
ThumbnailResult *thumbnail_capture(const char *filepath,
                                   const ThumbnailOptions *opts);

/**
 * Salva o resultado como arquivo PNG ou JPEG.
 *
 * @param result    Resultado de thumbnail_capture()
 * @param out_path  Caminho de saída (extensão define formato: .png / .jpg)
 * @return          0 = sucesso, -1 = erro
 */
int thumbnail_save(const ThumbnailResult *result, const char *out_path);

/**
 * Libera a memória alocada por thumbnail_capture().
 */
void thumbnail_free(ThumbnailResult *result);

/**
 * Retorna a duração do vídeo em segundos sem decodificar frames.
 * Retorna -1.0 em caso de erro.
 */
double thumbnail_get_duration(const char *filepath);

#ifdef __cplusplus
}
#endif
#endif /* VIDEO_THUMBNAIL_H */


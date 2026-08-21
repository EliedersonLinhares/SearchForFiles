/* ================================================================== */
/*  thumbnail_jni.c                                                     */
/*  Expõe video_thumbnail.h para o Java via JNI                        */
/* ================================================================== */
#include <jni.h>
#include <string.h>
#include "video_thumbnail.h"

/* Nome completo gerado pelo javac -h — não abreviar */
#define FN(name) Java_com_esl_searchforfiles_Video_VideoThumbnail_##name

/* Se o pacote for diferente, rode:
     javac -h . VideoThumbnail.java
   e use o prefixo gerado no .h */

/* ------------------------------------------------------------------ */
/*  captureThumbnail                                                    */
/*  Retorna byte[] RGB24, ou null em caso de erro                       */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
FN(captureThumbnail)(JNIEnv *env, jclass cls,
                     jstring  jpath,
                     jdouble  position_ratio,
                     jint     out_width,
                     jint     out_height)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    ThumbnailOptions opts = thumbnail_default_options();
    opts.position_ratio   = (double)position_ratio;
    opts.out_width        = (int)out_width;
    opts.out_height       = (int)out_height;
    opts.max_attempts     = 5;

    ThumbnailResult *res = thumbnail_capture(path, &opts);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (!res) return NULL;

    if (res->error[0] != '\0') {
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, res->error);
        thumbnail_free(res);
        return NULL;
    }

    /*
     * Empacotar em um único array (sem campos estáticos compartilhados):
     * [ width(4) | height(4) | timestamp_ms(8) | RGB24... ]
     */
    int    rgb_size  = res->width * res->height * 3;
    int    total     = 16 + rgb_size;
    int64_t ts_ms   = (int64_t)(res->timestamp_s * 1000.0);

    jbyteArray arr = (*env)->NewByteArray(env, total);
    if (!arr) { thumbnail_free(res); return NULL; }

    jbyte header[16];
    /* width big-endian */
    header[0] = (res->width  >> 24) & 0xFF;
    header[1] = (res->width  >> 16) & 0xFF;
    header[2] = (res->width  >>  8) & 0xFF;
    header[3] =  res->width         & 0xFF;
    /* height big-endian */
    header[4] = (res->height >> 24) & 0xFF;
    header[5] = (res->height >> 16) & 0xFF;
    header[6] = (res->height >>  8) & 0xFF;
    header[7] =  res->height        & 0xFF;
    /* timestamp_ms big-endian 64-bit */
    header[8]  = (ts_ms >> 56) & 0xFF;
    header[9]  = (ts_ms >> 48) & 0xFF;
    header[10] = (ts_ms >> 40) & 0xFF;
    header[11] = (ts_ms >> 32) & 0xFF;
    header[12] = (ts_ms >> 24) & 0xFF;
    header[13] = (ts_ms >> 16) & 0xFF;
    header[14] = (ts_ms >>  8) & 0xFF;
    header[15] =  ts_ms        & 0xFF;

    (*env)->SetByteArrayRegion(env, arr, 0,   16,       header);
    (*env)->SetByteArrayRegion(env, arr, 16,  rgb_size, (jbyte *)res->data);

    thumbnail_free(res);
    return arr;
}

/* ------------------------------------------------------------------ */
/*  saveThumbnail                                                       */
/*  Salva byte[] RGB24 diretamente como PNG ou JPEG                    */
/* ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
FN(saveThumbnail)(JNIEnv *env, jclass cls,
                  jbyteArray jdata,
                  jint width, jint height,
                  jstring jout_path)
{
    const char *out_path = (*env)->GetStringUTFChars(env, jout_path, NULL);
    jbyte      *raw      = (*env)->GetByteArrayElements(env, jdata, NULL);

    ThumbnailResult tmp = {0};
    tmp.data   = (uint8_t *)raw;
    tmp.width  = (int)width;
    tmp.height = (int)height;

    int ret = thumbnail_save(&tmp, out_path);

    (*env)->ReleaseByteArrayElements(env, jdata, raw, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, jout_path, out_path);
    return ret;
}

/* ------------------------------------------------------------------ */
/*  getDuration                                                         */
/* ------------------------------------------------------------------ */
JNIEXPORT jdouble JNICALL
FN(getDuration)(JNIEnv *env, jclass cls, jstring jpath)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    double dur = thumbnail_get_duration(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return dur;
}
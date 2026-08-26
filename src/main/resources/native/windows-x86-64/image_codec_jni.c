/* ================================================================== */
/*  image_codec_jni.c                                                   */
/* ================================================================== */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "image_codec.h"

/* ── Adicionar no image_codec_jni.c ─────────────────────────────── */
#include "psd_reader.h"

#define FN(name) Java_com_esl_searchforfiles_Video_ImageCodec_##name
/* Mesmo prefixo de pacote */
#define FN_PSD(name) Java_com_esl_searchforfiles_Video_ImageCodec_##name


/* ------------------------------------------------------------------ */
/*  readImage                                                           */
/*  Retorna: [width(4)|height(4)|bpp(1)|fmt(1)|pad(6)|pixels...]       */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
FN(readImage)(JNIEnv *env, jclass cls,
              jstring jpath, jint out_fmt)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    ICImage *img = ic_read(path, (ICPixelFormat)out_fmt);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (!img) return NULL;
    if (img->error[0] != '\0') {
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, img->error);
        ic_free(img);
        return NULL;
    }

    /* Header: width(4) height(4) bpp(1) fmt(1) codec(32) mime(32) pad(2) = 76 bytes */
    int    pixel_bytes = img->width * img->height * img->bytes_per_pixel;
    int    total       = 76 + pixel_bytes;
    jbyteArray arr = (*env)->NewByteArray(env, total);
    if (!arr) { ic_free(img); return NULL; }

    jbyte header[76];
    memset(header, 0, sizeof(header));
    header[0] = (img->width  >> 24) & 0xFF;
    header[1] = (img->width  >> 16) & 0xFF;
    header[2] = (img->width  >>  8) & 0xFF;
    header[3] =  img->width         & 0xFF;
    header[4] = (img->height >> 24) & 0xFF;
    header[5] = (img->height >> 16) & 0xFF;
    header[6] = (img->height >>  8) & 0xFF;
    header[7] =  img->height        & 0xFF;
    header[8] = (jbyte)img->bytes_per_pixel;
    header[9] = (jbyte)img->format;
    memcpy(header + 10, img->codec_name,
           strlen(img->codec_name) < 32 ? strlen(img->codec_name) : 31);
    memcpy(header + 42, img->mime_type,
           strlen(img->mime_type)  < 32 ? strlen(img->mime_type)  : 31);

    (*env)->SetByteArrayRegion(env, arr, 0,  76,          header);
    (*env)->SetByteArrayRegion(env, arr, 76, pixel_bytes,
                               (jbyte *)img->data);
    ic_free(img);
    return arr;
}

/* ------------------------------------------------------------------ */
/*  readImageFromMemory                                                  */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
FN(readImageFromMemory)(JNIEnv *env, jclass cls,
                        jbyteArray jbuf, jint out_fmt)
{
    int    sz  = (*env)->GetArrayLength(env, jbuf);
    jbyte *raw = (*env)->GetByteArrayElements(env, jbuf, NULL);

    ICImage *img = ic_read_memory((const uint8_t *)raw, sz,
                                  (ICPixelFormat)out_fmt);
    (*env)->ReleaseByteArrayElements(env, jbuf, raw, JNI_ABORT);

    if (!img) return NULL;
    if (img->error[0] != '\0') {
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, img->error);
        ic_free(img);
        return NULL;
    }

    int    pixel_bytes = img->width * img->height * img->bytes_per_pixel;
    int    total       = 76 + pixel_bytes;
    jbyteArray arr     = (*env)->NewByteArray(env, total);

    jbyte header[76];
    memset(header, 0, sizeof(header));
    header[0] = (img->width  >> 24) & 0xFF;
    header[1] = (img->width  >> 16) & 0xFF;
    header[2] = (img->width  >>  8) & 0xFF;
    header[3] =  img->width         & 0xFF;
    header[4] = (img->height >> 24) & 0xFF;
    header[5] = (img->height >> 16) & 0xFF;
    header[6] = (img->height >>  8) & 0xFF;
    header[7] =  img->height        & 0xFF;
    header[8] = (jbyte)img->bytes_per_pixel;
    header[9] = (jbyte)img->format;
    memcpy(header + 10, img->codec_name,
           strlen(img->codec_name) < 32 ? strlen(img->codec_name) : 31);
    memcpy(header + 42, img->mime_type,
           strlen(img->mime_type)  < 32 ? strlen(img->mime_type)  : 31);

    (*env)->SetByteArrayRegion(env, arr, 0,  76,          header);
    (*env)->SetByteArrayRegion(env, arr, 76, pixel_bytes,
                               (jbyte *)img->data);
    ic_free(img);
    return arr;
}

/* ------------------------------------------------------------------ */
/*  writeImage                                                          */
/* ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
FN(writeImage)(JNIEnv *env, jclass cls,
               jbyteArray jpixels,
               jint width, jint height,
               jint fmt, jint bpp,
               jstring jpath,
               jint quality, jint compression,
               jint lossless, jint depth)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    jbyte      *raw  = (*env)->GetByteArrayElements(env, jpixels, NULL);

    ICImage tmp = {0};
    tmp.data            = (uint8_t *)raw;
    tmp.width           = (int)width;
    tmp.height          = (int)height;
    tmp.format          = (ICPixelFormat)fmt;
    tmp.bytes_per_pixel = (int)bpp;

    ICWriteOptions opts = ic_default_write_options();
    opts.quality     = quality;
    opts.compression = compression;
    opts.lossless    = lossless;
    opts.depth       = depth;

    int ret = ic_write(&tmp, path, &opts);

    (*env)->ReleaseByteArrayElements(env, jpixels, raw, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return ret;
}

/* ------------------------------------------------------------------ */
/*  writeImageToMemory                                                  */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
FN(writeImageToMemory)(JNIEnv *env, jclass cls,
                       jbyteArray jpixels,
                       jint width, jint height,
                       jint fmt, jint bpp,
                       jstring jext,
                       jint quality, jint compression,
                       jint lossless, jint depth)
{
    const char *ext = (*env)->GetStringUTFChars(env, jext, NULL);
    jbyte      *raw = (*env)->GetByteArrayElements(env, jpixels, NULL);

    ICImage tmp = {0};
    tmp.data            = (uint8_t *)raw;
    tmp.width           = (int)width;
    tmp.height          = (int)height;
    tmp.format          = (ICPixelFormat)fmt;
    tmp.bytes_per_pixel = (int)bpp;

    ICWriteOptions opts = ic_default_write_options();
    opts.quality     = quality;
    opts.compression = compression;
    opts.lossless    = lossless;
    opts.depth       = depth;

    int out_size = 0;
    uint8_t *out = ic_write_memory(&tmp, ext, &opts, &out_size);

    (*env)->ReleaseByteArrayElements(env, jpixels, raw, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, jext, ext);

    if (!out || out_size == 0) return NULL;

    jbyteArray arr = (*env)->NewByteArray(env, out_size);
    (*env)->SetByteArrayRegion(env, arr, 0, out_size, (jbyte *)out);
    ic_free_buffer(out);
    return arr;
}

/* ------------------------------------------------------------------ */
/*  detectFormat                                                        */
/* ------------------------------------------------------------------ */
JNIEXPORT jstring JNICALL
FN(detectFormat)(JNIEnv *env, jclass cls, jstring jpath)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    const char *ext  = ic_detect_format(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return ext ? (*env)->NewStringUTF(env, ext) : NULL;
}

/* ------------------------------------------------------------------ */
/*  canRead / canWrite                                                  */
/* ------------------------------------------------------------------ */
JNIEXPORT jboolean JNICALL
FN(canRead)(JNIEnv *env, jclass cls, jstring jext) {
    const char *ext = (*env)->GetStringUTFChars(env, jext, NULL);
    jboolean r = ic_can_read(ext) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, jext, ext);
    return r;
}

JNIEXPORT jboolean JNICALL
FN(canWrite)(JNIEnv *env, jclass cls, jstring jext) {
    const char *ext = (*env)->GetStringUTFChars(env, jext, NULL);
    jboolean r = ic_can_write(ext) ? JNI_TRUE : JNI_FALSE;
    (*env)->ReleaseStringUTFChars(env, jext, ext);
    return r;
}
/*
 * readPSD
 * Retorna: [width(4)|height(4)|hasAlpha(1)|depth(2)|colorMode(1)|pad(0)|RGB(A)...]
 * Header = 12 bytes
 */
JNIEXPORT jbyteArray JNICALL
FN_PSD(readPSD)(JNIEnv *env, jclass cls, jstring jpath)
{
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    PSDImage   *img  = psd_read(path);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (!img) return NULL;

    if (img->error[0] != '\0') {
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, img->error);
        psd_free(img);
        return NULL;
    }

    int out_ch     = img->has_alpha ? 4 : 3;
    int pixel_size = img->width * img->height * out_ch;
    int total      = 12 + pixel_size;

    jbyteArray arr = (*env)->NewByteArray(env, total);
    if (!arr) { psd_free(img); return NULL; }

    jbyte header[12];
    header[0]  = (img->width  >> 24) & 0xFF;
    header[1]  = (img->width  >> 16) & 0xFF;
    header[2]  = (img->width  >>  8) & 0xFF;
    header[3]  =  img->width         & 0xFF;
    header[4]  = (img->height >> 24) & 0xFF;
    header[5]  = (img->height >> 16) & 0xFF;
    header[6]  = (img->height >>  8) & 0xFF;
    header[7]  =  img->height        & 0xFF;
    header[8]  = (jbyte)img->has_alpha;
    header[9]  = (jbyte)(img->depth >> 8);
    header[10] = (jbyte)(img->depth & 0xFF);
    header[11] = (jbyte)img->color_mode;

    (*env)->SetByteArrayRegion(env, arr, 0,  12,         header);
    (*env)->SetByteArrayRegion(env, arr, 12, pixel_size, (jbyte *)img->data);

    psd_free(img);
    return arr;
}

/* readPSDFromMemory */
JNIEXPORT jbyteArray JNICALL
FN_PSD(readPSDFromMemory)(JNIEnv *env, jclass cls, jbyteArray jbuf)
{
    int    sz  = (*env)->GetArrayLength(env, jbuf);
    jbyte *raw = (*env)->GetByteArrayElements(env, jbuf, NULL);

    PSDImage *img = psd_read_memory((const uint8_t *)raw, (size_t)sz);
    (*env)->ReleaseByteArrayElements(env, jbuf, raw, JNI_ABORT);

    if (!img) return NULL;

    if (img->error[0] != '\0') {
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, img->error);
        psd_free(img);
        return NULL;
    }

    int out_ch     = img->has_alpha ? 4 : 3;
    int pixel_size = img->width * img->height * out_ch;
    int total      = 12 + pixel_size;

    jbyteArray arr = (*env)->NewByteArray(env, total);
    if (!arr) { psd_free(img); return NULL; }

    jbyte header[12];
    header[0]  = (img->width  >> 24) & 0xFF;
    header[1]  = (img->width  >> 16) & 0xFF;
    header[2]  = (img->width  >>  8) & 0xFF;
    header[3]  =  img->width         & 0xFF;
    header[4]  = (img->height >> 24) & 0xFF;
    header[5]  = (img->height >> 16) & 0xFF;
    header[6]  = (img->height >>  8) & 0xFF;
    header[7]  =  img->height        & 0xFF;
    header[8]  = (jbyte)img->has_alpha;
    header[9]  = (jbyte)(img->depth >> 8);
    header[10] = (jbyte)(img->depth & 0xFF);
    header[11] = (jbyte)img->color_mode;

    (*env)->SetByteArrayRegion(env, arr, 0,  12,         header);
    (*env)->SetByteArrayRegion(env, arr, 12, pixel_size, (jbyte *)img->data);

    psd_free(img);
    return arr;
}
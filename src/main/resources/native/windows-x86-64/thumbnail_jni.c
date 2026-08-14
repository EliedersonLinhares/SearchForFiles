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

    ThumbnailOptions opts  = thumbnail_default_options();
    opts.position_ratio    = (double)position_ratio;
    opts.out_width         = (int)out_width;
    opts.out_height        = (int)out_height;
    opts.max_attempts      = 5;

    ThumbnailResult *res = thumbnail_capture(path, &opts);
    (*env)->ReleaseStringUTFChars(env, jpath, path);

    if (!res) return NULL;

    if (res->error[0] != '\0') {
        /* Propagar erro como exceção Java */
        jclass ex = (*env)->FindClass(env, "java/lang/RuntimeException");
        (*env)->ThrowNew(env, ex, res->error);
        thumbnail_free(res);
        return NULL;
    }

    int size        = res->width * res->height * 3;
    jbyteArray arr  = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, arr, 0, size, (jbyte *)res->data);

    /* Guardar metadados em campos estáticos para o Java ler depois  */
    jclass  jcls   = (*env)->FindClass(env,
        "com/esl/searchforfiles/Video/VideoThumbnail");
    jfieldID fW    = (*env)->GetStaticFieldID(env, jcls, "lastWidth",  "I");
    jfieldID fH    = (*env)->GetStaticFieldID(env, jcls, "lastHeight", "I");
    jfieldID fTS   = (*env)->GetStaticFieldID(env, jcls, "lastTimestampSeconds", "D");
    (*env)->SetStaticIntField   (env, jcls, fW,  res->width);
    (*env)->SetStaticIntField   (env, jcls, fH,  res->height);
    (*env)->SetStaticDoubleField(env, jcls, fTS, res->timestamp_s);

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
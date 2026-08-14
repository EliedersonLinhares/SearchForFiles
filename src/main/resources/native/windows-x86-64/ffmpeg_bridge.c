#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/imgutils.h>
#include <libavutil/opt.h>
#include <libavutil/channel_layout.h>
#include <libswscale/swscale.h>
#include <libswresample/swresample.h>

/* ------------------------------------------------------------------ */
/*  Fila simples thread-safe para frames decodificados                  */
/* ------------------------------------------------------------------ */
#define QUEUE_SIZE 16

typedef struct {
    uint8_t **data;   /* array de ponteiros para buffers */
    int      *sizes;  /* tamanhos de cada buffer         */
    int       head;
    int       tail;
    int       count;
    int       capacity;
} FrameQueue;

static FrameQueue *queue_create(int capacity) {
    FrameQueue *q  = calloc(1, sizeof(FrameQueue));
    q->data        = calloc(capacity, sizeof(uint8_t *));
    q->sizes       = calloc(capacity, sizeof(int));
    q->capacity    = capacity;
    return q;
}

static void queue_destroy(FrameQueue *q) {
    if (!q) return;
    for (int i = 0; i < q->capacity; i++) {
        if (q->data[i]) { free(q->data[i]); q->data[i] = NULL; }
    }
    free(q->data);
    free(q->sizes);
    free(q);
}

/* Retorna 1 se inseriu, 0 se cheio */
static int queue_push(FrameQueue *q, uint8_t *buf, int size) {
    if (q->count >= q->capacity) return 0;
    q->data[q->tail]  = buf;
    q->sizes[q->tail] = size;
    q->tail = (q->tail + 1) % q->capacity;
    q->count++;
    return 1;
}

/* Retorna buffer (caller libera) ou NULL se vazio */
static uint8_t *queue_pop(FrameQueue *q, int *size_out) {
    if (q->count == 0) return NULL;
    uint8_t *buf = q->data[q->head];
    *size_out    = q->sizes[q->head];
    q->data[q->head] = NULL;
    q->head = (q->head + 1) % q->capacity;
    q->count--;
    return buf;
}

/* ------------------------------------------------------------------ */
/*  Contexto principal                                                  */
/* ------------------------------------------------------------------ */
typedef struct {
    AVFormatContext  *fmt_ctx;
    AVCodecContext   *video_ctx;
    AVCodecContext   *audio_ctx;
    struct SwsContext *sws_ctx;
    SwrContext        *swr_ctx;

    int video_stream_idx;
    int audio_stream_idx;
    int total_audio_streams;

    AVFrame  *decode_frame;   /* frame de trabalho reutilizável   */
    AVFrame  *rgb_frame;
    uint8_t  *rgb_buffer;
    int       rgb_buffer_size;

    AVPacket *packet;

    /* Filas de frames prontos para o Java consumir */
    FrameQueue *video_queue;
    FrameQueue *audio_queue;

    int eof;   /* 1 quando o arquivo terminou */
} PlayerContext;

static PlayerContext *get_ctx(jlong ptr) {
    return (PlayerContext *)(uintptr_t)ptr;
}

/* ------------------------------------------------------------------ */
/*  Decode: lê pacotes e preenche as filas até ambas terem ao menos    */
/*  1 frame, ou até EOF.                                                */
/* ------------------------------------------------------------------ */
static void decode_until_queues_have_data(PlayerContext *ctx) {
    if (ctx->eof) return;

    /* Continuar lendo enquanto alguma fila estiver vazia */
    while ((ctx->video_queue->count == 0 || ctx->audio_queue->count == 0)
           && ctx->video_queue->count < QUEUE_SIZE
           && ctx->audio_queue->count < QUEUE_SIZE) {

        if (av_read_frame(ctx->fmt_ctx, ctx->packet) < 0) {
            ctx->eof = 1;
            break;
        }

        /* ── Pacote de vídeo ── */
        if (ctx->packet->stream_index == ctx->video_stream_idx
                && ctx->video_ctx) {

            if (avcodec_send_packet(ctx->video_ctx, ctx->packet) >= 0) {
                while (avcodec_receive_frame(ctx->video_ctx,
                                             ctx->decode_frame) == 0) {

                    /* Converter para RGB24 */
                    sws_scale(ctx->sws_ctx,
                              (const uint8_t * const *)ctx->decode_frame->data,
                              ctx->decode_frame->linesize,
                              0, ctx->video_ctx->height,
                              ctx->rgb_frame->data,
                              ctx->rgb_frame->linesize);

                    /* Copiar para buffer próprio e enfileirar */
                    uint8_t *copy = malloc(ctx->rgb_buffer_size);
                    memcpy(copy, ctx->rgb_buffer, ctx->rgb_buffer_size);

                    if (!queue_push(ctx->video_queue, copy,
                                    ctx->rgb_buffer_size)) {
                        free(copy); /* fila cheia, descartar */
                    }

                    av_frame_unref(ctx->decode_frame);
                }
            }
        }
        /* ── Pacote de áudio ── */
        else if (ctx->packet->stream_index == ctx->audio_stream_idx
                 && ctx->audio_ctx && ctx->swr_ctx) {

            if (avcodec_send_packet(ctx->audio_ctx, ctx->packet) >= 0) {
                while (avcodec_receive_frame(ctx->audio_ctx,
                                             ctx->decode_frame) == 0) {

                    int out_samples = swr_get_out_samples(
                        ctx->swr_ctx, ctx->decode_frame->nb_samples);

                    int buf_size = out_samples * 2 * 2; /* stereo S16 */
                    uint8_t *pcm = malloc(buf_size);

                    uint8_t *out_ptr = pcm;
                    int converted = swr_convert(
                        ctx->swr_ctx,
                        &out_ptr, out_samples,
                        (const uint8_t **)ctx->decode_frame->data,
                        ctx->decode_frame->nb_samples);

                    int actual = converted * 2 * 2;
                    if (actual > 0) {
                        if (!queue_push(ctx->audio_queue, pcm, actual)) {
                            free(pcm); /* fila cheia */
                        }
                    } else {
                        free(pcm);
                    }

                    av_frame_unref(ctx->decode_frame);
                }
            }
        }

        av_packet_unref(ctx->packet);
    }
}

/* ------------------------------------------------------------------ */
/*  openVideo                                                           */
/* ------------------------------------------------------------------ */
JNIEXPORT jlong JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_openVideo(
        JNIEnv *env, jobject obj, jstring jpath) {

    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);

    PlayerContext *ctx = calloc(1, sizeof(PlayerContext));
    if (!ctx) { (*env)->ReleaseStringUTFChars(env, jpath, path); return 0L; }

    /* Abrir container */
    if (avformat_open_input(&ctx->fmt_ctx, path, NULL, NULL) < 0) {
        fprintf(stderr, "[JNI] Erro ao abrir: %s\n", path);
        free(ctx);
        (*env)->ReleaseStringUTFChars(env, jpath, path);
        return 0L;
    }
    avformat_find_stream_info(ctx->fmt_ctx, NULL);

    /* Contar streams de áudio */
    for (unsigned i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        if (ctx->fmt_ctx->streams[i]->codecpar->codec_type
                == AVMEDIA_TYPE_AUDIO)
            ctx->total_audio_streams++;
    }

    ctx->video_stream_idx = av_find_best_stream(
        ctx->fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    ctx->audio_stream_idx = av_find_best_stream(
        ctx->fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);

    /* ── Decoder de vídeo ── */
    if (ctx->video_stream_idx >= 0) {
        AVCodecParameters *par =
            ctx->fmt_ctx->streams[ctx->video_stream_idx]->codecpar;
        const AVCodec *codec = avcodec_find_decoder(par->codec_id);

        ctx->video_ctx = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(ctx->video_ctx, par);
        ctx->video_ctx->thread_count = 0; /* auto */
        ctx->video_ctx->thread_type  = FF_THREAD_FRAME;
        avcodec_open2(ctx->video_ctx, codec, NULL);

        ctx->sws_ctx = sws_getContext(
            ctx->video_ctx->width, ctx->video_ctx->height,
            ctx->video_ctx->pix_fmt,
            ctx->video_ctx->width, ctx->video_ctx->height,
            AV_PIX_FMT_RGB24,
            SWS_BILINEAR, NULL, NULL, NULL);

        ctx->rgb_buffer_size = av_image_get_buffer_size(
            AV_PIX_FMT_RGB24,
            ctx->video_ctx->width, ctx->video_ctx->height, 1);

        ctx->rgb_buffer = av_malloc(ctx->rgb_buffer_size);
        ctx->rgb_frame  = av_frame_alloc();

        av_image_fill_arrays(
            ctx->rgb_frame->data, ctx->rgb_frame->linesize,
            ctx->rgb_buffer, AV_PIX_FMT_RGB24,
            ctx->video_ctx->width, ctx->video_ctx->height, 1);
    }

    /* ── Decoder de áudio ── */
    if (ctx->audio_stream_idx >= 0) {
        AVCodecParameters *par =
            ctx->fmt_ctx->streams[ctx->audio_stream_idx]->codecpar;
        const AVCodec *codec = avcodec_find_decoder(par->codec_id);

        ctx->audio_ctx = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(ctx->audio_ctx, par);
        avcodec_open2(ctx->audio_ctx, codec, NULL);

        /* SWR: qualquer formato de entrada → stereo S16 44100Hz */
        swr_alloc_set_opts2(
            &ctx->swr_ctx,
            &(AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO,
            AV_SAMPLE_FMT_S16,
            ctx->audio_ctx->sample_rate, /* manter sample rate original */
            &ctx->audio_ctx->ch_layout,
            ctx->audio_ctx->sample_fmt,
            ctx->audio_ctx->sample_rate,
            0, NULL);
        swr_init(ctx->swr_ctx);
    }

    ctx->decode_frame = av_frame_alloc();
    ctx->packet       = av_packet_alloc();

    /* Criar filas */
    ctx->video_queue = queue_create(QUEUE_SIZE);
    ctx->audio_queue = queue_create(QUEUE_SIZE);
    ctx->eof         = 0;

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jlong)(uintptr_t)ctx;
}

/* ------------------------------------------------------------------ */
/*  grabNextFrames — lê do FFmpeg e enfileira vídeo+áudio juntos       */
/*  Chamado pelo Java antes de pegar vídeo ou áudio separadamente.     */
/* ------------------------------------------------------------------ */
JNIEXPORT void JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_grabNextFrames(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return;
    decode_until_queues_have_data(ctx);
}

/* ------------------------------------------------------------------ */
/*  pollVideoFrame — retira um frame de vídeo da fila                  */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_pollVideoFrame(
        JNIEnv *env, jobject obj, jlong ptr) {

    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return NULL;

    int size = 0;
    uint8_t *buf = queue_pop(ctx->video_queue, &size);
    if (!buf) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, result, 0, size, (jbyte *)buf);
    free(buf);
    return result;
}

/* ------------------------------------------------------------------ */
/*  pollAudioFrame — retira um frame de áudio da fila                  */
/* ------------------------------------------------------------------ */
JNIEXPORT jbyteArray JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_pollAudioFrame(
        JNIEnv *env, jobject obj, jlong ptr) {

    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return NULL;

    int size = 0;
    uint8_t *buf = queue_pop(ctx->audio_queue, &size);
    if (!buf) return NULL;

    jbyteArray result = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, result, 0, size, (jbyte *)buf);
    free(buf);
    return result;
}

/* ------------------------------------------------------------------ */
/*  isEOF                                                               */
/* ------------------------------------------------------------------ */
JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_isEOF(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return JNI_TRUE;
    return (ctx->eof && ctx->video_queue->count == 0
                     && ctx->audio_queue->count == 0)
           ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------------ */
/*  seekToSeconds                                                       */
/* ------------------------------------------------------------------ */
JNIEXPORT void JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_seekToSeconds(
        JNIEnv *env, jobject obj, jlong ptr, jdouble seconds) {

    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return;

    int64_t ts = (int64_t)(seconds * AV_TIME_BASE);
    av_seek_frame(ctx->fmt_ctx, -1, ts, AVSEEK_FLAG_BACKWARD);

    if (ctx->video_ctx) avcodec_flush_buffers(ctx->video_ctx);
    if (ctx->audio_ctx) avcodec_flush_buffers(ctx->audio_ctx);

    /* Limpar filas após seek */
    int sz;
    uint8_t *b;
    while ((b = queue_pop(ctx->video_queue, &sz))) free(b);
    while ((b = queue_pop(ctx->audio_queue, &sz))) free(b);

    ctx->eof = 0;
}

/* ------------------------------------------------------------------ */
/*  closeVideo                                                          */
/* ------------------------------------------------------------------ */
JNIEXPORT void JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_closeVideo(
        JNIEnv *env, jobject obj, jlong ptr) {

    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return;

    if (ctx->sws_ctx)    sws_freeContext(ctx->sws_ctx);
    if (ctx->swr_ctx)    swr_free(&ctx->swr_ctx);
    if (ctx->video_ctx)  avcodec_free_context(&ctx->video_ctx);
    if (ctx->audio_ctx)  avcodec_free_context(&ctx->audio_ctx);
    if (ctx->fmt_ctx)    avformat_close_input(&ctx->fmt_ctx);
    if (ctx->decode_frame) av_frame_free(&ctx->decode_frame);
    if (ctx->rgb_frame)  av_frame_free(&ctx->rgb_frame);
    if (ctx->rgb_buffer) av_free(ctx->rgb_buffer);
    if (ctx->packet)     av_packet_free(&ctx->packet);

    queue_destroy(ctx->video_queue);
    queue_destroy(ctx->audio_queue);
    free(ctx);
}

/* ------------------------------------------------------------------ */
/*  Getters (iguais ao original)                                        */
/* ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getWidth(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx && ctx->video_ctx ? ctx->video_ctx->width : 0;
}

JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getHeight(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx && ctx->video_ctx ? ctx->video_ctx->height : 0;
}

JNIEXPORT jdouble JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getFrameRate(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || ctx->video_stream_idx < 0) return 30.0;
    AVRational r =
        ctx->fmt_ctx->streams[ctx->video_stream_idx]->avg_frame_rate;
    return r.den > 0 ? (double)r.num / r.den : 30.0;
}

JNIEXPORT jlong JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getTotalFrames(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || ctx->video_stream_idx < 0) return 0;
    return ctx->fmt_ctx->streams[ctx->video_stream_idx]->nb_frames;
}

JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getSampleRate(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx && ctx->audio_ctx ? ctx->audio_ctx->sample_rate : 0;
}

JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getAudioChannels(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx && ctx->audio_ctx
           ? ctx->audio_ctx->ch_layout.nb_channels : 0;
}

JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getTotalAudioStreams(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx ? ctx->total_audio_streams : 0;
}

JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getVideoCodecName(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || !ctx->video_ctx || !ctx->video_ctx->codec)
        return (*env)->NewStringUTF(env, "unknown");
    return (*env)->NewStringUTF(env, ctx->video_ctx->codec->name);
}
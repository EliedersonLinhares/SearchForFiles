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
typedef struct {
    double values[QUEUE_SIZE];
    int head, tail, count;
} PtsQueue;

static void ptsq_push(PtsQueue *q, double v) {
    if (q->count >= QUEUE_SIZE) return;
    q->values[q->tail] = v;
    q->tail = (q->tail + 1) % QUEUE_SIZE;
    q->count++;
}

static double ptsq_pop(PtsQueue *q) {
    if (q->count == 0) return -1.0;
    double v = q->values[q->head];
    q->head = (q->head + 1) % QUEUE_SIZE;
    q->count--;
    return v;
}
/* ------------------------------------------------------------------ */
/*  Faixas de áudio/legenda disponíveis no container                   */
/* ------------------------------------------------------------------ */
#define MAX_TRACKS 16

typedef struct {
    int  stream_idx;
    char lang[16];
} TrackInfo;

/* ------------------------------------------------------------------ */
/*  Fila de legendas de texto decodificadas                            */
/* ------------------------------------------------------------------ */
#define SUB_QUEUE_CAP 8

typedef struct {
    char   *text;
    double  start;
    double  end;
} SubtitleEntry;

typedef struct {
    SubtitleEntry entries[SUB_QUEUE_CAP];
    int head, tail, count;
} SubtitleQueue;

static SubtitleQueue *subqueue_create(void) {
    return calloc(1, sizeof(SubtitleQueue));
}

static int subqueue_push(SubtitleQueue *q, const char *text, double start, double end) {
    if (q->count >= SUB_QUEUE_CAP) return 0;
    q->entries[q->tail].text  = strdup(text);
    q->entries[q->tail].start = start;
    q->entries[q->tail].end   = end;
    q->tail = (q->tail + 1) % SUB_QUEUE_CAP;
    q->count++;
    return 1;
}

static int subqueue_pop(SubtitleQueue *q, char **text_out, double *start_out, double *end_out) {
    if (q->count == 0) return 0;
    *text_out  = q->entries[q->head].text;
    *start_out = q->entries[q->head].start;
    *end_out   = q->entries[q->head].end;
    q->head = (q->head + 1) % SUB_QUEUE_CAP;
    q->count--;
    return 1;
}

static void subqueue_destroy(SubtitleQueue *q) {
    if (!q) return;
    char *t; double s, e;
    while (subqueue_pop(q, &t, &s, &e)) free(t);
    free(q);
}

/* ------------------------------------------------------------------ */
/*  Extração de texto puro de legendas (SRT/mov_text/ASS-SSA)          */
/* ------------------------------------------------------------------ */
static void extract_plain_text(const char *raw, int is_ass, char *out, size_t out_size) {
    const char *src = raw;

    /* Formato ASS/SSA: Layer,Style,Name,MarginL,MarginR,MarginV,Effect,Texto
     * Pulamos os 8 primeiros campos separados por vírgula para chegar
     * apenas no texto do diálogo. */
    if (is_ass) {
        int commas = 0;
        while (*src && commas < 8) { if (*src == ',') commas++; src++; }
    }

    size_t o = 0;
    int in_tag = 0;
    for (; *src && o < out_size - 1; src++) {
        if (*src == '{') { in_tag = 1; continue; }   /* início de tag de estilo {\...} */
        if (*src == '}') { in_tag = 0; continue; }
        if (in_tag) continue;

        if (src[0] == '\\' && (src[1] == 'N' || src[1] == 'n')) {
            out[o++] = '\n';
            src++;
            continue;
        }
        out[o++] = *src;
    }
    out[o] = '\0';
}

/* ------------------------------------------------------------------ */
/*  Contexto principal                                                  */
/* ------------------------------------------------------------------ */
typedef struct {
    AVFormatContext  *fmt_ctx;
    AVCodecContext   *video_ctx;
    AVCodecContext   *audio_ctx;
    AVCodecContext   *subtitle_ctx;
    struct SwsContext *sws_ctx;
    SwrContext        *swr_ctx;

    int video_stream_idx;
    int audio_stream_idx;
    int subtitle_stream_idx;   /* -1 = nenhuma legenda ativa */
    int total_audio_streams;

    TrackInfo audio_tracks[MAX_TRACKS];
    int       audio_track_count;

    TrackInfo subtitle_tracks[MAX_TRACKS];
    int       subtitle_track_count;

    AVFrame  *decode_frame;   /* frame de trabalho reutilizável   */
    AVFrame  *rgb_frame;
    uint8_t  *rgb_buffer;
    int       rgb_buffer_size;

    AVPacket *packet;

    /* Filas de frames prontos para o Java consumir */
    FrameQueue    *video_queue;
    FrameQueue    *audio_queue;
    SubtitleQueue *subtitle_queue;

    int eof;   /* 1 quando o arquivo terminou */
} PlayerContext;

static PlayerContext *get_ctx(jlong ptr) {
    return (PlayerContext *)(uintptr_t)ptr;
}

/* ------------------------------------------------------------------ */
/*  Abertura/troca de faixa de áudio                                    */
/* ------------------------------------------------------------------ */
static int open_audio_stream(PlayerContext *ctx, int stream_idx) {
    if (ctx->audio_ctx) avcodec_free_context(&ctx->audio_ctx);
    if (ctx->swr_ctx)   swr_free(&ctx->swr_ctx);

    AVCodecParameters *par = ctx->fmt_ctx->streams[stream_idx]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) return 0;

    ctx->audio_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(ctx->audio_ctx, par);

    /* Vincula a base de tempo antes da inicialização (corrige timestamps MP3FLOAT) */
    ctx->audio_ctx->pkt_timebase = ctx->fmt_ctx->streams[stream_idx]->time_base;

    if (avcodec_open2(ctx->audio_ctx, codec, NULL) < 0) {
        avcodec_free_context(&ctx->audio_ctx);
        return 0;
    }

    /* Força a saída do conversor SWR a ser SEMPRE STEREO (2 canais) */
    swr_alloc_set_opts2(
        &ctx->swr_ctx,
        &(AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO, /* Canal de saída (Fixo Estéreo) */
        AV_SAMPLE_FMT_S16,
        ctx->audio_ctx->sample_rate,
        &ctx->audio_ctx->ch_layout,                 /* Canal de entrada do arquivo */
        ctx->audio_ctx->sample_fmt,
        ctx->audio_ctx->sample_rate,
        0, NULL);
    swr_init(ctx->swr_ctx);

    ctx->audio_stream_idx = stream_idx;
    return 1;
}

/* ------------------------------------------------------------------ */
/*  Abertura/troca/desativação de faixa de legenda                     */
/* ------------------------------------------------------------------ */
static int open_subtitle_stream(PlayerContext *ctx, int stream_idx) {
    if (ctx->subtitle_ctx) avcodec_free_context(&ctx->subtitle_ctx);
    ctx->subtitle_stream_idx = -1;

    if (stream_idx < 0) return 1; /* apenas desativa, sem erro */

    AVCodecParameters *par = ctx->fmt_ctx->streams[stream_idx]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(par->codec_id);
    if (!codec) return 0;

    ctx->subtitle_ctx = avcodec_alloc_context3(codec);
    avcodec_parameters_to_context(ctx->subtitle_ctx, par);

    if (avcodec_open2(ctx->subtitle_ctx, codec, NULL) < 0) {
        avcodec_free_context(&ctx->subtitle_ctx);
        return 0;
    }

    ctx->subtitle_stream_idx = stream_idx;
    return 1;
}

/* ------------------------------------------------------------------ */
/*  Decode: trata áudio puro, vídeo, áudio e legendas embutidas        */
/* ------------------------------------------------------------------ */
static void decode_until_queues_have_data(PlayerContext *ctx) {
    if (ctx->eof) return;

    int has_video_stream = (ctx->video_stream_idx >= 0 && ctx->video_ctx != NULL);
    int has_audio_stream = (ctx->audio_stream_idx >= 0 && ctx->audio_ctx != NULL);

    /* ── CORREÇÃO DA CONDIÇÃO DE PREENCHIMENTO DE FILA ── */
    /* Continuamos lendo pacotes se: */
    /* 1. O arquivo tiver vídeo e a fila de vídeo estiver vazia, OU */
    /* 2. O arquivo tiver áudio e a fila de áudio estiver vazia. */
    /* MAS interrompemos se QUALQUER uma das filas ativas atingir o limite (QUEUE_SIZE - 2) para não estourar. */
    while (1) {
        int video_needs_data = has_video_stream && (ctx->video_queue->count == 0);
        int audio_needs_data = has_audio_stream && (ctx->audio_queue->count == 0);

        // Se nenhuma fila ativa precisa de dados urgentemente, saímos do laço para o Java processar
        if (!video_needs_data && !audio_needs_data) {
            // Se for áudio puro, vamos aproveitar a viagem e encher a fila até pelo menos metade
            if (!has_video_stream && ctx->audio_queue->count < (QUEUE_SIZE / 2)) {
                // Continua no laço para encher o buffer
            } else {
                break;
            }
        }

        // Proteção para não estourar a capacidade máxima da fila de blocos alocados
        if (ctx->video_queue->count >= (QUEUE_SIZE - 1) || ctx->audio_queue->count >= (QUEUE_SIZE - 1)) {
            break;
        }
      if (ctx->subtitle_queue->count >= SUB_QUEUE_CAP - 1) {
          break;
      }
        if (av_read_frame(ctx->fmt_ctx, ctx->packet) < 0) {
            ctx->eof = 1;
            break;
        }

        /* ── Processamento de pacote de vídeo ── */
        if (has_video_stream && ctx->packet->stream_index == ctx->video_stream_idx) {
            if (avcodec_send_packet(ctx->video_ctx, ctx->packet) >= 0) {
                while (avcodec_receive_frame(ctx->video_ctx, ctx->decode_frame) == 0) {
                    sws_scale(ctx->sws_ctx,
                              (const uint8_t * const *)ctx->decode_frame->data,
                              ctx->decode_frame->linesize,
                              0, ctx->video_ctx->height,
                              ctx->rgb_frame->data,
                              ctx->rgb_frame->linesize);

                    uint8_t *copy = malloc(ctx->rgb_buffer_size);
                    memcpy(copy, ctx->rgb_buffer, ctx->rgb_buffer_size);

                    if (!queue_push(ctx->video_queue, copy, ctx->rgb_buffer_size)) {
                        free(copy);
                    }
                    av_frame_unref(ctx->decode_frame);
                }
            }
        }
        /* ── Processamento de pacote de áudio ── */
        else if (has_audio_stream && ctx->packet->stream_index == ctx->audio_stream_idx && ctx->swr_ctx) {
            if (avcodec_send_packet(ctx->audio_ctx, ctx->packet) >= 0) {
                while (avcodec_receive_frame(ctx->audio_ctx, ctx->decode_frame) == 0) {
                    int out_samples = swr_get_out_samples(ctx->swr_ctx, ctx->decode_frame->nb_samples);
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
                            free(pcm);
                        }
                    } else {
                        free(pcm);
                    }
                    av_frame_unref(ctx->decode_frame);
                }
            }
        }
    else if (ctx->subtitle_stream_idx >= 0 &&
             ctx->packet->stream_index == ctx->subtitle_stream_idx &&
             ctx->subtitle_ctx) {

        AVSubtitle sub;
        int got = 0;
        memset(&sub, 0, sizeof(sub));

        if (avcodec_decode_subtitle2(ctx->subtitle_ctx, &sub, &got, ctx->packet) >= 0 && got) {
            AVStream *sst = ctx->fmt_ctx->streams[ctx->subtitle_stream_idx];
            double pts_sec = (ctx->packet->pts != AV_NOPTS_VALUE)
                ? ctx->packet->pts * av_q2d(sst->time_base) : 0.0;

            double start = pts_sec + (sub.start_display_time / 1000.0);
            double end   = pts_sec + (sub.end_display_time   / 1000.0);

            /* Correção ANTES de qualquer uso/log de 'end' */
            if (end <= start) {
                if (ctx->packet->duration > 0) {
                    end = pts_sec + ctx->packet->duration * av_q2d(sst->time_base);
                } else {
                    end = start + 4.0;
                }
            }

            fprintf(stderr, "[SUB] pts=%.3f pkt_dur=%lld -> start=%.3f end=%.3f (dur=%.3f)\n",
                    pts_sec, (long long)ctx->packet->duration, start, end, end - start);

            for (unsigned r = 0; r < sub.num_rects; r++) {
                AVSubtitleRect *rect = sub.rects[r];
                if (rect->type == SUBTITLE_BITMAP) continue;

                const char *raw = rect->ass ? rect->ass : rect->text;
                if (raw) {
                    char clean[1024];
                    extract_plain_text(raw, rect->ass != NULL, clean, sizeof(clean));
                    if (clean[0]) subqueue_push(ctx->subtitle_queue, clean, start, end);
                }
            }
            avsubtitle_free(&sub);
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

    ctx->subtitle_stream_idx = -1;

    /* Enumerar streams de áudio e legenda (com idioma) */
    for (unsigned i = 0; i < ctx->fmt_ctx->nb_streams; i++) {
        AVStream *st = ctx->fmt_ctx->streams[i];

        if (st->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            ctx->total_audio_streams++;
            if (ctx->audio_track_count < MAX_TRACKS) {
                TrackInfo *t = &ctx->audio_tracks[ctx->audio_track_count++];
                t->stream_idx = i;
                AVDictionaryEntry *lang = av_dict_get(st->metadata, "language", NULL, 0);
                snprintf(t->lang, sizeof(t->lang), "%s", lang ? lang->value : "und");
            }
        } else if (st->codecpar->codec_type == AVMEDIA_TYPE_SUBTITLE) {
            if (ctx->subtitle_track_count < MAX_TRACKS) {
                TrackInfo *t = &ctx->subtitle_tracks[ctx->subtitle_track_count++];
                t->stream_idx = i;
                AVDictionaryEntry *lang = av_dict_get(st->metadata, "language", NULL, 0);
                snprintf(t->lang, sizeof(t->lang), "%s", lang ? lang->value : "und");
            }
        }
    }

    ctx->video_stream_idx = av_find_best_stream(
        ctx->fmt_ctx, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    ctx->audio_stream_idx = av_find_best_stream(
        ctx->fmt_ctx, AVMEDIA_TYPE_AUDIO, -1, -1, NULL, 0);

    /* ── FILTRO DE "CAPA FALSA" ──────────────────────────────────────
     * Alguns arquivos FLAC/WAV/OGG/M4A sem capa embutida ainda expõem
     * um stream do tipo AVMEDIA_TYPE_VIDEO "fantasma" (metadado de
     * imagem vazio/corrompido), com width/height zerados.
     * Se isso acontecer, sws_getContext() adiante quebra com
     * "w and h must be > 0". Aqui descartamos esse stream e tratamos
     * o arquivo como áudio puro. */
    if (ctx->video_stream_idx >= 0) {
        AVCodecParameters *vpar =
            ctx->fmt_ctx->streams[ctx->video_stream_idx]->codecpar;

        if (vpar->width <= 0 || vpar->height <= 0) {
            ctx->video_stream_idx = -1;
        }
    }

    /* ── Decoder de vídeo ── */
    if (ctx->video_stream_idx >= 0) {
        AVCodecParameters *par =
            ctx->fmt_ctx->streams[ctx->video_stream_idx]->codecpar;
        const AVCodec *codec = avcodec_find_decoder(par->codec_id);

        ctx->video_ctx = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(ctx->video_ctx, par);
        ctx->video_ctx->thread_count = 0; /* auto */
        ctx->video_ctx->thread_type  = FF_THREAD_FRAME;

        int opened_ok = (avcodec_open2(ctx->video_ctx, codec, NULL) >= 0);

        if (opened_ok && ctx->video_ctx->width > 0 && ctx->video_ctx->height > 0) {

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
        } else {
            /* Falhou ao abrir ou dimensões inválidas: descarta o "vídeo"
             * e segue o fluxo como áudio puro */
            if (ctx->video_ctx) {
                avcodec_free_context(&ctx->video_ctx);
                ctx->video_ctx = NULL;
            }
            ctx->video_stream_idx = -1;
        }
    }

    /* ── Decoder de áudio (faixa inicial escolhida pelo FFmpeg) ── */
    if (ctx->audio_stream_idx >= 0) {
        open_audio_stream(ctx, ctx->audio_stream_idx);
    }

    /* Legenda começa desativada por padrão (subtitle_stream_idx = -1) */

    ctx->decode_frame = av_frame_alloc();
    ctx->packet       = av_packet_alloc();

    /* Criar filas */
    ctx->video_queue    = queue_create(QUEUE_SIZE);
    ctx->audio_queue    = queue_create(QUEUE_SIZE);
    ctx->subtitle_queue = subqueue_create();
    ctx->eof            = 0;

    (*env)->ReleaseStringUTFChars(env, jpath, path);
    return (jlong)(uintptr_t)ctx;
}

/* ------------------------------------------------------------------ */
/*  grabNextFrames — lê do FFmpeg e enfileira vídeo+áudio+legenda      */
/*  Chamado pelo Java antes de pegar vídeo, áudio ou legenda.          */
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
/*  pollSubtitleFrame — retira uma entrada de legenda da fila          */
/*  Retorna string "start;end;texto" (segundos, ponto decimal) ou null */
/* ------------------------------------------------------------------ */
JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_pollSubtitleFrame(
        JNIEnv *env, jobject obj, jlong ptr) {

    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return NULL;

    char *text; double start, end;
    if (!subqueue_pop(ctx->subtitle_queue, &text, &start, &end)) return NULL;

    char buf[1200];
    snprintf(buf, sizeof(buf), "%.3f;%.3f;%s", start, end, text);
    free(text);

    return (*env)->NewStringUTF(env, buf);
}

/* ------------------------------------------------------------------ */
/*  isEOF                                                               */
/* ------------------------------------------------------------------ */
JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_isEOF(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return JNI_TRUE;

    // Detecta dinamicamente quais mídias estão de fato ativas neste arquivo
    int has_video = (ctx->video_stream_idx >= 0 && ctx->video_ctx != NULL);
    int has_audio = (ctx->audio_stream_idx >= 0 && ctx->audio_ctx != NULL);

    // Só consideramos a fila vazia se a respectiva mídia existir no container
    int video_queue_empty = !has_video || (ctx->video_queue->count == 0);
    int audio_queue_empty = !has_audio || (ctx->audio_queue->count == 0);

    // O arquivo só terminou de VERDADE se o leitor chegou ao fim (eof)
    // E todas as mídias presentes já esvaziaram suas respectivas filas na memória
    if (ctx->eof && video_queue_empty && audio_queue_empty) {
        return JNI_TRUE;
    }

    return JNI_FALSE;
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

    if (ctx->video_ctx)    avcodec_flush_buffers(ctx->video_ctx);
    if (ctx->audio_ctx)    avcodec_flush_buffers(ctx->audio_ctx);
    if (ctx->subtitle_ctx) avcodec_flush_buffers(ctx->subtitle_ctx);

    /* Limpar filas após seek */
    int sz;
    uint8_t *b;
    while ((b = queue_pop(ctx->video_queue, &sz))) free(b);
    while ((b = queue_pop(ctx->audio_queue, &sz))) free(b);

    char *t; double s, e;
    while (subqueue_pop(ctx->subtitle_queue, &t, &s, &e)) free(t);

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

    if (ctx->sws_ctx)      sws_freeContext(ctx->sws_ctx);
    if (ctx->swr_ctx)      swr_free(&ctx->swr_ctx);
    if (ctx->video_ctx)    avcodec_free_context(&ctx->video_ctx);
    if (ctx->audio_ctx)    avcodec_free_context(&ctx->audio_ctx);
    if (ctx->subtitle_ctx) avcodec_free_context(&ctx->subtitle_ctx);
    if (ctx->fmt_ctx)      avformat_close_input(&ctx->fmt_ctx);
    if (ctx->decode_frame) av_frame_free(&ctx->decode_frame);
    if (ctx->rgb_frame)    av_frame_free(&ctx->rgb_frame);
    if (ctx->rgb_buffer)   av_free(ctx->rgb_buffer);
    if (ctx->packet)       av_packet_free(&ctx->packet);

    queue_destroy(ctx->video_queue);
    queue_destroy(ctx->audio_queue);
    subqueue_destroy(ctx->subtitle_queue);
    free(ctx);
}

/* ------------------------------------------------------------------ */
/*  Faixas de áudio                                                     */
/* ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getAudioTrackCount(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx ? ctx->audio_track_count : 0;
}

JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getAudioTrackLanguage(
        JNIEnv *env, jobject obj, jlong ptr, jint trackIndex) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || trackIndex < 0 || trackIndex >= ctx->audio_track_count)
        return (*env)->NewStringUTF(env, "und");
    return (*env)->NewStringUTF(env, ctx->audio_tracks[trackIndex].lang);
}

JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_setAudioTrack(
        JNIEnv *env, jobject obj, jlong ptr, jint trackIndex) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || trackIndex < 0 || trackIndex >= ctx->audio_track_count) return JNI_FALSE;

    int stream_idx = ctx->audio_tracks[trackIndex].stream_idx;
    if (stream_idx == ctx->audio_stream_idx) return JNI_TRUE; /* já é essa faixa */

    /* Descarta áudio pendente da faixa antiga antes de trocar */
    int sz; uint8_t *b;
    while ((b = queue_pop(ctx->audio_queue, &sz))) free(b);

    return open_audio_stream(ctx, stream_idx) ? JNI_TRUE : JNI_FALSE;
}

/* ------------------------------------------------------------------ */
/*  Faixas de legenda                                                   */
/* ------------------------------------------------------------------ */
JNIEXPORT jint JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getSubtitleTrackCount(
        JNIEnv *env, jobject obj, jlong ptr) {
    PlayerContext *ctx = get_ctx(ptr);
    return ctx ? ctx->subtitle_track_count : 0;
}

JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getSubtitleTrackLanguage(
        JNIEnv *env, jobject obj, jlong ptr, jint trackIndex) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx || trackIndex < 0 || trackIndex >= ctx->subtitle_track_count)
        return (*env)->NewStringUTF(env, "und");
    return (*env)->NewStringUTF(env, ctx->subtitle_tracks[trackIndex].lang);
}

/* trackIndex < 0 desativa as legendas */
JNIEXPORT jboolean JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_setSubtitleTrack(
        JNIEnv *env, jobject obj, jlong ptr, jint trackIndex) {
    PlayerContext *ctx = get_ctx(ptr);
    if (!ctx) return JNI_FALSE;

    /* Limpa qualquer legenda pendente da faixa anterior */
    char *t; double s, e;
    while (subqueue_pop(ctx->subtitle_queue, &t, &s, &e)) free(t);

    if (trackIndex < 0) {
        return open_subtitle_stream(ctx, -1) ? JNI_TRUE : JNI_FALSE;
    }
    if (trackIndex >= ctx->subtitle_track_count) return JNI_FALSE;

    int stream_idx = ctx->subtitle_tracks[trackIndex].stream_idx;
    return open_subtitle_stream(ctx, stream_idx) ? JNI_TRUE : JNI_FALSE;
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

JNIEXPORT jstring JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getContainerFormatName
  (JNIEnv *env, jobject obj, jlong contextPtr) {
    PlayerContext *ctx = (PlayerContext*) contextPtr;
    if (!ctx || !ctx->fmt_ctx || !ctx->fmt_ctx->iformat) return (*env)->NewStringUTF(env, "Desconhecido");
    return (*env)->NewStringUTF(env, ctx->fmt_ctx->iformat->name);
}

JNIEXPORT jdouble JNICALL
Java_com_esl_searchforfiles_Video_FFmpegBridge_getDurationInSeconds
  (JNIEnv *env, jobject obj, jlong contextPtr) {
    PlayerContext *ctx = (PlayerContext*) contextPtr;
    if (!ctx || !ctx->fmt_ctx) return 0.0;

    if (ctx->fmt_ctx->duration != AV_NOPTS_VALUE) {
        return (double)ctx->fmt_ctx->duration / AV_TIME_BASE;
    }
    return 0.0;
}
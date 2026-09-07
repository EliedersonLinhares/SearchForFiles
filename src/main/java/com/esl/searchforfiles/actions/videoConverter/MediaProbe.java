package com.esl.searchforfiles.actions.videoConverter;

import com.esl.searchforfiles.Video.FFmpegBridge;

/**
 * Metadados mínimos lidos rapidamente de um arquivo de vídeo/áudio,
 * usados só para estimativa — reaproveita o FFmpegBridge já existente
 * no player, abrindo e fechando o contexto sem decodificar frames.
 */
final class MediaProbe {

    final double durationSeconds;
    final int width;
    final int height;
    final double frameRate;
    final boolean hasVideo;

    private MediaProbe(double durationSeconds, int width, int height, double frameRate, boolean hasVideo) {
        this.durationSeconds = durationSeconds;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.hasVideo = hasVideo;
    }

    static MediaProbe of(java.io.File source) {
        FFmpegBridge bridge = new FFmpegBridge();
        long ctx = bridge.openVideo(source.getAbsolutePath());
        if (ctx == 0) return null;

        try {
            double duration = bridge.getDurationInSeconds(ctx);
            int width = bridge.getWidth(ctx);
            int height = bridge.getHeight(ctx);
            double fps = bridge.getFrameRate(ctx);
            boolean hasVideo = width > 0 && height > 0;
            return new MediaProbe(duration, width, height, fps, hasVideo);
        } finally {
            bridge.closeVideo(ctx);
        }
    }
}
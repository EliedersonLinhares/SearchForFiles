package com.esl.searchforfiles.actions.videoConverter;

import java.util.Map;

/**
 * Opções de conversão escolhidas pelo usuário, repassadas para o FFmpegConverter (JNI).
 */
public record ConversionProfile(
        String containerExtension,   // "mp4", "mkv", "webm", "avi"
        String videoCodec,           // "copy", "libx264", "libx265", "libvpx-vp9", "mpeg4"
        String audioCodec,           // "copy", "aac", "libmp3lame", "libopus"
        int outWidth,                // 0 = manter original
        int outHeight,               // 0 = manter original
        long videoBitrate,           // bits/s, 0 = usar CRF
        long audioBitrate,           // bits/s, 0 = padrão do encoder
        int crf,                     // 0-51 (x264/x265), -1 = não usar
        String preset,                // "ultrafast".."veryslow"
        int maxThreads
       ) {

    public boolean isCopyVideo() {
        return "copy".equalsIgnoreCase(videoCodec);
    }

    public boolean isCopyAudio() {
        return "copy".equalsIgnoreCase(audioCodec);
    }

    // ------------------------------------------------------------------
    // Presets combinando container + codecs (mostrados na UI)
    // ------------------------------------------------------------------
    public enum FormatPreset {
        MP4_H264("MP4 (H.264 / AAC)", "mp4", "libx264", "aac"),
        MP4_H265("MP4 (H.265 / AAC)", "mp4", "libx265", "aac"),
        MKV_H264("MKV (H.264 / AAC)", "mkv", "libx264", "aac"),
        WEBM_VP9("WebM (VP9 / Opus)", "webm", "libvpx-vp9", "libopus"),
        AVI_MPEG4("AVI (MPEG-4 / MP3)", "avi", "mpeg4", "libmp3lame"),
        COPY_REMUX("Copiar sem recodificar (trocar container)", "mp4", "copy", "copy");

        public final String label;
        public final String extension;
        public final String videoCodec;
        public final String audioCodec;

        FormatPreset(String label, String extension, String videoCodec, String audioCodec) {
            this.label = label;
            this.extension = extension;
            this.videoCodec = videoCodec;
            this.audioCodec = audioCodec;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ------------------------------------------------------------------
    // Resolução de saída
    // ------------------------------------------------------------------
    public enum ResolutionOption {
        ORIGINAL("Original", 0, 0),
        R_2160P("2160p (4K)", 3840, 2160),
        R_1080P("1080p", 1920, 1080),
        R_720P("720p", 1280, 720),
        R_480P("480p", 854, 480);

        public final String label;
        public final int width;
        public final int height;

        ResolutionOption(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ------------------------------------------------------------------
    // Qualidade (mapeada para CRF — quanto menor, melhor a qualidade)
    // ------------------------------------------------------------------
    public enum QualityOption {
        HIGH("Alta qualidade", 18, "slow"),
        NORMAL("Normal", 23, "medium"),
        SMALL("Arquivo pequeno", 28, "fast"),
        VERY_SMALL("Arquivo muito pequeno", 32, "faster");

        public final String label;
        public final int crf;
        public final String preset;

        QualityOption(String label, int crf, String preset) {
            this.label = label;
            this.crf = crf;
            this.preset = preset;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum CpuLimitOption {
        LOW("Baixo (~25% da CPU)", 0.25, "ultrafast"),
        MEDIUM("Médio (~50% da CPU)", 0.50, "fast"),
        HIGH("Alto (~75% da CPU)", 0.75, "medium"),
        MAX("Máximo (sem limite)", 1.0, null); // usa o preset da qualidade escolhida

        public final String label;
        public final double fraction;
        public final String forcedPreset; // null = respeita o preset do QualityOption

        CpuLimitOption(String label, double fraction, String forcedPreset) {
            this.label = label;
            this.fraction = fraction;
            this.forcedPreset = forcedPreset;
        }

        public int toThreadCount() {
            if (this == MAX) return 0;
            int cores = Runtime.getRuntime().availableProcessors();
            return Math.max(1, (int) Math.round(cores * fraction));
        }

        @Override
        public String toString() { return label; }
    }

    public static ConversionProfile from(FormatPreset format, ResolutionOption resolution,
                                         QualityOption quality, CpuLimitOption cpuLimit) {
        boolean copy = format.videoCodec.equals("copy");
        String effectivePreset = cpuLimit.forcedPreset != null ? cpuLimit.forcedPreset : quality.preset;

        return new ConversionProfile(
                format.extension,
                format.videoCodec,
                format.audioCodec,
                resolution.width,
                resolution.height,
                0L,
                0L,
                copy ? -1 : quality.crf,
                copy ? "" : effectivePreset,
                copy ? 0 : cpuLimit.toThreadCount()
        );
    }

    // ------------------------------------------------------------------
    // Heurística de estimativa de tamanho (bits por pixel por nível de qualidade)
    // ------------------------------------------------------------------

    /**
     * Bits por pixel aproximados por nível de qualidade, calibrados para H.264
     * com movimento moderado. É uma estimativa grosseira — o tamanho real do
     * CRF depende muito do conteúdo (cenas estáticas comprimem muito mais que
     * ação rápida).
     */
    private static double bitsPerPixelFor(QualityOption quality) {
        return switch (quality) {
            case HIGH -> 0.12;
            case NORMAL -> 0.08;
            case SMALL -> 0.05;
            case VERY_SMALL -> 0.03;
        };
    }

    /**
     * Fator de eficiência do codec em relação ao H.264 (referência = 1.0).
     * Codecs mais modernos entregam qualidade similar com menos bits.
     */
    private static double codecEfficiencyFactor(String videoCodec) {
        return switch (videoCodec) {
            case "libx265" -> 0.60;      // ~40% menor que H.264 na mesma qualidade
            case "libvpx-vp9" -> 0.65;   // ~35% menor
            case "mpeg4" -> 1.40;        // codec antigo, menos eficiente
            default -> 1.0;              // libx264 e outros
        };
    }

    /** Bitrate de áudio padrão assumido por codec (bits/s), quando não fixado manualmente. */
    private static long defaultAudioBitrateFor(String audioCodec) {
        return switch (audioCodec) {
            case "libmp3lame" -> 192_000L;
            case "libopus" -> 128_000L;
            case "aac" -> 128_000L;
            default -> 160_000L; // fallback, inclui "copy" (aproximação do áudio original)
        };
    }

    /**
     * Estima o bitrate de vídeo (bits/s) para a resolução efetiva de saída.
     * Se o usuário fixou um bitrate manual, esse valor é respeitado diretamente.
     */
    public long estimateVideoBitrate(QualityOption quality, int effectiveWidth, int effectiveHeight, double fps) {
        if (videoBitrate > 0) {
            return videoBitrate;
        }
        double bpp = bitsPerPixelFor(quality) * codecEfficiencyFactor(videoCodec);
        double safeFps = fps > 0 ? fps : 30.0;
        return (long) (effectiveWidth * effectiveHeight * safeFps * bpp);
    }

    public long estimateAudioBitrate() {
        return audioBitrate > 0 ? audioBitrate : defaultAudioBitrateFor(audioCodec);
    }
}
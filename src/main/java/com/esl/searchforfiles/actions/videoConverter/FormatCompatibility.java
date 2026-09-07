package com.esl.searchforfiles.actions.videoConverter;

import com.esl.searchforfiles.Video.FFmpegBridge;
import com.esl.searchforfiles.model.FileInfo;

import java.util.*;

/**
 * Validação preventiva de combinações container/codec/resolução/canais,
 * rodada em Java ANTES de chamar o nativo, para dar mensagens claras ao
 * usuário em vez de deixar o erro estourar nos logs do FFmpeg.
 */
public final class FormatCompatibility {

    private FormatCompatibility() {}

    // ------------------------------------------------------------------
    // Resultado da validação
    // ------------------------------------------------------------------

    public record ValidationResult(List<String> errors, List<String> warnings) {

        public static ValidationResult ok() {
            return new ValidationResult(List.of(), List.of());
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public ValidationResult merge(ValidationResult other) {
            List<String> e = new ArrayList<>(errors);
            List<String> w = new ArrayList<>(warnings);
            e.addAll(other.errors);
            w.addAll(other.warnings);
            return new ValidationResult(e, w);
        }
    }

    // ------------------------------------------------------------------
    // Matriz: container -> codecs de vídeo permitidos
    // ------------------------------------------------------------------

    private static final Map<String, Set<String>> CONTAINER_VIDEO_CODECS = Map.of(
            "mp4",  Set.of("copy", "libx264", "libx265", "mpeg4"),
            "mkv",  Set.of("copy", "libx264", "libx265", "libvpx-vp9", "mpeg4"),
            "webm", Set.of("copy", "libvpx-vp9", "libvpx-vp8"),
            "avi",  Set.of("copy", "mpeg4", "libx264")
    );

    // ------------------------------------------------------------------
    // Matriz: container -> codecs de áudio permitidos
    // ------------------------------------------------------------------

    private static final Map<String, Set<String>> CONTAINER_AUDIO_CODECS = Map.of(
            "mp4",  Set.of("copy", "aac", "libmp3lame"),
            "mkv",  Set.of("copy", "aac", "libmp3lame", "libopus"),
            "webm", Set.of("copy", "libopus", "libvorbis"),
            "avi",  Set.of("copy", "libmp3lame", "aac")
    );

    // ------------------------------------------------------------------
    // Limite prático de canais por codec de áudio
    // ------------------------------------------------------------------

    private static final Map<String, Integer> AUDIO_CODEC_MAX_CHANNELS = Map.of(
            "libmp3lame", 2,   // LAME so suporta mono/estereo
            "aac",        8,   // 7.1
            "libopus",    8,
            "libvorbis",  8
    );

    // ------------------------------------------------------------------
    // Resolução máxima recomendada por codec (compatibilidade com players
    // antigos, não é limite técnico rígido do FFmpeg)
    // ------------------------------------------------------------------

    private static final Map<String, Integer> VIDEO_CODEC_RECOMMENDED_MAX_HEIGHT = Map.of(
            "mpeg4", 1080  // MPEG-4 part 2 em resoluções muito altas tem suporte
            // inconsistente em players/decoders antigos (ex: alguns
            // players de TV/DVD players que leem AVI/MPEG-4)
    );

    // ------------------------------------------------------------------
    // Validação de um único arquivo (chamada pelo VideoConversionService)
    // ------------------------------------------------------------------

    /**
     * @param sourceChannels canais de áudio detectados na origem (via MediaProbe/FFmpegBridge),
     *                       -1 se não foi possível detectar (ex: arquivo sem áudio ou probe falhou)
     */
    public static ValidationResult validate(ConversionProfile profile, int sourceChannels) {

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String container = profile.containerExtension().toLowerCase();

        // ── Container x codec de vídeo ──
        Set<String> allowedVideo = CONTAINER_VIDEO_CODECS.get(container);
        if (allowedVideo != null && !allowedVideo.contains(profile.videoCodec())) {
            errors.add(String.format(
                    "O codec de vídeo '%s' não é compatível com o container '.%s'.",
                    profile.videoCodec(), container));
        }

        // ── Container x codec de áudio ──
        Set<String> allowedAudio = CONTAINER_AUDIO_CODECS.get(container);
        if (allowedAudio != null && !allowedAudio.contains(profile.audioCodec())) {
            errors.add(String.format(
                    "O codec de áudio '%s' não é compatível com o container '.%s'.",
                    profile.audioCodec(), container));
        }

        // ── Canais de áudio ──
        if (sourceChannels > 0 && !profile.isCopyAudio()) {
            Integer maxChannels = AUDIO_CODEC_MAX_CHANNELS.get(profile.audioCodec());
            if (maxChannels != null && sourceChannels > maxChannels) {
                warnings.add(String.format(
                        "O áudio original tem %d canais, mas '%s' suporta no máximo %d. " +
                                "O áudio será convertido (downmix) automaticamente para %s.",
                        sourceChannels, profile.audioCodec(), maxChannels,
                        maxChannels == 2 ? "estéreo" : maxChannels + " canais"));
            }
        }

        // ── Resolução recomendada ──
        if (!profile.isCopyVideo()) {
            Integer recommendedMaxHeight = VIDEO_CODEC_RECOMMENDED_MAX_HEIGHT.get(profile.videoCodec());
            int effectiveHeight = profile.outHeight() > 0 ? profile.outHeight() : Integer.MAX_VALUE;
            // Nota: se outHeight for 0 (mantém original), essa checagem específica
            // não detecta a altura real aqui — o chamador deve passar a altura
            // efetiva já resolvida quando quiser essa validação completa (ver
            // validateWithProbe abaixo).
            if (recommendedMaxHeight != null && effectiveHeight > recommendedMaxHeight) {
                warnings.add(String.format(
                        "Resolução acima de %dp com o codec '%s' pode não reproduzir " +
                                "corretamente em players/dispositivos mais antigos.",
                        recommendedMaxHeight, profile.videoCodec()));
            }
        }

        return new ValidationResult(errors, warnings);
    }

    /**
     * Versão completa: usa a altura efetiva real (considerando resolução
     * "Original" do arquivo de origem) para a checagem de resolução recomendada.
     */
    public static ValidationResult validateWithProbe(ConversionProfile profile,
                                                     int sourceChannels,
                                                     int sourceHeight) {
        ValidationResult base = validate(profile, sourceChannels);

        if (profile.isCopyVideo() || sourceHeight <= 0) {
            return base;
        }

        Integer recommendedMaxHeight = VIDEO_CODEC_RECOMMENDED_MAX_HEIGHT.get(profile.videoCodec());
        int effectiveHeight = profile.outHeight() > 0 ? profile.outHeight() : sourceHeight;

        if (recommendedMaxHeight != null && effectiveHeight > recommendedMaxHeight) {
            List<String> warnings = new ArrayList<>(base.warnings());
            warnings.add(String.format(
                    "Resolução de %dp acima de %dp com o codec '%s' pode não reproduzir " +
                            "corretamente em players/dispositivos mais antigos.",
                    effectiveHeight, recommendedMaxHeight, profile.videoCodec()));
            return new ValidationResult(base.errors(), warnings);
        }

        return base;
    }

    // ------------------------------------------------------------------
    // Validação em lote (todos os arquivos selecionados)
    // ------------------------------------------------------------------

    public record BatchValidationResult(
            Map<String, ValidationResult> perFile,   // caminho do arquivo -> resultado
            boolean hasAnyError,
            boolean hasAnyWarning) {}

    public static BatchValidationResult validateBatch(List<FileInfo> items, ConversionProfile profile) {
        Map<String, ValidationResult> perFile = new LinkedHashMap<>();
        boolean anyError = false;
        boolean anyWarning = false;

        for (FileInfo info : items) {
            if (info == null) continue;
            java.io.File source = new java.io.File(info.getPath());
            if (!source.exists() || !source.isFile()) continue;

            MediaProbe probeInfo = MediaProbe.of(source);

            int channels = -1;
            int height = -1;
            if (probeInfo != null) {
                height = probeInfo.height;
                // MediaProbe atual não expõe canais de áudio; ver extensão abaixo
            }
            channels = probeAudioChannels(source);

            ValidationResult result = validateWithProbe(profile, channels, height);
            perFile.put(source.getAbsolutePath(), result);

            if (!result.isValid()) anyError = true;
            if (result.hasWarnings()) anyWarning = true;
        }

        return new BatchValidationResult(perFile, anyError, anyWarning);
    }

    private static int probeAudioChannels(java.io.File source) {
        FFmpegBridge bridge = new FFmpegBridge();
        long ctx = bridge.openVideo(source.getAbsolutePath());
        if (ctx == 0) return -1;
        try {
            return bridge.getAudioChannels(ctx);
        } finally {
            bridge.closeVideo(ctx);
        }
    }
}
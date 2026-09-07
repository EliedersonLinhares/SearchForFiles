package com.esl.searchforfiles.actions.videoConverter;

import com.esl.searchforfiles.Video.FFmpegConverter;
import com.esl.searchforfiles.actions.compressFile.ByteFormatter;
import com.esl.searchforfiles.model.FileInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de conversão de vídeo/áudio em lote, usando FFmpegConverter (JNI/FFmpeg).
 * Segue o mesmo padrão do ZipCompressionService: recebe List<FileInfo>, processa
 * sequencialmente, publica progresso via callback e suporta cancelamento.
 */
public class VideoConversionService {

    private static final long PROGRESS_INTERVAL = 100; // ms

    private volatile boolean cancelRequested;
    private volatile long currentCtx;
    private volatile FFmpegConverter currentConverter;

    public VideoConversionService() {}

    public void cancel() {
        cancelRequested = true;
        // Não há como "abortar" o nativo no meio de um processNextPacket em execução;
        // o loop verifica a flag a cada pacote e interrompe assim que possível.
    }

    public interface ProgressCallback {
        void onProgress(ConversionProgress progress);
    }

    /**
     * Ponto de entrada único: converte cada item da lista, gerando um arquivo de
     * saída por item na pasta de destino informada.
     */
    public BatchConversionResult convert(
            List<FileInfo> items,
            File destinationDir,
            ConversionProfile profile,
            ProgressCallback callback) {

        long start = System.currentTimeMillis();
        cancelRequested = false;

        List<FileConversionResult> results = new ArrayList<>();
        List<FileInfo> valid = filterValidItems(items);

        if (valid.isEmpty()) {
            return new BatchConversionResult(results, elapsed(start));
        }

        int totalFiles = valid.size();
        int processedFiles = 0;

        for (FileInfo info : valid) {

            File source = new File(info.getPath());

            if (cancelRequested) {
                results.add(FileConversionResult.cancelled(source));
                continue;
            }

            File output = buildOutputFile(source, destinationDir, profile);

            publishFileProgress(callback, source.getName(), "Preparando...",
                    0, processedFiles, totalFiles, start);

            FileConversionResult result = convertSingle(source, output, profile, callback,
                    processedFiles, totalFiles, start);

            results.add(result);
            processedFiles++;
        }

        return new BatchConversionResult(results, elapsed(start));
    }

    // ------------------------------------------------------------------
    // Conversão de um único arquivo
    // ------------------------------------------------------------------

    private FileConversionResult convertSingle(
            File source, File output, ConversionProfile profile,
            ProgressCallback callback, int processedFiles, int totalFiles, long start) {

        if (!source.exists() || !source.isFile()) {
            return FileConversionResult.error(source, output, "Arquivo de origem não encontrado.");
        }
        if (sameFile(source, output)) {
            return FileConversionResult.error(source, output,
                    "O arquivo de saída não pode ser igual ao de origem.");
        }
        if (output.getParentFile() != null && !output.getParentFile().exists()) {
            if (!output.getParentFile().mkdirs()) {
                return FileConversionResult.error(source, output,
                        "Não foi possível criar a pasta de destino.");
            }
        }

        FFmpegConverter converter = new FFmpegConverter();
        long ctx = 0;

        try {
            ctx = converter.openConverter(
                    source.getAbsolutePath(), output.getAbsolutePath(),
                    profile.videoCodec(), profile.audioCodec(),
                    profile.outWidth(), profile.outHeight(),
                    profile.videoBitrate(), profile.audioBitrate(),
                    profile.crf(), profile.preset(),profile.maxThreads());

            currentCtx = ctx;
            currentConverter = converter;

            if (converter.hasError(ctx)) {
                String msg = converter.getErrorMessage(ctx);
                converter.closeConverter(ctx);
                deleteIncomplete(output);
                return FileConversionResult.error(source, output, msg);
            }

            long lastPublish = 0;

            /*
             * processNextPacket processa UM pacote por chamada (mesmo espírito
             * do ProgressMonitor do Zip4j) — assim conseguimos verificar
             * cancelamento e publicar progresso periodicamente.
             */
            while (converter.processNextPacket(ctx)) {

                if (cancelRequested) {
                    break;
                }

                long now = System.currentTimeMillis();
                if (now - lastPublish >= PROGRESS_INTERVAL) {
                    double filePct = converter.getProgress(ctx);
                    publishFileProgress(callback, source.getName(), "Convertendo",
                            filePct, processedFiles, totalFiles, start);
                    lastPublish = now;
                }
            }

            if (cancelRequested) {
                converter.closeConverter(ctx);
                deleteIncomplete(output);
                return FileConversionResult.cancelled(source);
            }

            if (converter.hasError(ctx)) {
                String msg = converter.getErrorMessage(ctx);
                converter.closeConverter(ctx);
                deleteIncomplete(output);
                return FileConversionResult.error(source, output, msg);
            }

            converter.closeConverter(ctx);

            if (!output.exists() || output.length() <= 0) {
                return FileConversionResult.error(source, output,
                        "O arquivo de saída não foi criado corretamente.");
            }

            publishFileProgress(callback, source.getName(), "Concluído",
                    1.0, processedFiles + 1, totalFiles, start);

            return FileConversionResult.success(source, output);

        } catch (Throwable t) {
            try { if (ctx != 0) converter.closeConverter(ctx); } catch (Exception ignored) {}
            deleteIncomplete(output);
            return FileConversionResult.error(source, output,
                    t.getMessage() != null ? t.getMessage() : "Erro desconhecido durante a conversão.");
        } finally {
            currentCtx = 0;
            currentConverter = null;
        }
    }

    // ------------------------------------------------------------------
    // Suporte comum
    // ------------------------------------------------------------------

    private List<FileInfo> filterValidItems(List<FileInfo> items) {
        List<FileInfo> valid = new ArrayList<>();
        if (items == null) return valid;

        for (FileInfo info : items) {
            if (info == null) continue;
            File f = new File(info.getPath());
            if (f.exists() && f.isFile()) {
                valid.add(info);
            }
        }
        return valid;
    }

    private File buildOutputFile(File source, File destinationDir, ConversionProfile profile) {
        String baseName = stripExtension(source.getName());
        String ext = profile.containerExtension();
        File dir = destinationDir != null ? destinationDir : source.getParentFile();

        File candidate = new File(dir, baseName + "." + ext);
        int counter = 1;
        /* Evita sobrescrever arquivos existentes automaticamente */
        while (candidate.exists()) {
            candidate = new File(dir, baseName + " (" + counter + ")." + ext);
            counter++;
        }
        return candidate;
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void publishFileProgress(ProgressCallback callback, String fileName, String task,
                                     double filePercent, int processedFiles, int totalFiles, long start) {
        if (callback == null) return;

        double overall = totalFiles > 0
                ? (processedFiles + clamp01(filePercent)) / (double) totalFiles
                : 0;

        callback.onProgress(new ConversionProgress(
                (int) Math.round(clamp01(overall) * 100),
                (int) Math.round(clamp01(filePercent) * 100),
                fileName, task, processedFiles, totalFiles, elapsed(start)));
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private boolean sameFile(File a, File b) {
        try {
            return a.toPath().toRealPath().equals(b.toPath().toRealPath());
        } catch (IOException e) {
            return a.getAbsoluteFile().equals(b.getAbsoluteFile());
        }
    }

    private void deleteIncomplete(File output) {
        if (output == null) return;
        try {
            Files.deleteIfExists(output.toPath());
        } catch (IOException e) {
            System.err.println("Não foi possível remover arquivo incompleto: " + output);
        }
    }

    private long elapsed(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }


    // ------------------------------------------------------------------
    // Estimativa de tamanho final
    // ------------------------------------------------------------------

    public record EstimateResult(long originalBytes, long estimatedBytes, int fileCount,
                                 int probedFiles, int failedProbes) {

        public long estimatedSavedBytes() {
            return Math.max(0, originalBytes - estimatedBytes);
        }

        public double estimatedSavedRatio() {
            return originalBytes > 0 ? (double) estimatedSavedBytes() / originalBytes : 0;
        }

        /** true se algum arquivo não pôde ser analisado (ex: corrompido, formato não suportado). */
        public boolean hasIncompleteData() {
            return failedProbes > 0;
        }
    }

    /**
     * Estima o tamanho total de saída do lote. Para vídeo com CRF, a estimativa
     * é aproximada (o CRF é adaptativo ao conteúdo); para modo "copy" o tamanho
     * tende a ficar próximo do original, já que não há recodificação.
     */
    public EstimateResult estimate(List<FileInfo> items, ConversionProfile profile,
                                   ConversionProfile.QualityOption quality) {

        List<FileInfo> valid = filterValidItems(items);

        long originalBytes = 0;
        long estimatedBytes = 0;
        int probed = 0;
        int failed = 0;

        for (FileInfo info : valid) {
            File source = new File(info.getPath());
            long sourceSize = source.length();
            originalBytes += sourceSize;

            MediaProbe probeInfo = MediaProbe.of(source);

            if (probeInfo == null || probeInfo.durationSeconds <= 0) {
                /* Não foi possível ler metadados: assume que o tamanho de saída
                   será igual ao original (estimativa conservadora, evita
                   subestimar o espaço necessário). */
                estimatedBytes += sourceSize;
                failed++;
                continue;
            }
            probed++;

            long videoBitrate = 0;
            if (!profile.isCopyVideo() && probeInfo.hasVideo) {
                int effW = profile.outWidth() > 0 ? profile.outWidth() : probeInfo.width;
                int effH = profile.outHeight() > 0 ? profile.outHeight() : probeInfo.height;
                videoBitrate = profile.estimateVideoBitrate(quality, effW, effH, probeInfo.frameRate);
            }

            long audioBitrate = profile.isCopyAudio() ? profile.estimateAudioBitrate() : profile.estimateAudioBitrate();

            if (profile.isCopyVideo() && profile.isCopyAudio()) {
                /* Remux puro: tamanho fica muito próximo do original
                   (só muda overhead de container). */
                estimatedBytes += sourceSize;
                continue;
            }

            long totalBitrate = videoBitrate + audioBitrate;
            long estimatedFileBytes = (long) (totalBitrate * probeInfo.durationSeconds / 8.0);

            /* Se só o áudio for recodificado (arquivo de áudio puro, sem vídeo),
               não faz sentido "herdar" bitrate de vídeo zerado incorretamente. */
            if (!probeInfo.hasVideo) {
                estimatedFileBytes = (long) (audioBitrate * probeInfo.durationSeconds / 8.0);
            }

            estimatedBytes += Math.max(estimatedFileBytes, 0);
        }

        return new EstimateResult(originalBytes, estimatedBytes, valid.size(), probed, failed);
    }

    // ------------------------------------------------------------------
    // Verificação de espaço em disco (mesmo padrão do ZipCompressionService)
    // ------------------------------------------------------------------

    public void checkDiskSpace(File destinationDir, long estimatedBytes) throws IOException {
        if (destinationDir == null) {
            return;
        }
        long usable = destinationDir.getUsableSpace();
        long required = (long) (estimatedBytes * 1.15); // margem de 15% (estimativa de vídeo é menos precisa que ZIP)

        if (usable > 0 && usable < required) {
            throw new IOException(String.format(
                    "Espaço em disco insuficiente em %s.%nNecessário: aprox. %s%nDisponível: %s",
                    destinationDir.getAbsolutePath(),
                    ByteFormatter.format(required),
                    ByteFormatter.format(usable)));
        }
    }
}

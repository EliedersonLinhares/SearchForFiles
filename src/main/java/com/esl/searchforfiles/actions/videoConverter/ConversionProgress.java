package com.esl.searchforfiles.actions.videoConverter;

/**
 * Progresso agregado (arquivo atual + lote inteiro), publicado durante a conversão.
 */
public final class ConversionProgress {

    private final int overallPercent;   // 0-100, considera todos os arquivos do lote
    private final int filePercent;      // 0-100, apenas o arquivo em processamento
    private final String currentFile;
    private final String currentTask;   // "Preparando...", "Convertendo", "Concluído"
    private final int processedFiles;
    private final int totalFiles;
    private final long elapsedMillis;

    public ConversionProgress(int overallPercent, int filePercent, String currentFile, String currentTask,
                              int processedFiles, int totalFiles, long elapsedMillis) {
        this.overallPercent = clamp(overallPercent);
        this.filePercent = clamp(filePercent);
        this.currentFile = currentFile == null ? "" : currentFile;
        this.currentTask = currentTask == null ? "" : currentTask;
        this.processedFiles = Math.max(0, processedFiles);
        this.totalFiles = Math.max(0, totalFiles);
        this.elapsedMillis = Math.max(0, elapsedMillis);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    public int getOverallPercent() { return overallPercent; }
    public int getFilePercent() { return filePercent; }
    public String getCurrentFile() { return currentFile; }
    public String getCurrentTask() { return currentTask; }
    public int getProcessedFiles() { return processedFiles; }
    public int getTotalFiles() { return totalFiles; }
    public long getElapsedMillis() { return elapsedMillis; }

    /** Estimativa simples: tempo médio por arquivo já concluído × arquivos restantes. */
    public long getEstimatedRemainingMillis() {
        if (processedFiles <= 0 || totalFiles <= 0) return -1;
        double avgPerFile = (double) elapsedMillis / processedFiles;
        int remainingFiles = Math.max(0, totalFiles - processedFiles);
        return (long) (avgPerFile * remainingFiles);
    }
}
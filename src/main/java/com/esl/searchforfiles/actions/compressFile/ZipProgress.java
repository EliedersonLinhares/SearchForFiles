package com.esl.searchforfiles.actions.compressFile;

public final class ZipProgress {

    private final int percent;

    private final String currentFile;
    private final String currentTask;

    private final long processedBytes;
    private final long totalBytes;

    private final long processedFiles;
    private final long totalFiles;

    private final long elapsedMillis;
    private final long bytesPerSecond;

    private final long estimatedRemainingMillis;

    public ZipProgress(int percent, String currentFile, String currentTask, long processedBytes, long totalBytes,
            long processedFiles, long totalFiles, long elapsedMillis, long bytesPerSecond, long estimatedRemainingMillis) {

        this.percent = Math.max(0, Math.min(100, percent));

        this.currentFile = currentFile == null ? "" : currentFile;
        this.currentTask = currentTask == null ? "" : currentTask;
        this.processedBytes = Math.max(0, processedBytes);
        this.totalBytes = Math.max(0, totalBytes);
        this.processedFiles = Math.max(0, processedFiles);
        this.totalFiles = Math.max(0, totalFiles);
        this.elapsedMillis = Math.max(0, elapsedMillis);
        this.bytesPerSecond = Math.max(0, bytesPerSecond);

        this.estimatedRemainingMillis = estimatedRemainingMillis;
    }

    public int getPercent() {
        return percent;
    }

    public String getCurrentFile() {
        return currentFile;
    }

    public String getCurrentTask() {
        return currentTask;
    }

    public long getProcessedBytes() {
        return processedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getProcessedFiles() {
        return processedFiles;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public long getBytesPerSecond() {
        return bytesPerSecond;
    }

    public long getEstimatedRemainingMillis() {
        return estimatedRemainingMillis;
    }

    public long getRemainingBytes() {
        return Math.max(
                0,
                totalBytes - processedBytes
        );
    }
}
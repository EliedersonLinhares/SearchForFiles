package com.esl.searchforfiles.actions.compressFile;

import java.io.File;

public final class ZipResult {

    public enum Status {
        SUCCESS,
        CANCELLED,
        ERROR
    }

    private final Status status;
    private final File zipFile;
    private final long totalBytes;
    private final long processedBytes;

    private final long totalFiles;
    private final long processedFiles;

    private final long elapsedMillis;
    private final Throwable error;

    private ZipResult(Status status, File zipFile, long totalBytes, long processedBytes, long totalFiles,
            long processedFiles, long elapsedMillis, Throwable error) {

        this.status = status;
        this.zipFile = zipFile;
        this.totalBytes = totalBytes;
        this.processedBytes = processedBytes;
        this.totalFiles = totalFiles;
        this.processedFiles = processedFiles;
        this.elapsedMillis = elapsedMillis;
        this.error = error;
    }

    public static ZipResult success(File zipFile, long totalBytes, long processedBytes, long totalFiles,
            long processedFiles, long elapsedMillis) {

        return new ZipResult(Status.SUCCESS, zipFile, totalBytes, processedBytes, totalFiles, processedFiles,
                elapsedMillis, null);
    }

    public static ZipResult cancelled(File zipFile, long totalBytes, long processedBytes, long totalFiles,
            long processedFiles, long elapsedMillis) {

        return new ZipResult(Status.CANCELLED, zipFile, totalBytes, processedBytes, totalFiles, processedFiles,
                elapsedMillis, null);
    }

    public static ZipResult error(File zipFile, long totalBytes, long processedBytes, long totalFiles,
                                  long processedFiles, long elapsedMillis, Throwable error) {

        return new ZipResult(Status.ERROR, zipFile, totalBytes, processedBytes, totalFiles, processedFiles,
                elapsedMillis, error
        );
    }

    public Status getStatus() {
        return status;
    }

    public File getZipFile() {
        return zipFile;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public long getProcessedBytes() {
        return processedBytes;
    }

    public long getTotalFiles() {
        return totalFiles;
    }

    public long getProcessedFiles() {
        return processedFiles;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public Throwable getError() {
        return error;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public long getZipSize() {
        if (zipFile == null || !zipFile.exists()) {
            return 0;
        }
        return zipFile.length();
    }

    public long getSavedBytes() {
        if (zipFile == null || !zipFile.exists()) {
            return 0;
        }
        return Math.max(0, totalBytes - zipFile.length()
        );
    }
}
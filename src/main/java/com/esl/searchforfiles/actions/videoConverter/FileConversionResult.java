package com.esl.searchforfiles.actions.videoConverter;

import java.io.File;

/**
 * Resultado da conversão de UM arquivo dentro do lote.
 */
public final class FileConversionResult {

    public enum Status { SUCCESS, ERROR, CANCELLED, SKIPPED }

    private final Status status;
    private final File source;
    private final File output;
    private final String errorMessage;

    private FileConversionResult(Status status, File source, File output, String errorMessage) {
        this.status = status;
        this.source = source;
        this.output = output;
        this.errorMessage = errorMessage;
    }

    public static FileConversionResult success(File source, File output) {
        return new FileConversionResult(Status.SUCCESS, source, output, null);
    }

    public static FileConversionResult error(File source, File output, String message) {
        return new FileConversionResult(Status.ERROR, source, output, message);
    }

    public static FileConversionResult cancelled(File source) {
        return new FileConversionResult(Status.CANCELLED, source, null, null);
    }

    public static FileConversionResult skipped(File source, String reason) {
        return new FileConversionResult(Status.SKIPPED, source, null, reason);
    }

    public Status getStatus() { return status; }
    public File getSource() { return source; }
    public File getOutput() { return output; }
    public String getErrorMessage() { return errorMessage; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
    public boolean isError() { return status == Status.ERROR; }
    public boolean isCancelled() { return status == Status.CANCELLED; }
}
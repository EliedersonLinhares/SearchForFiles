package com.esl.searchforfiles.actions.videoConverter;

import java.util.List;

/**
 * Resultado agregado de um lote de conversões.
 */
public final class BatchConversionResult {

    private final List<FileConversionResult> results;
    private final long elapsedMillis;

    public BatchConversionResult(List<FileConversionResult> results, long elapsedMillis) {
        this.results = List.copyOf(results);
        this.elapsedMillis = elapsedMillis;
    }

    public List<FileConversionResult> getResults() { return results; }
    public long getElapsedMillis() { return elapsedMillis; }

    public long successCount() {
        return results.stream().filter(FileConversionResult::isSuccess).count();
    }

    public long errorCount() {
        return results.stream().filter(FileConversionResult::isError).count();
    }

    public long cancelledCount() {
        return results.stream().filter(FileConversionResult::isCancelled).count();
    }

    public boolean hasErrors() {
        return errorCount() > 0;
    }

    public boolean wasFullyCancelled() {
        return !results.isEmpty() && cancelledCount() == results.size();
    }
}
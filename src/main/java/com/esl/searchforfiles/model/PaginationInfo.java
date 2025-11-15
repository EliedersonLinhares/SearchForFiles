package com.esl.searchforfiles.model;

/**
 * Informações sobre paginação de resultados
 */
public class PaginationInfo {
    private final int currentPage;
    private final int pageSize;
    private final long totalResults;
    private final int totalPages;

    public PaginationInfo(int currentPage, int pageSize, long totalResults) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalResults = totalResults;
        this.totalPages = (int) Math.ceil((double) totalResults / pageSize);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalResults() {
        return totalResults;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean hasNextPage() {
        return currentPage < totalPages;
    }

    public boolean hasPreviousPage() {
        return currentPage > 1;
    }

    public int getOffset() {
        return (currentPage - 1) * pageSize;
    }

    @Override
    public String toString() {
        return String.format("Página %d de %d (%,d resultados)",
                currentPage, totalPages, totalResults);
    }
}
package com.esl.searchforfiles.model;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class IndexStats {
    private long totalFiles = 0;
    private long totalSize = 0;
    private long lastUpdate = 0;
    private Map<FileType, Long> filesByType = new HashMap<>();
    private Map<String, Long> filesByDrive = new HashMap<>();

    public long getTotalFiles() { return totalFiles; }
    public void setTotalFiles(long totalFiles) { this.totalFiles = totalFiles; }

    public long getTotalSize() { return totalSize; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }

    public long getLastUpdate() { return lastUpdate; }
    public void setLastUpdate(long lastUpdate) { this.lastUpdate = lastUpdate; }

    public Map<FileType, Long> getFilesByType() { return filesByType; }
    public void setFilesByType(Map<FileType, Long> filesByType) {
        this.filesByType = filesByType;
    }

    public Map<String, Long> getFilesByDrive() { return filesByDrive; }
    public void setFilesByDrive(Map<String, Long> filesByDrive) {
        this.filesByDrive = filesByDrive;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Estatísticas do Índice ===\n");
        sb.append(String.format("Total de arquivos: %,d\n", totalFiles));
        sb.append(String.format("Tamanho total: %.2f GB\n", totalSize / (1024.0 * 1024.0 * 1024.0)));
        sb.append(String.format("Última atualização: %s\n", new Date(lastUpdate)));

        if (!filesByDrive.isEmpty()) {
            sb.append("\nPor drive:\n");
            filesByDrive.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> sb.append(String.format("  %s: %,d arquivos\n", e.getKey(), e.getValue())));
        }

        sb.append("\nPor tipo:\n");
        filesByType.entrySet().stream()
                .sorted(Map.Entry.<FileType, Long>comparingByValue().reversed())
                .forEach(e -> sb.append(String.format("  %s: %,d\n", e.getKey(), e.getValue())));

        return sb.toString();
    }
}

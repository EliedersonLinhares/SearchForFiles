package com.esl.searchforfiles.service;

import com.esl.searchforfiles.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Serviço de sincronização automática do índice
 * Apenas sincroniza pastas que já foram indexadas
 */
public class SyncService {

    private final DatabaseManager dbManager;
    private final SearchService searchService;
    private final ExecutorService virtualExecutor; // NOVO

    private boolean isFolderNotIndexed;

    public SyncService(DatabaseManager dbManager, SearchService searchService) {
        this.dbManager = dbManager;
        this.searchService = searchService;
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor(); // NOVO
    }



    /**
     * Verifica se pasta foi indexada anteriormente
     * NOVO MÉTODO - Validação crítica
     *
     * @param folderPath Caminho da pasta
     * @return true se pasta está no índice, false caso contrário
     */
//    public boolean isFolderIndexed(String folderPath) {
//        try {
//            String sql = "SELECT COUNT(*) FROM file_index WHERE path LIKE ? LIMIT 1";
//
//            try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
//                pstmt.setString(1, folderPath + "%");
//
//                try (ResultSet rs = pstmt.executeQuery()) {
//                    if (rs.next()) {
//                        int count = rs.getInt(1);
//                        boolean isIndexed = count > 0;
//
//                        if (!isIndexed) {
//                            System.out.println("⚠️ Pasta não indexada: " + folderPath);///////
//                            System.out.println("   Use 'Indexar' antes de buscar nesta pasta");
//                        }
//
//                        return isIndexed;
//                    }
//                }
//            }
//        } catch (SQLException e) {
//            System.err.println("❌ Erro ao verificar pasta: " + e.getMessage());
//        }
//
//        return false;
//    }

    /**
     * Verifica se uma pasta está coberta pelo índice: ou ela mesma foi
     * indexada explicitamente, ou algum ancestral dela foi indexado
     * COM subpastas. Nunca infere isso a partir de file_index diretamente,
     * pois um path pode "casar" textualmente com arquivos de uma pasta
     * filha indexada de forma independente, sem que a própria pasta
     * tenha sido escolhida para indexação.
     */
    public boolean isFolderIndexed(String folderPath) throws SQLException {
        Path normalized = Paths.get(folderPath).toAbsolutePath().normalize();

        // 1. Match exato: esta pasta foi registrada diretamente (com ou sem subpastas)
        String exactSql = "SELECT 1 FROM indexed_folders WHERE path = ? LIMIT 1";
        try (PreparedStatement p = dbManager.getConn().prepareStatement(exactSql)) {
            p.setString(1, normalized.toString());
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return true;
            }
        }

        // 2. Match por ancestral: algum ancestral foi indexado COM subpastas,
        //    e portanto cobre esta pasta legitimamente
        String ancestorSql = "SELECT path FROM indexed_folders WHERE include_subfolders = 1";
        try (Statement stmt = dbManager.getConn().createStatement();
             ResultSet rs = stmt.executeQuery(ancestorSql)) {
            while (rs.next()) {
                Path ancestor = Paths.get(rs.getString("path"));
                if (normalized.startsWith(ancestor)) {
                    return true;
                }
            }
        }

        // Nenhuma raiz indexada cobre esta pasta
        System.out.println("⚠️ Pasta não indexada: " + normalized);
        System.out.println("   Use 'Indexar' antes de buscar nesta pasta");
        return false;
    }

    /**
     * Sincroniza pasta com o índice
     * MODIFICADO: Valida se pasta foi indexada antes
     *
     * @param folderPath Caminho da pasta a sincronizar
     * @return Resultado da sincronização com estatísticas
     */
    public SyncResult synchronizeFolder(String folderPath) throws SQLException {
        System.out.println("\n🔄 Iniciando sincronização: " + folderPath);

        if (!isFolderIndexed(folderPath)) {
            SyncResult result = new SyncResult(folderPath);
            result.setNotIndexed(true);
            return result;
        }

        // NOVO: se esta pasta não é uma raiz própria, redireciona a sincronização
        // para a raiz que efetivamente a governa
        String governingRoot = findGoverningIndexedRoot(folderPath);
        String syncTarget = governingRoot != null ? governingRoot : folderPath;

        if (!syncTarget.equals(folderPath)) {
            System.out.println("ℹ️ '" + folderPath + "' é coberta por raiz indexada '"
                    + syncTarget + "' — sincronizando a raiz.");
        }

        boolean includeSubfolders = dbManager.getEffectiveIncludeSubfoldersPolicy(syncTarget);

        long startTime = System.currentTimeMillis();
        SyncResult result = new SyncResult(folderPath);

        try {
            Map<String, FileSnapshot> filesInSystem = scanFileSystem(folderPath, includeSubfolders);
            Map<String, FileSnapshot> filesInIndex = getIndexedFiles(folderPath, includeSubfolders);

            result = compareAndSync(filesInSystem, filesInIndex, folderPath);

            if (result.hasChanges()) {
                searchService.clearCache();
            }

            result.setElapsedTime(System.currentTimeMillis() - startTime);
            System.out.println(result);

        } catch (Exception e) {
            result.setError(e.getMessage());
        }

        return result;
    }



    /**
     * Encontra a raiz própria (com registro explícito em indexed_folders,
     * include_subfolders=1) que cobre a pasta informada, se houver.
     * Retorna null se a própria pasta já é raiz, ou se não há cobertura.
     */
    private String findGoverningIndexedRoot(String folderPath) throws SQLException {
        String normalized = Paths.get(folderPath).toAbsolutePath().normalize().toString();

        // Se a própria pasta é raiz, não precisa redirecionar
        String exactSql = "SELECT 1 FROM indexed_folders WHERE path = ?";
        try (PreparedStatement p = dbManager.getConnection().prepareStatement(exactSql)) {
            p.setString(1, normalized);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return null;
            }
        }

        Path target = Paths.get(normalized);
        String ancestorSql = "SELECT path FROM indexed_folders WHERE include_subfolders = 1";
        try (Statement stmt = dbManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(ancestorSql)) {
            while (rs.next()) {
                Path ancestor = Paths.get(rs.getString("path"));
                if (target.startsWith(ancestor)) return ancestor.toString();
            }
        }
        return null;
    }


    /**
     * Escaneia sistema de arquivos e cria snapshots
     */
    private Map<String, FileSnapshot> scanFileSystem(String folderPath, boolean includeSubfolders) throws IOException {
        Map<String, FileSnapshot> files = new HashMap<>();
        Path rootPath = Paths.get(folderPath);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return files;
        }

        int maxDepth = includeSubfolders ? Integer.MAX_VALUE : 1;

        Files.walkFileTree(rootPath, EnumSet.noneOf(FileVisitOption.class), maxDepth,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        try {
                            String absolutePath = file.toAbsolutePath().toString();
                            files.put(absolutePath, new FileSnapshot(
                                    absolutePath, attrs.lastModifiedTime().toMillis(),
                                    attrs.size(), attrs.isDirectory()));
                        } catch (Exception ignored) {}
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        // Não conta a própria raiz como "arquivo do disco"
                        if (!dir.equals(rootPath)) {
                            try {
                                String absolutePath = dir.toAbsolutePath().toString();
                                files.put(absolutePath, new FileSnapshot(
                                        absolutePath, attrs.lastModifiedTime().toMillis(), 0, true));
                            } catch (Exception ignored) {}
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });

        return files;
    }

    /**
     * Obtém arquivos indexados no banco de dados
     */
    private Map<String, FileSnapshot> getIndexedFiles(String folderPath, boolean includeSubfolders) throws SQLException {
        Map<String, FileSnapshot> files = new HashMap<>();

        // Se não inclui subpastas, só olha filhos diretos (parent_path = folderPath)
        String sql = includeSubfolders
                ? "SELECT path, last_modified, size, is_directory FROM file_index WHERE path LIKE ?"
                : "SELECT path, last_modified, size, is_directory FROM file_index WHERE parent_path = ?";

        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, includeSubfolders ? folderPath + "%" : folderPath);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    files.put(path, new FileSnapshot(
                            path, rs.getLong("last_modified"),
                            rs.getLong("size"), rs.getBoolean("is_directory")));
                }
            }
        }

        return files;
    }

    /**
     * Compara e sincroniza diferenças
     */
    private SyncResult compareAndSync(Map<String, FileSnapshot> systemFiles,
                                      Map<String, FileSnapshot> indexFiles,
                                      String folderPath) throws Exception {

        SyncResult result = new SyncResult(folderPath);
        AtomicInteger added = new AtomicInteger(0);
        AtomicInteger updated = new AtomicInteger(0);
        AtomicInteger deleted = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<String, FileSnapshot> entry : systemFiles.entrySet()) {
            String path = entry.getKey();
            FileSnapshot systemFile = entry.getValue();
            FileSnapshot indexedFile = indexFiles.get(path);

            if (indexedFile == null) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        indexNewFile(path);
                        added.incrementAndGet();
                        result.addNewFile(path);
                    } catch (Exception e) {
                        System.err.println("⚠️ Erro ao indexar novo arquivo: " + e.getMessage());
                    }
                }, virtualExecutor));

            } else if (systemFile.isModified(indexedFile)) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        reindexFile(path);
                        updated.incrementAndGet();
                        result.addModifiedFile(path);
                    } catch (Exception e) {
                        System.err.println("⚠️ Erro ao reindexar arquivo: " + e.getMessage());
                    }
                }, virtualExecutor));
            }
        }

        for (String indexedPath : indexFiles.keySet()) {
            if (!systemFiles.containsKey(indexedPath)) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        deleteFromIndex(indexedPath);
                        deleted.incrementAndGet();
                        result.addDeletedFile(indexedPath);
                    } catch (Exception e) {
                        System.err.println("⚠️ Erro ao remover do índice: " + e.getMessage());
                    }
                }, virtualExecutor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        result.setAdded(added.get());
        result.setUpdated(updated.get());
        result.setDeleted(deleted.get());

        return result;
    }

    /**
     * Indexa arquivo novo
     */
    private void indexNewFile(String path) throws Exception {
        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            dbManager.indexFile(filePath, attrs);
            System.out.println("  ➕ Novo: " + getFileName(path));
        }
    }

    /**
     * Re-indexa arquivo modificado
     */
    private void reindexFile(String path) throws Exception {
        Path filePath = Paths.get(path);
        if (Files.exists(filePath)) {
            BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
            dbManager.indexFile(filePath, attrs);
            System.out.println("  ✏️ Modificado: " + getFileName(path));
        }
    }

    /**
     * Remove arquivo deletado do índice
     */
    private void deleteFromIndex(String path) throws SQLException {
        dbManager.deleteFile(path);
        System.out.println("  ➖ Deletado: " + getFileName(path));
    }

    /**
     * Extrai nome do arquivo do caminho completo
     */
    private String getFileName(String path) {
        Path p = Paths.get(path);
        Path fileName = p.getFileName();
        return fileName != null ? fileName.toString() : path;
    }

    /**
     * Verifica se pasta precisa de sincronização (rápido)
     * MODIFICADO: Valida se pasta foi indexada
     *
     * @param folderPath Pasta a verificar
     * @return true se precisa sincronizar, false caso contrário
     */
    public boolean needsSync(String folderPath) throws SQLException {
        if (!isFolderIndexed(folderPath)) {
            return false;
        }

        Boolean policy;
        try {
            policy = dbManager.getIncludeSubfoldersPolicy(folderPath);
        } catch (SQLException e) {
            return false;
        }
        boolean includeSubfolders = Boolean.TRUE.equals(policy);

        try {
            Map<String, FileSnapshot> disk = scanFileSystem(folderPath, includeSubfolders);
            Map<String, FileSnapshot> index = getIndexedFiles(folderPath, includeSubfolders);

            int diff = Math.abs(disk.size() - index.size());
            return diff > 0;

        } catch (Exception e) {
            System.err.println("⚠️ Erro ao verificar sincronização: " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // CLASSES AUXILIARES
    // ========================================================================

    /**
     * Snapshot de um arquivo em um momento específico
     */
    private static class FileSnapshot {
        private final String path;
        private final long lastModified;
        private final long size;
        private final boolean isDirectory;

        public FileSnapshot(String path, long lastModified, long size, boolean isDirectory) {
            this.path = path;
            this.lastModified = lastModified;
            this.size = size;
            this.isDirectory = isDirectory;
        }

        public boolean isModified(FileSnapshot other) {
            if (isDirectory) {
                return false;
            }
            return this.lastModified != other.lastModified ||
                    this.size != other.size;
        }
    }

    /**
     * Resultado da sincronização
     * MODIFICADO: Adiciona flag notIndexed
     */
    public static class SyncResult {
        private final String folderPath;
        private int added = 0;
        private int updated = 0;
        private int deleted = 0;
        private long elapsedTime = 0;
        private String error = null;
        private boolean notIndexed = false; // NOVO

        private List<String> newFiles = new ArrayList<>();
        private List<String> modifiedFiles = new ArrayList<>();
        private List<String> deletedFiles = new ArrayList<>();

        public SyncResult(String folderPath) {
            this.folderPath = folderPath;
        }

        public void setAdded(int added) { this.added = added; }
        public void setUpdated(int updated) { this.updated = updated; }
        public void setDeleted(int deleted) { this.deleted = deleted; }
        public void setElapsedTime(long elapsed) { this.elapsedTime = elapsed; }
        public void setError(String error) { this.error = error; }
        public void setNotIndexed(boolean notIndexed) { this.notIndexed = notIndexed; } // NOVO

        public void addNewFile(String path) { newFiles.add(path); }
        public void addModifiedFile(String path) { modifiedFiles.add(path); }
        public void addDeletedFile(String path) { deletedFiles.add(path); }

        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getDeleted() { return deleted; }
        public long getElapsedTime() { return elapsedTime; }
        public boolean isNotIndexed() { return notIndexed; } // NOVO

        public boolean hasChanges() {
            return added > 0 || updated > 0 || deleted > 0;
        }

        public boolean hasError() {
            return error != null;
        }

        /**
         * Retorna resumo do resultado
         * MODIFICADO: Trata caso de pasta não indexada
         */
        public String getSummary() {
            if (notIndexed) {
                return "⚠️ Pasta não indexada - Use 'Indexar' primeiro";
            }

            if (hasError()) {
                return "❌ Erro: " + error;
            }

            if (!hasChanges()) {
                return "✅ Índice sincronizado";
            }

            return String.format("✅ Sincronizado: +%d | ↻%d | -%d",
                    added, updated, deleted);
        }

        @Override
        public String toString() {
            if (notIndexed) {
                return "\n⚠️ PASTA NÃO INDEXADA\n" +
                        "   Use o botão 'Indexar' para indexar esta pasta primeiro.\n";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n╔════════════════════════════════════════════════════════════╗\n");
            sb.append("║           RESULTADO DA SINCRONIZAÇÃO                      ║\n");
            sb.append("╚════════════════════════════════════════════════════════════╝\n");
            sb.append(String.format("📂 Pasta: %s\n", folderPath));
            sb.append(String.format("➕ Novos: %d arquivos\n", added));
            sb.append(String.format("✏️ Modificados: %d arquivos\n", updated));
            sb.append(String.format("➖ Deletados: %d arquivos\n", deleted));
            sb.append(String.format("⏱️ Tempo: %dms\n", elapsedTime));

            if (!hasChanges()) {
                sb.append("\n✅ Índice já estava sincronizado!\n");
            } else {
                sb.append(String.format("\n✅ Total de mudanças: %d\n", added + updated + deleted));
            }

            return sb.toString();
        }
    }

    public void removeFolder(String folderPath) throws SQLException {
        boolean includeSubfolders = dbManager.getIncludeSubfoldersPolicy(folderPath);
        int removed = dbManager.removeIndexedFolder(folderPath, includeSubfolders);
        searchService.clearCache(); // importante: cache pode ter resultados da pasta removida
        System.out.println("Pasta removida: " + removed + " arquivos");
    }

    public void shutdown() { // NOVO
        virtualExecutor.shutdown();
    }

    public boolean isFolderNotIndexed() {
        return isFolderNotIndexed;
    }
}

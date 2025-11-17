package com.esl.searchforfiles.service;

import com.esl.searchforfiles.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Serviço de sincronização automática do índice
 * Apenas sincroniza pastas que já foram indexadas
 */
public class SyncService {

    private final DatabaseManager dbManager;
    private final SearchService searchService;

    public SyncService(DatabaseManager dbManager, SearchService searchService) {
        this.dbManager = dbManager;
        this.searchService = searchService;
    }

    /**
     * Verifica se pasta foi indexada anteriormente
     * NOVO MÉTODO - Validação crítica
     *
     * @param folderPath Caminho da pasta
     * @return true se pasta está no índice, false caso contrário
     */
    public boolean isFolderIndexed(String folderPath) {
        try {
            String sql = "SELECT COUNT(*) FROM file_index WHERE path LIKE ? LIMIT 1";

            try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
                pstmt.setString(1, folderPath + "%");

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        boolean isIndexed = count > 0;

                        if (!isIndexed) {
                            System.out.println("⚠️ Pasta não indexada: " + folderPath);
                            System.out.println("   Use 'Indexar' antes de buscar nesta pasta");
                        }

                        return isIndexed;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Erro ao verificar pasta: " + e.getMessage());
        }

        return false;
    }

    /**
     * Sincroniza pasta com o índice
     * MODIFICADO: Valida se pasta foi indexada antes
     *
     * @param folderPath Caminho da pasta a sincronizar
     * @return Resultado da sincronização com estatísticas
     */
    public SyncResult synchronizeFolder(String folderPath) {
        System.out.println("\n🔄 Iniciando sincronização: " + folderPath);

        // VALIDAÇÃO CRÍTICA: Pasta deve estar indexada
        if (!isFolderIndexed(folderPath)) {
            System.out.println("⚠️ Sincronização ignorada: pasta não está no índice");
            SyncResult result = new SyncResult(folderPath);
            result.setNotIndexed(true);
            return result;
        }

        long startTime = System.currentTimeMillis();
        SyncResult result = new SyncResult(folderPath);

        try {
            // PASSO 1: Obtém arquivos do sistema de arquivos
            Map<String, FileSnapshot> filesInSystem = scanFileSystem(folderPath);
            System.out.println("📂 Arquivos no disco: " + filesInSystem.size());

            // PASSO 2: Obtém arquivos do índice (banco de dados)
            Map<String, FileSnapshot> filesInIndex = getIndexedFiles(folderPath);
            System.out.println("🗄️ Arquivos no índice: " + filesInIndex.size());

            // PASSO 3: Compara e sincroniza
            result = compareAndSync(filesInSystem, filesInIndex, folderPath);

            // PASSO 4: Limpa cache se houve mudanças
            if (result.hasChanges()) {
                searchService.clearCache();
                System.out.println("🗑️ Cache limpo");
            }

            long elapsed = System.currentTimeMillis() - startTime;
            result.setElapsedTime(elapsed);

            System.out.println(result);
            System.out.println("⏱️ Sincronização concluída em " + elapsed + "ms\n");

        } catch (Exception e) {
            System.err.println("❌ Erro na sincronização: " + e.getMessage());
            e.printStackTrace();
            result.setError(e.getMessage());
        }

        return result;
    }

    /**
     * Escaneia sistema de arquivos e cria snapshots
     */
    private Map<String, FileSnapshot> scanFileSystem(String folderPath) throws IOException {
        Map<String, FileSnapshot> files = new HashMap<>();
        Path rootPath = Paths.get(folderPath);

        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) {
            return files;
        }

        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    String absolutePath = file.toAbsolutePath().toString();
                    files.put(absolutePath, new FileSnapshot(
                            absolutePath,
                            attrs.lastModifiedTime().toMillis(),
                            attrs.size(),
                            attrs.isDirectory()
                    ));
                } catch (Exception e) {
                    // Ignora arquivos com erro
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                try {
                    String absolutePath = dir.toAbsolutePath().toString();
                    files.put(absolutePath, new FileSnapshot(
                            absolutePath,
                            attrs.lastModifiedTime().toMillis(),
                            0,
                            true
                    ));
                } catch (Exception e) {
                    // Ignora diretórios com erro
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
    private Map<String, FileSnapshot> getIndexedFiles(String folderPath) throws SQLException {
        Map<String, FileSnapshot> files = new HashMap<>();

        String sql = "SELECT path, last_modified, size, is_directory FROM file_index WHERE path LIKE ?";

        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, folderPath + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    files.put(path, new FileSnapshot(
                            path,
                            rs.getLong("last_modified"),
                            rs.getLong("size"),
                            rs.getBoolean("is_directory")
                    ));
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

        // DETECTAR NOVOS E MODIFICADOS
        for (Map.Entry<String, FileSnapshot> entry : systemFiles.entrySet()) {
            String path = entry.getKey();
            FileSnapshot systemFile = entry.getValue();
            FileSnapshot indexedFile = indexFiles.get(path);

            if (indexedFile == null) {
                // ARQUIVO NOVO
                indexNewFile(path);
                added.incrementAndGet();
                result.addNewFile(path);

            } else if (systemFile.isModified(indexedFile)) {
                // ARQUIVO MODIFICADO
                reindexFile(path);
                updated.incrementAndGet();
                result.addModifiedFile(path);
            }
        }

        // DETECTAR DELETADOS
        for (String indexedPath : indexFiles.keySet()) {
            if (!systemFiles.containsKey(indexedPath)) {
                // ARQUIVO DELETADO
                deleteFromIndex(indexedPath);
                deleted.incrementAndGet();
                result.addDeletedFile(indexedPath);
            }
        }

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
    public boolean needsSync(String folderPath) {
        // VALIDAÇÃO: Pasta deve estar indexada
        if (!isFolderIndexed(folderPath)) {
            return false; // Não sincroniza pasta não indexada
        }

        try {
            // Conta arquivos no disco
            AtomicInteger diskCount = new AtomicInteger(0);
            Path rootPath = Paths.get(folderPath);

            if (!Files.exists(rootPath)) {
                return false;
            }

            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    diskCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    diskCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });

            // Conta arquivos no índice
            String sql = "SELECT COUNT(*) FROM file_index WHERE path LIKE ?";
            try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
                pstmt.setString(1, folderPath + "%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int indexCount = rs.getInt(1);
                        int difference = Math.abs(diskCount.get() - indexCount);
                        boolean needsSync = difference > 0;

                        if (needsSync) {
                            System.out.println("⚠️ Sincronização necessária:");
                            System.out.println("   Disco: " + diskCount.get() + " arquivos");
                            System.out.println("   Índice: " + indexCount + " arquivos");
                            System.out.println("   Diferença: " + difference);
                        }

                        return needsSync;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erro ao verificar sincronização: " + e.getMessage());
        }

        return false;
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
}

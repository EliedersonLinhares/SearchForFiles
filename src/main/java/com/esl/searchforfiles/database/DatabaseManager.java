package com.esl.searchforfiles.database;

import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.util.FileTypeDetector;
import com.esl.searchforfiles.util.PathUtils;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.*;


public class DatabaseManager {
    private static final String DB_NAME = "file_index.db";
    private final Connection conn;

    public DatabaseManager() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
        initDatabase();
    }

    private void initDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Otimizações
            for (String pragma : DatabaseSchema.PRAGMAS) {
                stmt.execute(pragma);
            }

            // Tabelas
            stmt.execute(DatabaseSchema.CREATE_FILE_INDEX_TABLE);
            stmt.execute(DatabaseSchema.CREATE_SEARCH_STATS_TABLE);

            // Índices
            for (String index : DatabaseSchema.INDEXES) {
                stmt.execute(index);
            }
        }
    }

//    public synchronized void indexFile(Path file, BasicFileAttributes attrs) throws SQLException {
//        String path = file.toAbsolutePath().toString();
//        String name = file.getFileName().toString();
//        String extension = PathUtils.getExtension(name);
//        FileType fileType = FileTypeDetector.detect(name, attrs.isDirectory());
//        long size = attrs.size();
//        long lastModified = attrs.lastModifiedTime().toMillis();
//        String parentPath = file.getParent() != null ? file.getParent().toString() : "";
//        boolean isDirectory = attrs.isDirectory();
//        long indexedAt = System.currentTimeMillis();
//
//        String sql = """
//            INSERT OR REPLACE INTO file_index
//            (path, name, extension, file_type, size, last_modified, parent_path, is_directory, indexed_at)
//            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
//        """;
//
//        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
//            pstmt.setString(1, path);
//            pstmt.setString(2, name);
//            pstmt.setString(3, extension);
//            pstmt.setString(4, fileType.name());
//            pstmt.setLong(5, size);
//            pstmt.setLong(6, lastModified);
//            pstmt.setString(7, parentPath);
//            pstmt.setBoolean(8, isDirectory);
//            pstmt.setLong(9, indexedAt);
//            pstmt.executeUpdate();
//        }
//    }
    /**
     * Indexa um arquivo com validações robustas
     * CORRIGIDO: Trata casos onde getFileName() retorna null
     */
    public synchronized void indexFile(Path file, BasicFileAttributes attrs) throws SQLException {
        // VALIDAÇÃO 1: Path não pode ser nulo
        if (file == null) {
            return;
        }

        String path = file.toAbsolutePath().toString();

        // VALIDAÇÃO 2: getFileName() pode retornar null para raiz de drive
        Path fileNamePath = file.getFileName();
        String name;

        if (fileNamePath == null) {
            // Caso especial: raiz de drive (C:\, D:\, etc.)
            name = path; // Usa o caminho completo como nome
        } else {
            name = fileNamePath.toString();
        }

        // VALIDAÇÃO 3: Nome não pode ser vazio
        if (name == null || name.isEmpty()) {
            System.err.println("⚠️  Arquivo ignorado (nome vazio): " + path);
            return;
        }

        String extension = PathUtils.getExtension(name);
        FileType fileType = FileTypeDetector.detect(name, attrs.isDirectory());
        long size = attrs.size();
        long lastModified = attrs.lastModifiedTime().toMillis();

        // VALIDAÇÃO 4: getParent() também pode retornar null
        Path parentPath = file.getParent();
        String parentPathStr = (parentPath != null) ? parentPath.toString() : "";

        boolean isDirectory = attrs.isDirectory();
        long indexedAt = System.currentTimeMillis();

        String sql = """
            INSERT OR REPLACE INTO file_index 
            (path, name, extension, file_type, size, last_modified, parent_path, is_directory, indexed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.setString(2, name);
            pstmt.setString(3, extension);
            pstmt.setString(4, fileType.name());
            pstmt.setLong(5, size);
            pstmt.setLong(6, lastModified);
            pstmt.setString(7, parentPathStr);
            pstmt.setBoolean(8, isDirectory);
            pstmt.setLong(9, indexedAt);
            pstmt.executeUpdate();
        }
    }
    public void deleteFile(String path) throws SQLException {
        String sql = "DELETE FROM file_index WHERE path = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, path);
            pstmt.executeUpdate();
        }
    }

    public int clearDriveIndex(String driveLetter) throws SQLException {
        String drivePattern = PathUtils.normalizeDriveLetter(driveLetter) + "%";
        String sql = "DELETE FROM file_index WHERE path LIKE ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, drivePattern);
            return pstmt.executeUpdate();
        }
    }

    public int clearFolderIndex(String folderPath, boolean includeSubfolders) throws SQLException {
        String sql = includeSubfolders ?
                "DELETE FROM file_index WHERE path LIKE ?" :
                "DELETE FROM file_index WHERE parent_path = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (includeSubfolders) {
                pstmt.setString(1, folderPath + "%");
            } else {
                pstmt.setString(1, folderPath);
            }
            return pstmt.executeUpdate();
        }
    }

    public void logSearchStats(String query, int resultCount, long executionTime) {
        try {
            String sql = "INSERT INTO search_stats (query, result_count, execution_time_ms, searched_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, query);
                pstmt.setInt(2, resultCount);
                pstmt.setLong(3, executionTime);
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            // Ignora erros de log
        }
    }

    public void compactDatabase() throws SQLException {
        System.out.println("Compactando banco de dados...");
        long startTime = System.currentTimeMillis();

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("VACUUM");
            stmt.execute("ANALYZE");
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Compactação concluída em " + elapsed + "ms");
    }

    public Connection getConnection() {
        return conn;
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}

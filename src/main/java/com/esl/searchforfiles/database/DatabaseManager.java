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

            migrateDatabase();

            // Índices
            for (String index : DatabaseSchema.INDEXES) {
                stmt.execute(index);
            }
        }
    }

    private void migrateDatabase() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Tenta adicionar a coluna; ignora se já existir
            try {
                stmt.execute("ALTER TABLE file_index ADD COLUMN rating INTEGER DEFAULT 0 CHECK(rating BETWEEN 0 AND 5)");
            } catch (SQLException e) {
                // Coluna já existe, ignora
            }

            stmt.execute(DatabaseSchema.CREATE_TAGS_TABLE);
            stmt.execute(DatabaseSchema.CREATE_FILE_TAGS_TABLE);

            // Índices úteis para as novas tabelas
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rating ON file_index(rating)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_file_tags ON file_tags(file_path)");
        }
    }

    // --- RATING ---

    public void setRating(String path, int stars) throws SQLException {
        if (stars < 0 || stars > 5) throw new IllegalArgumentException("Rating deve ser entre 0 e 5");
        String sql = "UPDATE file_index SET rating = ? WHERE path = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setInt(1, stars);
            p.setString(2, path);
            p.executeUpdate();
        }
    }

// --- TAGS ---

    public void createTag(String tagName) throws SQLException {
        String sql = "INSERT OR IGNORE INTO tags (name) VALUES (?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, tagName.trim());
            p.executeUpdate();
        }
    }

    public void addTagToFile(String path, String tagName) throws SQLException {
        createTag(tagName); // garante que a tag existe
        String sql = """
        INSERT OR IGNORE INTO file_tags (file_path, tag_id)
        SELECT ?, id FROM tags WHERE name = ? COLLATE NOCASE
    """;
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path);
            p.setString(2, tagName);
            p.executeUpdate();
        }
    }

    public void removeTagFromFile(String path, String tagName) throws SQLException {
        String sql = """
        DELETE FROM file_tags WHERE file_path = ?
        AND tag_id = (SELECT id FROM tags WHERE name = ? COLLATE NOCASE)
    """;
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path);
            p.setString(2, tagName);
            p.executeUpdate();
        }
    }

    public List<String> getTagsForFile(String path) throws SQLException {
        String sql = """
        SELECT t.name FROM tags t
        JOIN file_tags ft ON ft.tag_id = t.id
        WHERE ft.file_path = ?
        ORDER BY t.name
    """;
        List<String> tags = new ArrayList<>();
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) tags.add(rs.getString("name"));
            }
        }
        return tags;
    }

    public List<String> getAllTags() throws SQLException {
        List<String> tags = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM tags ORDER BY name")) {
            while (rs.next()) tags.add(rs.getString("name"));
        }
        return tags;
    }

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

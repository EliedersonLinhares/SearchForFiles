package com.esl.searchforfiles.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseInformation {
    
    private final DatabaseManager databaseManager;
    
    public DatabaseInformation(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    // ── Informações gerais do banco ───────────────────────────────

    /** Total de arquivos indexados (excluindo pastas). */
    public long getTotalFiles() throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_index WHERE is_directory = 0";
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Total de pastas indexadas. */
    public long getTotalFolders() throws SQLException {
        String sql = "SELECT COUNT(*) FROM file_index WHERE is_directory = 1";
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Data da última indexação (maior indexed_at do banco). */
    public LocalDateTime getLastIndexedAt() throws SQLException {
        String sql = "SELECT MAX(indexed_at) FROM file_index";
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next() && rs.getLong(1) > 0)
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(rs.getLong(1)),
                        ZoneId.systemDefault());
            return null;
        }
    }

    /** Data da última modificação de arquivo registrada no índice. */
    public LocalDateTime getLastModifiedFile() throws SQLException {
        String sql = "SELECT MAX(last_modified) FROM file_index WHERE is_directory = 0";
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next() && rs.getLong(1) > 0)
                return LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(rs.getLong(1)),
                        ZoneId.systemDefault());
            return null;
        }
    }

    /** Tamanho total de todos os arquivos indexados em bytes. */
    public long getTotalIndexedSizeBytes() throws SQLException {
        String sql = "SELECT SUM(size) FROM file_index WHERE is_directory = 0";
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Tamanho do arquivo .db em bytes. */
    public long getDatabaseFileSizeBytes() {
        return DatabaseManager.DB_PATH.toFile().length();
    }

    /** Quantas pastas raiz distintas foram indexadas. */
    public List<String> getIndexedRoots() throws SQLException {
        String sql = """
            SELECT DISTINCT parent_path FROM file_index
            WHERE parent_path NOT IN (
                SELECT DISTINCT parent_path FROM file_index fi2
                WHERE file_index.parent_path LIKE fi2.parent_path || '%'
                  AND file_index.parent_path != fi2.parent_path
            )
            ORDER BY parent_path
            LIMIT 100
            """;
        // Abordagem mais simples e performática: busca os paths únicos de nível 1
        String sqlRoots = """
            SELECT DISTINCT
                CASE
                    WHEN INSTR(SUBSTR(path, 4), '\\') = 0
                    THEN path
                    ELSE SUBSTR(path, 1, 3) ||
                         SUBSTR(SUBSTR(path, 4), 1,
                                INSTR(SUBSTR(path, 4), '\\') - 1)
                END AS root
            FROM file_index
            ORDER BY root
            """;
        List<String> roots = new ArrayList<>();
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sqlRoots)) {
            while (rs.next()) roots.add(rs.getString("root"));
        }
        return roots;
    }

    /** Top 5 extensões mais indexadas com contagem. */
    public List<Map.Entry<String, Long>> getTopExtensions(int limit) throws SQLException {
        String sql = """
            SELECT extension, COUNT(*) as cnt
            FROM file_index
            WHERE is_directory = 0
              AND extension IS NOT NULL
              AND extension != ''
            GROUP BY extension
            ORDER BY cnt DESC
            LIMIT ?
            """;
        List<Map.Entry<String, Long>> result = new ArrayList<>();
        try (PreparedStatement p = databaseManager.getConn().prepareStatement(sql)) {
            p.setInt(1, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next())
                    result.add(Map.entry(rs.getString("extension"),
                            rs.getLong("cnt")));
            }
        }
        return result;
    }

    /** Arquivos com rating > 0, agrupados por estrela. */
    public Map<Integer, Long> getRatingDistribution() throws SQLException {
        String sql = """
            SELECT rating, COUNT(*) as cnt
            FROM file_index
            WHERE rating > 0
            GROUP BY rating
            ORDER BY rating
            """;
        Map<Integer, Long> dist = new LinkedHashMap<>();
        try (Statement s = databaseManager.getConn().createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next())
                dist.put(rs.getInt("rating"), rs.getLong("cnt"));
        }
        return dist;
    }

    /** Total de tags cadastradas e total de arquivos com ao menos uma tag. */
    public long[] getTagStats() throws SQLException {
        long totalTags = 0, filesWithTags = 0;
        try (Statement s = databaseManager.getConn().createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM tags")) {
                if (rs.next()) totalTags = rs.getLong(1);
            }
            try (ResultSet rs = s.executeQuery(
                    "SELECT COUNT(DISTINCT file_path) FROM file_tags")) {
                if (rs.next()) filesWithTags = rs.getLong(1);
            }
        }
        return new long[]{totalTags, filesWithTags};
    }

    /** Formata bytes em KB / MB / GB legível. */
    public static String formatBytes(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /** Formata LocalDateTime para exibição amigável. */
    public static String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "—";
        return dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

}

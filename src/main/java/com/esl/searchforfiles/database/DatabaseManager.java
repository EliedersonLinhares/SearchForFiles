package com.esl.searchforfiles.database;

import com.esl.searchforfiles.configuration.FingerprintCalculator;
import com.esl.searchforfiles.configuration.NtfsFileIdReader;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.util.FileTypeDetector;
import com.esl.searchforfiles.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


public class DatabaseManager {
    private static final String DB_NAME = "file_index.db";
    private final Connection conn;

    public DatabaseManager() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_NAME);
        initDatabase();
        migrateToIdentitySystem();
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

//    public void setRating(String path, int stars) throws SQLException {
//        if (stars < 0 || stars > 5) throw new IllegalArgumentException("Rating deve ser entre 0 e 5");
//        String sql = "UPDATE file_index SET rating = ? WHERE path = ?";
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setInt(1, stars);
//            p.setString(2, path);
//            p.executeUpdate();
//        }
//    }

// --- TAGS ---

    public void createTag(String tagName) throws SQLException {
        String sql = "INSERT OR IGNORE INTO tags (name) VALUES (?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, tagName.trim());
            p.executeUpdate();
        }
    }

//    public void addTagToFile(String path, String tagName) throws SQLException {
//        createTag(tagName); // garante que a tag existe
//        String sql = """
//        INSERT OR IGNORE INTO file_tags (file_path, tag_id)
//        SELECT ?, id FROM tags WHERE name = ? COLLATE NOCASE
//    """;
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setString(1, path);
//            p.setString(2, tagName);
//            p.executeUpdate();
//        }
//    }

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
//
//    public List<String> getTagsForFile(String path) throws SQLException {
//        String sql = """
//                    SELECT t.name FROM tags t
//                    JOIN file_tags ft ON ft.tag_id = t.id
//                    WHERE ft.file_path = ?
//                    ORDER BY t.name
//                """;
//        List<String> tags = new ArrayList<>();
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setString(1, path);
//            try (ResultSet rs = p.executeQuery()) {
//                while (rs.next()) tags.add(rs.getString("name"));
//            }
//        }
//        return tags;
//    }


    public List<String> getTagsForFile(String path) throws SQLException {

        String checkSql = "SELECT ntfs_file_id, fingerprint FROM file_index WHERE path = ?";
        try (PreparedStatement p = conn.prepareStatement(checkSql)) {
            p.setString(1, path);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    System.out.println("getTagsForFile → ntfs=" + rs.getString(1)
                            + " fp=" + rs.getString(2));
                } else {
                    System.out.println("getTagsForFile → path NÃO encontrado no índice: " + path);
                }
            }
        }

        // Tenta buscar pela identity primeiro
        String sqlByIdentity = """
        SELECT DISTINCT t.name
        FROM tags t
        JOIN file_tags ft ON ft.tag_id = t.id
        JOIN file_identity fi ON fi.id = ft.identity_id
        WHERE fi.ntfs_file_id = (
            SELECT ntfs_file_id FROM file_index WHERE path = ?
        )
        OR fi.fingerprint = (
            SELECT fingerprint FROM file_index WHERE path = ?
        )
        ORDER BY t.name COLLATE NOCASE
    """;

        List<String> tags = new ArrayList<>();
        try (PreparedStatement p = conn.prepareStatement(sqlByIdentity)) {
            p.setString(1, path);
            p.setString(2, path);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) tags.add(rs.getString("name"));
            }
        }

        // Fallback: busca pelo path direto (dados legados sem identity_id)
        if (tags.isEmpty()) {
            String sqlByPath = """
            SELECT t.name FROM tags t
            JOIN file_tags ft ON ft.tag_id = t.id
            WHERE ft.file_path = ?
            ORDER BY t.name COLLATE NOCASE
        """;
            try (PreparedStatement p = conn.prepareStatement(sqlByPath)) {
                p.setString(1, path);
                try (ResultSet rs = p.executeQuery()) {
                    while (rs.next()) tags.add(rs.getString("name"));
                }
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
     * Busca tags cujo nome contém o termo informado (case-insensitive).
     * Limita a 'limit' resultados para não sobrecarregar a UI.
     * O índice COLLATE NOCASE em tags(name) garante performance.
     */
    public List<String> searchTags(String term, int limit) throws SQLException {
        String sql = """
                    SELECT name FROM tags
                    WHERE name LIKE ? COLLATE NOCASE
                    ORDER BY name COLLATE NOCASE
                    LIMIT ?
                """;
        List<String> result = new ArrayList<>();
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, "%" + term + "%");
            p.setInt(2, limit);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) result.add(rs.getString("name"));
            }
        }
        return result;
    }

    /**
     * Indexa um arquivo com validações robustas
     * CORRIGIDO: Trata casos onde getFileName() retorna null
     */
//    public synchronized void indexFile(Path file, BasicFileAttributes attrs) throws SQLException {
//        // VALIDAÇÃO 1: Path não pode ser nulo
//        if (file == null) {
//            return;
//        }
//
//        String path = file.toAbsolutePath().toString();
//
//        // VALIDAÇÃO 2: getFileName() pode retornar null para raiz de drive
//        Path fileNamePath = file.getFileName();
//        String name;
//
//        if (fileNamePath == null) {
//            // Caso especial: raiz de drive (C:\, D:\, etc.)
//            name = path; // Usa o caminho completo como nome
//        } else {
//            name = fileNamePath.toString();
//        }
//
//        // VALIDAÇÃO 3: Nome não pode ser vazio
//        if (name == null || name.isEmpty()) {
//            System.err.println("⚠️  Arquivo ignorado (nome vazio): " + path);
//            return;
//        }
//
//        String extension = PathUtils.getExtension(name);
//        FileType fileType = FileTypeDetector.detect(name, attrs.isDirectory());
//        long size = attrs.size();
//        long lastModified = attrs.lastModifiedTime().toMillis();
//
//        // VALIDAÇÃO 4: getParent() também pode retornar null
//        Path parentPath = file.getParent();
//        String parentPathStr = (parentPath != null) ? parentPath.toString() : "";
//
//        boolean isDirectory = attrs.isDirectory();
//        long indexedAt = System.currentTimeMillis();
//
////        String sql = """
////            INSERT OR REPLACE INTO file_index
////            (path, name, extension, file_type, size, last_modified, parent_path, is_directory, indexed_at)
////            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
////        """;
//
//        // NOVO: calcula identidade
//        String ntfsId = NtfsFileIdReader.readFileId(file);
//        String fingerprint = FingerprintCalculator.calculate(file, attrs);
//
//        String sql = """
//                    INSERT OR REPLACE INTO file_index
//                    (path, name, extension, file_type, size, last_modified,
//                     parent_path, is_directory, indexed_at, ntfs_file_id, fingerprint)
//                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
//                """;
//
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setString(1, path);
//            p.setString(2, name);
//            p.setString(3, extension);
//            p.setString(4, fileType.name());
//            p.setLong(5, size);
//            p.setLong(6, lastModified);
//            p.setString(7, parentPathStr);
//            p.setBoolean(8, isDirectory);
//            p.setLong(9, indexedAt);
//            p.setString(10, ntfsId);        // NOVO
//            p.setString(11, fingerprint);   // NOVO
//            p.executeUpdate();
//        }
//
//        // NOVO: garante que a identity existe na tabela file_identity
//        if (fingerprint != null) {
//            upsertIdentity(ntfsId, fingerprint, path);
//        }
//    }



public synchronized void indexFile(Path file,
                                   BasicFileAttributes attrs) throws SQLException {
    if (file == null) return;

    String path = file.toAbsolutePath().toString();
    Path fileNamePath = file.getFileName();
    String name = fileNamePath != null ? fileNamePath.toString() : path;
    if (name.isEmpty()) return;

    String    extension    = PathUtils.getExtension(name);
    FileType  fileType     = FileTypeDetector.detect(name, attrs.isDirectory());
    long      size         = attrs.size();
    long      lastModified = attrs.lastModifiedTime().toMillis();
    Path      parentPath   = file.getParent();
    String    parentStr    = parentPath != null ? parentPath.toString() : "";
    boolean   isDirectory  = attrs.isDirectory();
    long      indexedAt    = System.currentTimeMillis();
    String    ntfsId       = NtfsFileIdReader.readFileId(file);
    String    fingerprint  = FingerprintCalculator.calculate(file, attrs);

    // NOVO: garante identity e atualiza last_path
    long identityId = -1;
    if (fingerprint != null) {
        identityId = upsertIdentity(ntfsId, fingerprint, path);
    }

    // NOVO: busca rating atual da identity para preservar
    int existingRating = 0;
    if (identityId > 0) {
        existingRating = getRatingByIdentity(identityId);
    }

    String sql = """
        INSERT OR REPLACE INTO file_index
        (path, name, extension, file_type, size, last_modified,
         parent_path, is_directory, indexed_at,
         ntfs_file_id, fingerprint, rating)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """;

    try (PreparedStatement p = conn.prepareStatement(sql)) {
        p.setString(1,  path);
        p.setString(2,  name);
        p.setString(3,  extension);
        p.setString(4,  fileType.name());
        p.setLong(5,    size);
        p.setLong(6,    lastModified);
        p.setString(7,  parentStr);
        p.setBoolean(8, isDirectory);
        p.setLong(9,    indexedAt);
        p.setString(10, ntfsId);
        p.setString(11, fingerprint);
        p.setInt(12,    existingRating); // NOVO: preserva rating
        p.executeUpdate();
    }
}


    // Busca rating pela identity (não pelo path)
    private int getRatingByIdentity(long identityId) throws SQLException {
        String sql = "SELECT rating FROM file_identity WHERE id = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, identityId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? rs.getInt("rating") : 0;
            }
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


    // ── migrateLegacyRatings() ────────────────────────────────────────
    private void migrateLegacyRatings() throws SQLException {
        String sql = """
                    SELECT path, ntfs_file_id, fingerprint, rating
                    FROM file_index
                    WHERE rating > 0
                """;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {

            while (rs.next()) {
                String ntfsId = rs.getString("ntfs_file_id");
                String fingerprint = rs.getString("fingerprint");
                int rating = rs.getInt("rating");
                String path = rs.getString("path");

                if (fingerprint == null) continue;

                // Garante que a identity existe
                long identityId = upsertIdentity(ntfsId, fingerprint, path);

                // Move o rating para file_identity
                String update = """
                            UPDATE file_identity SET rating = ?
                            WHERE id = ? AND rating = 0
                        """;
                try (PreparedStatement p = conn.prepareStatement(update)) {
                    p.setInt(1, rating);
                    p.setLong(2, identityId);
                    p.executeUpdate();
                }
            }
            System.out.println("✅ Ratings migrados para file_identity.");
        }
    }


    /**
     * Migração automática ao iniciar:
     * 1. Cria tabela file_identity
     * 2. Adiciona colunas ntfs_file_id e fingerprint na file_index
     * 3. Migra ratings e tags existentes (vinculados ao path)
     * para o novo sistema de identidade
     */
    public void migrateToIdentitySystem() throws SQLException {
        System.out.println("🔄 Verificando migração para sistema de identidade...");

        try (Statement stmt = conn.createStatement()) {

            // Cria tabela de identidade
            stmt.execute(DatabaseSchema.CREATE_FILE_IDENTITY_TABLE);
            stmt.execute(DatabaseSchema.IDX_IDENTITY_NTFS);
            stmt.execute(DatabaseSchema.IDX_IDENTITY_FP);


            executeSafe(stmt, DatabaseSchema.ALTER_IDENTITY_RATING);

            // Migra ratings existentes de file_index → file_identity
            migrateLegacyRatings();



            // Adiciona colunas (ignora se já existem)
            executeSafe(stmt, DatabaseSchema.ALTER_FILE_INDEX_NTFS);
            executeSafe(stmt, DatabaseSchema.ALTER_FILE_INDEX_FP);
            executeSafe(stmt, DatabaseSchema.ALTER_FILE_TAGS_IDENTITY);

            // Verifica se há dados legados para migrar
            // (arquivos com rating > 0 ou tags mas sem identity)
            long toMigrate = countLegacyData();
            if (toMigrate == 0) {
                System.out.println("✅ Sistema de identidade já atualizado.");
                return;
            }

            System.out.println("📦 Migrando " + toMigrate + " arquivos com rating/tags...");
            migrateLegacyData();
            // Adicione em migrateToIdentitySystem(), após as outras migrações:
            cleanupDuplicateIdentities();
            System.out.println("✅ Migração concluída.");

        }
    }


    private void cleanupDuplicateIdentities() throws SQLException {
        System.out.println("🧹 Limpando identidades duplicadas...");

        // Recalcula fingerprint de todos os arquivos indexados
        // para corrigir os que foram calculados com creationTime
        String sql = "SELECT path FROM file_index WHERE fingerprint IS NOT NULL";

        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {

            while (rs.next()) {
                String path = rs.getString("path");
                try {
                    Path p = Paths.get(path);
                    if (!Files.exists(p)) continue;

                    BasicFileAttributes attrs =
                            Files.readAttributes(p, BasicFileAttributes.class);

                    String newFp   = FingerprintCalculator.calculate(p, attrs);
                    String ntfsId  = NtfsFileIdReader.readFileId(p);

                    // Atualiza fingerprint na file_index
                    updateFileIndexIdentity(path, ntfsId, newFp);

                    // Faz merge das identidades se havia duplicata
                    mergeIdentities(ntfsId, newFp, path);

                } catch (Exception e) {
                    // Arquivo sem permissão ou inacessível — ignora
                }
            }
        }
        System.out.println("✅ Limpeza de duplicatas concluída.");
    }

    /**
     * Se existem duas file_identity para o mesmo arquivo
     * (uma pelo ntfsId e outra pelo fingerprint antigo),
     * consolida tudo na mais antiga e remove a duplicata.
     */
    private void mergeIdentities(String ntfsId, String fingerprint,
                                 String path) throws SQLException {
        if (ntfsId == null || fingerprint == null) return;

        Long idByNtfs = findIdentityByNtfs(ntfsId);
        Long idByFp   = findIdentityByFingerprint(fingerprint);

        if (idByNtfs == null || idByFp == null || idByNtfs.equals(idByFp)) {
            // Sem duplicata — apenas garante que o upsert está correto
            upsertIdentity(ntfsId, fingerprint, path);
            return;
        }

        // Há duas identidades diferentes — consolida na mais antiga (menor id)
        long keepId   = Math.min(idByNtfs, idByFp);
        long deleteId = Math.max(idByNtfs, idByFp);

        // Migra tags da identidade a ser removida para a que será mantida
        String migrateTags = """
        UPDATE OR IGNORE file_tags
        SET identity_id = ?
        WHERE identity_id = ?
    """;
        try (PreparedStatement p = conn.prepareStatement(migrateTags)) {
            p.setLong(1, keepId);
            p.setLong(2, deleteId);
            p.executeUpdate();
        }

        // Migra rating se a identidade mantida não tiver rating
        String migrateRating = """
        UPDATE file_identity
        SET rating = (SELECT rating FROM file_identity WHERE id = ?)
        WHERE id = ? AND rating = 0
    """;
        try (PreparedStatement p = conn.prepareStatement(migrateRating)) {
            p.setLong(1, deleteId);
            p.setLong(2, keepId);
            p.executeUpdate();
        }

        // Atualiza ntfs_file_id e fingerprint na identidade mantida
        String updateKept = """
        UPDATE file_identity
        SET ntfs_file_id = ?, fingerprint = ?, last_path = ?
        WHERE id = ?
    """;
        try (PreparedStatement p = conn.prepareStatement(updateKept)) {
            p.setString(1, ntfsId);
            p.setString(2, fingerprint);
            p.setString(3, path);
            p.setLong(4, keepId);
            p.executeUpdate();
        }

        // Remove a identidade duplicada
        String deleteDup = "DELETE FROM file_identity WHERE id = ?";
        try (PreparedStatement p = conn.prepareStatement(deleteDup)) {
            p.setLong(1, deleteId);
            p.executeUpdate();
        }

        System.out.println("🔀 Identidades mescladas: " + deleteId + " → " + keepId);
    }


    /**
     * Conta arquivos legados que têm rating ou tags mas não têm identity.
     */
    private long countLegacyData() throws SQLException {
        String sql = """
                    SELECT COUNT(DISTINCT fi.path)
                    FROM file_index fi
                    LEFT JOIN file_tags ft ON ft.file_path = fi.path
                    WHERE fi.ntfs_file_id IS NULL
                      AND (fi.rating > 0 OR ft.file_path IS NOT NULL)
                """;
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /**
     * Migra dados legados: para cada arquivo com rating/tag, cria uma FileIdentity.
     */
    private void migrateLegacyData() throws SQLException {
        String sql = """
                    SELECT DISTINCT fi.path
                    FROM file_index fi
                    LEFT JOIN file_tags ft ON ft.file_path = fi.path
                    WHERE fi.ntfs_file_id IS NULL
                      AND (fi.rating > 0 OR ft.file_path IS NOT NULL)
                """;

        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery(sql)) {

            while (rs.next()) {
                String path = rs.getString("path");
                try {
                    Path p = Paths.get(path);
                    if (!Files.exists(p)) continue;

                    BasicFileAttributes attrs =
                            Files.readAttributes(p, BasicFileAttributes.class);

                    String ntfsId = NtfsFileIdReader.readFileId(p);
                    String fingerprint = FingerprintCalculator.calculate(p, attrs);

                    if (fingerprint == null) continue;

                    // Cria ou recupera identity
                    long identityId = upsertIdentity(ntfsId, fingerprint, path);

                    // Atualiza file_index com os novos campos
                    updateFileIndexIdentity(path, ntfsId, fingerprint);

                    // Migra file_tags para usar identity_id
                    migrateFileTags(path, identityId);

                } catch (Exception e) {
                    System.err.println("⚠️ Falha ao migrar: " + path + " — " + e.getMessage());
                }
            }
        }
    }

    /**
     * Insere ou recupera a identity_id para o par (ntfsId, fingerprint).
     */
    public long upsertIdentity(String ntfsId, String fingerprint,
                               String currentPath) throws SQLException {

        System.out.println("upsertIdentity → ntfs=" + ntfsId + " fp=" + fingerprint);

        // Tenta encontrar pelo NTFS File ID primeiro
        if (ntfsId != null) {
            Long id = findIdentityByNtfs(ntfsId);
            System.out.println("  findByNtfs → " + id);
            if (id != null) {
                updateLastPath(id, currentPath);
                return id;
            }
        }

        // Tenta pelo fingerprint (fallback para outro volume)
        Long id = findIdentityByFingerprint(fingerprint);
        System.out.println("  findByFp → " + id);
        if (id != null) {
            // Atualiza ntfs_file_id se agora temos (arquivo retornou ao volume)
            if (ntfsId != null) updateNtfsId(id, ntfsId);
            updateLastPath(id, currentPath);
            return id;
        }

        // Cria nova identity
        String sql = """
                    INSERT INTO file_identity (ntfs_file_id, fingerprint, last_path)
                    VALUES (?, ?, ?)
                """;
        try (PreparedStatement p = conn.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, ntfsId);
            p.setString(2, fingerprint);
            p.setString(3, currentPath);
            p.executeUpdate();
            try (ResultSet rs = p.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    private Long findIdentityByNtfs(String ntfsId) throws SQLException {
        String sql = "SELECT id FROM file_identity WHERE ntfs_file_id = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, ntfsId);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    private Long findIdentityByFingerprint(String fp) throws SQLException {
        String sql = "SELECT id FROM file_identity WHERE fingerprint = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, fp);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    private void updateLastPath(long id, String path) throws SQLException {
        String sql = "UPDATE file_identity SET last_path = ? WHERE id = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path);
            p.setLong(2, id);
            p.executeUpdate();
        }
    }

    private void updateNtfsId(long id, String ntfsId) throws SQLException {
        String sql = "UPDATE file_identity SET ntfs_file_id = ? WHERE id = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, ntfsId);
            p.setLong(2, id);
            p.executeUpdate();
        }
    }

    private void updateFileIndexIdentity(String path, String ntfsId,
                                         String fp) throws SQLException {
        String sql = """
                    UPDATE file_index SET ntfs_file_id = ?, fingerprint = ?
                    WHERE path = ?
                """;
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, ntfsId);
            p.setString(2, fp);
            p.setString(3, path);
            p.executeUpdate();
        }
    }

    private void migrateFileTags(String path, long identityId) throws SQLException {
        String sql = "UPDATE file_tags SET identity_id = ? WHERE file_path = ?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setLong(1, identityId);
            p.setString(2, path);
            p.executeUpdate();
        }
    }

    /**
     * Executa SQL ignorando erro de coluna/tabela já existente.
     */
    private void executeSafe(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException e) { /* já existe — ignora */ }
    }

    // ── Atualização do setRating() para usar identity ─────────────

//    public void setRating(String path, int stars) throws SQLException {
//        if (stars < 0 || stars > 5)
//            throw new IllegalArgumentException("Rating deve ser entre 0 e 5");
//
//        // Garante que a identity existe para este arquivo
//        ensureIdentity(path);
//
//        String sql = "UPDATE file_index SET rating = ? WHERE path = ?";
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setInt(1, stars);
//            p.setString(2, path);
//            p.executeUpdate();
//        }
//    }

    public void setRating(String path, int stars) throws SQLException {
        if (stars < 0 || stars > 5)
            throw new IllegalArgumentException("Rating deve ser entre 0 e 5");

        long identityId = ensureIdentity(path);

        // Salva na identity (persistente, sobrevive a movimentações)
        String sqlIdentity = "UPDATE file_identity SET rating = ? WHERE id = ?";
        try (PreparedStatement p = conn.prepareStatement(sqlIdentity)) {
            p.setInt(1, stars);
            p.setLong(2, identityId);
            p.executeUpdate();
        }

        // Salva também em file_index (para exibição imediata sem join)
        String sqlIndex = "UPDATE file_index SET rating = ? WHERE path = ?";
        try (PreparedStatement p = conn.prepareStatement(sqlIndex)) {
            p.setInt(1, stars);
            p.setString(2, path);
            p.executeUpdate();
        }
    }

    // ── Atualização do addTagToFile() para usar identity ──────────
//
//    public void addTagToFile(String path, String tagName) throws SQLException {
//        createTag(tagName);
//        long identityId = ensureIdentity(path);
//
//        String sql = """
//                    INSERT OR IGNORE INTO file_tags (file_path, tag_id, identity_id)
//                    SELECT ?, id, ? FROM tags WHERE name = ? COLLATE NOCASE
//                """;
//        try (PreparedStatement p = conn.prepareStatement(sql)) {
//            p.setString(1, path);
//            p.setLong(2, identityId);
//            p.setString(3, tagName);
//            p.executeUpdate();
//        }
//    }


    public void addTagToFile(String path, String tagName) throws SQLException {
        createTag(tagName);
        long identityId = ensureIdentity(path); // SEMPRE garante identity

        String sql = """
        INSERT OR IGNORE INTO file_tags (file_path, tag_id, identity_id)
        SELECT ?, id, ? FROM tags WHERE name = ? COLLATE NOCASE
    """;
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, path);
            p.setLong(2, identityId);
            p.setString(3, tagName);
            p.executeUpdate();
        }
    }
    /**
     * Garante que existe uma FileIdentity para o path.
     * Se não existir, lê o NTFS File ID e o fingerprint e cria.
     * Retorna o identity_id.
     */
    public long ensureIdentity(String path) throws SQLException {
        // Verifica se já existe na file_index
        String checkSql = """
                    SELECT fi.ntfs_file_id, fi.fingerprint
                    FROM file_index fi WHERE fi.path = ?
                """;
        try (PreparedStatement p = conn.prepareStatement(checkSql)) {
            p.setString(1, path);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    String ntfsId = rs.getString("ntfs_file_id");
                    String fp = rs.getString("fingerprint");
                    if (ntfsId != null && fp != null) {
                        // Já tem identity — só retorna o id
                        return upsertIdentity(ntfsId, fp, path);
                    }
                }
            }
        }

        // Precisa calcular — lê atributos do arquivo
        try {
            Path filePath = Paths.get(path);
            BasicFileAttributes attrs =
                    Files.readAttributes(filePath, BasicFileAttributes.class);
            String ntfsId = NtfsFileIdReader.readFileId(filePath);
            String fp = FingerprintCalculator.calculate(filePath, attrs);
            updateFileIndexIdentity(path, ntfsId, fp);
            return upsertIdentity(ntfsId, fp, path);
        } catch (IOException e) {
            throw new SQLException("Não foi possível calcular identidade: " + e.getMessage());
        }
    }


}

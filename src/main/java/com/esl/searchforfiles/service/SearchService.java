package com.esl.searchforfiles.service;

import com.esl.searchforfiles.cache.LRUCache;
import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.*;

import java.sql.*;
import java.util.*;

/**
 * Serviço responsável por todas as operações de busca
 *
 * Funcionalidades:
 * - Busca por nome (com wildcards)
 * - Busca por tipo de arquivo
 * - Busca avançada com múltiplos filtros
 * - Cache LRU para performance
 * - Geração de estatísticas
 * - Logging de buscas
 *
 * @author Sistema de Busca
 */
public class SearchService {
    private final DatabaseManager dbManager;
    private final LRUCache<String, List<FileInfo>> cache;
    private static final int CACHE_SIZE = 100;
    private static final int DEFAULT_LIMIT = 1000;

    /**
     * Construtor
     * @param dbManager Gerenciador do banco de dados
     */
    public SearchService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.cache = new LRUCache<>(CACHE_SIZE);
    }

    /**
     * Busca arquivos por nome com suporte a wildcards
     * Wildcards suportados: * (qualquer sequência) e ? (qualquer caractere)
     * Resultados são ordenados por frequência de acesso e nome
     *
     * @param pattern Padrão de busca (ex: "*.pdf", "relatorio*", "*2024*")
     * @return Lista de arquivos encontrados (máximo 1000)
     * @throws SQLException se houver erro na consulta
//     */
//    public List<FileInfo> searchByName(String pattern) throws SQLException {
//        String cacheKey = "name:" + pattern;
//
//        // Tenta obter do cache
//        List<FileInfo> cached = cache.get(cacheKey);
//        if (cached != null) {
//            System.out.println("💨 Cache HIT - Resultado instantâneo");
//            return new ArrayList<>(cached); // Retorna cópia para segurança
//        }
//
//        System.out.println("🔍 Buscando no banco de dados...");
//        long startTime = System.currentTimeMillis();
//
//        String sql = """
//            SELECT * FROM file_index
//            WHERE name LIKE ?
//            ORDER BY access_count DESC, name
//            LIMIT ?
//        """;
//
//        List<FileInfo> results = new ArrayList<>();
//
//        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(SEARCH_BASE_SQL)) {
//            pstmt.setString(1, pattern.replace("*", "%").replace("?", "_"));
//            pstmt.setInt(2, DEFAULT_LIMIT);
//
//            try (ResultSet rs = pstmt.executeQuery()) {
//                while (rs.next()) {
//                    results.add(extractFileInfo(rs));
//                }
//            }
//        }
//
//        long elapsed = System.currentTimeMillis() - startTime;
//
//        // Loga estatísticas
//        dbManager.logSearchStats(pattern, results.size(), elapsed);
//
//        // Armazena no cache
//        cache.put(cacheKey, results);
//
//        System.out.printf("✓ Busca concluída em %dms - %d resultados\n", elapsed, results.size());
//
//        return results;
//    }


// ── 4. searchByName() — usa SEARCH_BASE_SQL corretamente ─────────
    public List<FileInfo> searchByName(String pattern) throws SQLException {
        String cacheKey = "name:" + pattern;

        List<FileInfo> cached = cache.get(cacheKey);
        if (cached != null) {
            System.out.println("💨 Cache HIT - Resultado instantâneo");
            return new ArrayList<>(cached);
        }

        System.out.println("🔍 Buscando no banco de dados...");
        long startTime = System.currentTimeMillis();

        // MODIFICADO: usa SEARCH_BASE_SQL + AND name LIKE ?
        String sql = SEARCH_BASE_SQL
                + " AND fi.name LIKE ?"
                + " ORDER BY fi.access_count DESC, fi.name"
                + " LIMIT ?";

        List<FileInfo> results = new ArrayList<>();

        try (PreparedStatement pstmt = dbManager.getConnection()
                .prepareStatement(sql)) {
            pstmt.setString(1, pattern.replace("*", "%").replace("?", "_"));
            pstmt.setInt(2, DEFAULT_LIMIT);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) results.add(extractFileInfo(rs));
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        dbManager.logSearchStats(pattern, results.size(), elapsed);
        cache.put(cacheKey, results);

        System.out.printf("✓ Busca concluída em %dms - %d resultados\n",
                elapsed, results.size());
        return results;
    }



    private static final String SEARCH_BASE_SQL = """
        SELECT fi.*,
               COALESCE(fid.rating, fi.rating, 0) AS effective_rating
        FROM file_index fi
        LEFT JOIN file_identity fid
               ON fid.ntfs_file_id = fi.ntfs_file_id
               OR fid.fingerprint  = fi.fingerprint
        WHERE 1=1
        """;


    /**
     * Busca arquivos por tipo (categoria)
     * Tipos suportados: AUDIO, VIDEO, IMAGE, DOCUMENT, COMPRESSED, EXECUTABLE, FOLDER, ALL
     * Resultados ordenados por data de modificação (mais recentes primeiro)
     *
     * @param fileType Tipo de arquivo desejado
     * @return Lista de arquivos do tipo especificado (máximo 1000)
     * @throws SQLException se houver erro na consulta
     */
    public List<FileInfo> searchByFileType(FileType fileType) throws SQLException {
        String cacheKey = "type:" + fileType.name();

        // Tenta obter do cache
        List<FileInfo> cached = cache.get(cacheKey);
        if (cached != null) {
            System.out.println("💨 Cache HIT - Resultado instantâneo");
            return new ArrayList<>(cached);
        }

        System.out.println("🔍 Buscando arquivos do tipo: " + fileType);
        long startTime = System.currentTimeMillis();

        String sql = fileType == FileType.ALL ?
                "SELECT * FROM file_index ORDER BY last_modified DESC LIMIT ?" :
                "SELECT * FROM file_index WHERE file_type = ? ORDER BY last_modified DESC LIMIT ?";

        List<FileInfo> results = new ArrayList<>();

        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql)) {
            if (fileType == FileType.ALL) {
                pstmt.setInt(1, DEFAULT_LIMIT);
            } else {
                pstmt.setString(1, fileType.name());
                pstmt.setInt(2, DEFAULT_LIMIT);
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(extractFileInfo(rs));
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // Loga estatísticas
        dbManager.logSearchStats("type:" + fileType, results.size(), elapsed);

        // Armazena no cache
        cache.put(cacheKey, results);

        System.out.printf("✓ Busca concluída em %dms - %d resultados\n", elapsed, results.size());

        return results;
    }

    /**
     * Busca avançada com múltiplos critérios combinados
     * Todos os critérios são aplicados com operador AND (todos devem ser atendidos)
     *
     * Critérios suportados:
     * - Nome (com wildcards)
     * - Extensão específica
     * - Tipo de arquivo
     * - Tamanho mínimo/máximo
     * - Pasta específica (com ou sem subpastas)
     * - Drive específico
     * - Data de modificação (antes/depois)
     * - Ordenação customizada
     * - Limite de resultados
     *
     * @param criteria Objeto com os critérios de busca
     * @return Lista de arquivos que atendem TODOS os critérios
     * @throws SQLException se houver erro na consulta
     */
//    public List<FileInfo> advancedSearch(SearchCriteria criteria) throws SQLException {
//        String cacheKey = criteria.toCacheKey();
//
//        // Tenta obter do cache
//        List<FileInfo> cached = cache.get(cacheKey);
//        if (cached != null) {
//            System.out.println("Cache HIT - Resultado instantâneo");
//            return new ArrayList<>(cached);
//        }
//
//        System.out.println("🔍 Executando busca avançada...");
//        long startTime = System.currentTimeMillis();
//
//        // Constrói query SQL dinamicamente
//        StringBuilder sql = new StringBuilder("SELECT * FROM file_index WHERE 1=1");
//        List<Object> params = new ArrayList<>();
//
//        // Filtro por nome
//        if (criteria.getNamePattern() != null && !criteria.getNamePattern().isEmpty()) {
//            sql.append(" AND name LIKE ?");
//            params.add(criteria.getNamePattern().replace("*", "%").replace("?", "_"));
//        }
//
//        // Filtro por extensão
//        if (criteria.getExtension() != null && !criteria.getExtension().isEmpty()) {
//            sql.append(" AND extension = ?");
//            params.add(criteria.getExtension().toLowerCase());
//        }
//
//        // Filtro por tipo de arquivo
//        if (criteria.getFileType() != null && criteria.getFileType() != FileType.ALL) {
//            sql.append(" AND file_type = ?");
//            params.add(criteria.getFileType().name());
//        }
//
//        // Filtro por tamanho mínimo
//        if (criteria.getMinSize() != null) {
//            sql.append(" AND size >= ?");
//            params.add(criteria.getMinSize());
//        }
//
//        // Filtro por tamanho máximo
//        if (criteria.getMaxSize() != null) {
//            sql.append(" AND size <= ?");
//            params.add(criteria.getMaxSize());
//        }
//
//        // Filtro por pasta (com ou sem subpastas)
//        if (criteria.getParentPath() != null && !criteria.getParentPath().isEmpty()) {
//            if (criteria.isIncludeSubfolders()) {
//                // Inclui subpastas (busca recursiva)
//                sql.append(" AND path LIKE ?");
//                params.add(criteria.getParentPath() + "%");
//            } else {
//                // Apenas pasta atual (não recursivo)
//                sql.append(" AND parent_path = ?");
//                params.add(criteria.getParentPath());
//            }
//        }
//
//        // Filtro por drive
//        if (criteria.getDriveFilter() != null && !criteria.getDriveFilter().isEmpty()) {
//            String driveLetter = criteria.getDriveFilter().toUpperCase().replaceAll("[:\\\\]", "");
//            sql.append(" AND path LIKE ?");
//            params.add(driveLetter + ":\\%");
//        }
//
//        // Filtro por data de modificação (depois de)
//        if (criteria.getModifiedAfter() != null) {
//            sql.append(" AND last_modified >= ?");
//            params.add(criteria.getModifiedAfter());
//        }
//
//        // Filtro por data de modificação (antes de)
//        if (criteria.getModifiedBefore() != null) {
//            sql.append(" AND last_modified <= ?");
//            params.add(criteria.getModifiedBefore());
//        }
//
//        // Ordenação
//        sql.append(" ORDER BY ").append(criteria.getSortBy())
//                .append(" ").append(criteria.getSortOrder());
//
//        // Limite
//        sql.append(" LIMIT ").append(criteria.getLimit());
//
//        // Executa query
//        List<FileInfo> results = new ArrayList<>();
//
//        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql.toString())) {
//            // Define parâmetros
//            for (int i = 0; i < params.size(); i++) {
//                pstmt.setObject(i + 1, params.get(i));
//            }
//
//            // Executa e processa resultados
//            try (ResultSet rs = pstmt.executeQuery()) {
//                while (rs.next()) {
//                    results.add(extractFileInfo(rs));
//                }
//            }
//        }
//
//        long elapsed = System.currentTimeMillis() - startTime;
//
//        // Loga estatísticas
//        dbManager.logSearchStats(cacheKey, results.size(), elapsed);
//
//        // Armazena no cache
//        cache.put(cacheKey, results);
//
//        System.out.printf("✓ Busca avançada concluída em %dms - %d resultados\n", elapsed, results.size());
//
//        return results;
//    }


// ── 3. advancedSearch() — usa SEARCH_BASE_SQL ────────────────────
    public List<FileInfo> advancedSearch(SearchCriteria criteria) throws SQLException {
        String cacheKey = criteria.toCacheKey();

        List<FileInfo> cached = cache.get(cacheKey);
        if (cached != null) {
            System.out.println("Cache HIT - Resultado instantâneo");
            return new ArrayList<>(cached);
        }

        System.out.println("🔍 Executando busca avançada...");
        long startTime = System.currentTimeMillis();

        // MODIFICADO: parte do SEARCH_BASE_SQL em vez de SELECT * FROM file_index
        StringBuilder sql = new StringBuilder(SEARCH_BASE_SQL);
        List<Object> params = new ArrayList<>();

        addCriteriaToQuery(sql, params, criteria);

        sql.append(" ORDER BY fi.").append(criteria.getSortBy())
                .append(" ").append(criteria.getSortOrder());
        sql.append(" LIMIT ").append(criteria.getLimit());

        List<FileInfo> results = new ArrayList<>();

        try (PreparedStatement pstmt = dbManager.getConnection()
                .prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++)
                pstmt.setObject(i + 1, params.get(i));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) results.add(extractFileInfo(rs));
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        dbManager.logSearchStats(cacheKey, results.size(), elapsed);
        cache.put(cacheKey, results);

        System.out.printf("✓ Busca avançada concluída em %dms - %d resultados\n",
                elapsed, results.size());
        return results;
    }



    /**
     * Obtém estatísticas completas do índice
     * Inclui: total de arquivos, tamanho total, distribuição por tipo e drive
     *
     * @return Objeto com todas as estatísticas
     * @throws SQLException se houver erro na consulta
     */
    public IndexStats getIndexStats() throws SQLException {
        IndexStats stats = new IndexStats();

        try (Statement stmt = dbManager.getConnection().createStatement()) {

            // Total de arquivos
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as total FROM file_index");
            if (rs.next()) {
                stats.setTotalFiles(rs.getLong("total"));
            }

            // Distribuição por tipo de arquivo
            rs = stmt.executeQuery(
                    "SELECT file_type, COUNT(*) as count FROM file_index GROUP BY file_type"
            );
            Map<FileType, Long> byType = new HashMap<>();
            while (rs.next()) {
                String type = rs.getString("file_type");
                long count = rs.getLong("count");
                try {
                    byType.put(FileType.valueOf(type), count);
                } catch (IllegalArgumentException e) {
                    // Ignora tipos inválidos
                }
            }
            stats.setFilesByType(byType);

            // Distribuição por drive
            rs = stmt.executeQuery(
                    "SELECT SUBSTR(path, 1, 2) as drive, COUNT(*) as count " +
                            "FROM file_index " +
                            "WHERE path LIKE '_:\\%' " +
                            "GROUP BY drive"
            );
            Map<String, Long> byDrive = new HashMap<>();
            while (rs.next()) {
                String drive = rs.getString("drive");
                long count = rs.getLong("count");
                byDrive.put(drive, count);
            }
            stats.setFilesByDrive(byDrive);

            // Tamanho total
            rs = stmt.executeQuery("SELECT SUM(size) as total_size FROM file_index");
            if (rs.next()) {
                stats.setTotalSize(rs.getLong("total_size"));
            }

            // Data do último update
            rs = stmt.executeQuery("SELECT MAX(indexed_at) as last_update FROM file_index");
            if (rs.next()) {
                stats.setLastUpdate(rs.getLong("last_update"));
            }
        }

        return stats;
    }

    /**
     * Limpa todo o cache de buscas
     * Útil após indexação ou modificação massiva de arquivos
     */
    public void clearCache() {
        cache.clear();
        System.out.println("🗑️  Cache limpo");
    }

    /**
     * Extrai informações de arquivo de um ResultSet
     */

//    private FileInfo extractFileInfo(ResultSet rs) throws SQLException {
//        FileInfo fi = new FileInfo(
//                rs.getString("path"),
//                rs.getString("name"),
//                rs.getString("extension"),
//                FileType.valueOf(rs.getString("file_type")),
//                rs.getLong("size"),
//                rs.getLong("last_modified"),
//                rs.getBoolean("is_directory")
//        );
//        fi.setRating(rs.getInt("rating"));   // NOVO — coluna adicionada na migração
//        // Tags são carregadas sob demanda via dbManager.getTagsForFile()
//        return fi;
//    }

// ── 2. extractFileInfo() — usa effective_rating ──────────────────
    private FileInfo extractFileInfo(ResultSet rs) throws SQLException {
        FileInfo fi = new FileInfo(
                rs.getString("path"),
                rs.getString("name"),
                rs.getString("extension"),
                FileType.valueOf(rs.getString("file_type")),
                rs.getLong("size"),
                rs.getLong("last_modified"),
                rs.getBoolean("is_directory")
        );
        fi.setRating(rs.getInt("effective_rating")); // usa alias do JOIN
        return fi;
    }

    /**
     * Obtém informações do cache (para debugging)
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Verifica se uma busca está em cache
     */
    public boolean isCached(String cacheKey) {
        return cache.containsKey(cacheKey);
    }

    /**
     * Busca avançada com paginação
     * NOVO MÉTODO
     */
    public SearchResult advancedSearchWithPagination(SearchCriteria criteria, int page, int pageSize)
            throws SQLException {

        String cacheKey = criteria.toCacheKey() + ":page:" + page + ":size:" + pageSize;

        long startTime = System.currentTimeMillis();

        // Primeiro, conta total de resultados
        long totalResults = countResults(criteria);

        // Calcula offset
        int offset = (page - 1) * pageSize;

        // Busca com LIMIT e OFFSET
        List<FileInfo> results = executePagedQuery(criteria, offset, pageSize);

        long elapsed = System.currentTimeMillis() - startTime;

        // Cria informação de paginação
        PaginationInfo pagination = new PaginationInfo(page, pageSize, totalResults);

        // Loga estatísticas
        dbManager.logSearchStats(cacheKey, results.size(), elapsed);

        System.out.printf("✓ Página %d/%d: %d resultados em %dms\n",
                page, pagination.getTotalPages(), results.size(), elapsed);

        return new SearchResult(results, pagination);
    }
    /**
     * Conta total de resultados sem limite
     * NOVO MÉTODO
//     */
//    private long countResults(SearchCriteria criteria) throws SQLException {
//        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM file_index WHERE 1=1");
//        List<Object> params = new ArrayList<>();
//
//        // Aplica os mesmos filtros da busca principal
//        addCriteriaToQuery(sql, params, criteria);
//
//        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql.toString())) {
//            setParameters(pstmt, params);
//
//            try (ResultSet rs = pstmt.executeQuery()) {
//                if (rs.next()) {
//                    return rs.getLong(1);
//                }
//            }
//        }
//
//        return 0;
//    }

// ── 5. countResults() — usa SEARCH_BASE_SQL para consistência ────
    private long countResults(SearchCriteria criteria) throws SQLException {
        // MODIFICADO: conta a partir do mesmo JOIN para evitar divergência
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*) FROM (
            """);
        sql.append(SEARCH_BASE_SQL);

        List<Object> params = new ArrayList<>();
        addCriteriaToQuery(sql, params, criteria);

        sql.append(") AS counted");

        try (PreparedStatement pstmt = dbManager.getConnection()
                .prepareStatement(sql.toString())) {
            setParameters(pstmt, params);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /**
     * Executa query com paginação (LIMIT e OFFSET)
     * NOVO MÉTODO
//     */
//    private List<FileInfo> executePagedQuery(SearchCriteria criteria, int offset, int limit)
//            throws SQLException {
//
//        StringBuilder sql = new StringBuilder("SELECT * FROM file_index WHERE 1=1");
//        List<Object> params = new ArrayList<>();
//
//        // Aplica filtros
//        addCriteriaToQuery(sql, params, criteria);
//
//        // Ordenação
//        sql.append(" ORDER BY ").append(criteria.getSortBy())
//                .append(" ").append(criteria.getSortOrder());
//
//        // PAGINAÇÃO: LIMIT e OFFSET
//        sql.append(" LIMIT ? OFFSET ?");
//
//        List<FileInfo> results = new ArrayList<>();
//
//        try (PreparedStatement pstmt = dbManager.getConnection().prepareStatement(sql.toString())) {
//            // Define parâmetros dos filtros
//            setParameters(pstmt, params);
//
//            // Define LIMIT e OFFSET
//            int paramIndex = params.size() + 1;
//            pstmt.setInt(paramIndex, limit);
//            pstmt.setInt(paramIndex + 1, offset);
//
//            try (ResultSet rs = pstmt.executeQuery()) {
//                while (rs.next()) {
//                    results.add(extractFileInfo(rs));
//                }
//            }
//        }
//
//        return results;
//    }


// ── 6. executePagedQuery() — usa SEARCH_BASE_SQL ─────────────────
    private List<FileInfo> executePagedQuery(SearchCriteria criteria,
                                             int offset, int limit)
            throws SQLException {

        // MODIFICADO: parte do SEARCH_BASE_SQL
        StringBuilder sql = new StringBuilder(SEARCH_BASE_SQL);
        List<Object> params = new ArrayList<>();

        addCriteriaToQuery(sql, params, criteria);

        sql.append(" ORDER BY fi.").append(criteria.getSortBy())
                .append(" ").append(criteria.getSortOrder());
        sql.append(" LIMIT ? OFFSET ?");

        List<FileInfo> results = new ArrayList<>();

        try (PreparedStatement pstmt = dbManager.getConnection()
                .prepareStatement(sql.toString())) {
            setParameters(pstmt, params);
            int paramIndex = params.size() + 1;
            pstmt.setInt(paramIndex,     limit);
            pstmt.setInt(paramIndex + 1, offset);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) results.add(extractFileInfo(rs));
            }
        }
        return results;
    }


    /**
     * Adiciona critérios à query SQL
     * NOVO MÉTODO (extraído para reutilização)
//     */
//    private void addCriteriaToQuery(StringBuilder sql, List<Object> params, SearchCriteria criteria) {
//        if (criteria.getNamePattern() != null && !criteria.getNamePattern().isEmpty()) {
//            sql.append(" AND name LIKE ?");
//            params.add(criteria.getNamePattern().replace("*", "%").replace("?", "_"));
//        }
//
//        if (criteria.getExtension() != null && !criteria.getExtension().isEmpty()) {
//            sql.append(" AND extension = ?");
//            params.add(criteria.getExtension().toLowerCase());
//        }
//
//        if (criteria.getFileType() != null && criteria.getFileType() != FileType.ALL) {
//            sql.append(" AND file_type = ?");
//            params.add(criteria.getFileType().name());
//        }
//
//        if (criteria.getMinSize() != null) {
//            sql.append(" AND size >= ?");
//            params.add(criteria.getMinSize());
//        }
//
//        if (criteria.getMaxSize() != null) {
//            sql.append(" AND size <= ?");
//            params.add(criteria.getMaxSize());
//        }
//
//        if (criteria.getParentPath() != null && !criteria.getParentPath().isEmpty()) {
//            if (criteria.isIncludeSubfolders()) {
//                sql.append(" AND path LIKE ?");
//                params.add(criteria.getParentPath() + "%");
//            } else {
//                sql.append(" AND parent_path = ?");
//                params.add(criteria.getParentPath());
//            }
//        }
//
//        if (criteria.getDriveFilter() != null && !criteria.getDriveFilter().isEmpty()) {
//            String driveLetter = criteria.getDriveFilter().toUpperCase().replaceAll("[:\\\\]", "");
//            sql.append(" AND path LIKE ?");
//            params.add(driveLetter + ":\\%");
//        }
//
//        if (criteria.getModifiedAfter() != null) {
//            sql.append(" AND last_modified >= ?");
//            params.add(criteria.getModifiedAfter());
//        }
//
//        if (criteria.getModifiedBefore() != null) {
//            sql.append(" AND last_modified <= ?");
//            params.add(criteria.getModifiedBefore());
//        }
//
//        if (criteria.getMinRating() != null) {
//            sql.append(" AND rating >= ?");
//            params.add(criteria.getMinRating());
//        }
//
//        if (criteria.getTag() != null && !criteria.getTag().isEmpty()) {
//            sql.append("""
//         AND path IN (
//             SELECT ft.file_path FROM file_tags ft
//             JOIN tags t ON t.id = ft.tag_id
//             WHERE t.name = ? COLLATE NOCASE
//         )
//    """);
//            params.add(criteria.getTag());
//        }
//    }


    private void addCriteriaToQuery(StringBuilder sql, List<Object> params,
                                    SearchCriteria criteria) {
        if (criteria.getNamePattern() != null && !criteria.getNamePattern().isEmpty()) {
            sql.append(" AND fi.name LIKE ?");
            params.add(criteria.getNamePattern().replace("*", "%").replace("?", "_"));
        }
        if (criteria.getExtension() != null && !criteria.getExtension().isEmpty()) {
            sql.append(" AND fi.extension = ?");
            params.add(criteria.getExtension().toLowerCase());
        }
        if (criteria.getFileType() != null && criteria.getFileType() != FileType.ALL) {
            sql.append(" AND fi.file_type = ?");
            params.add(criteria.getFileType().name());
        }
        if (criteria.getMinSize() != null) {
            sql.append(" AND fi.size >= ?");
            params.add(criteria.getMinSize());
        }
        if (criteria.getMaxSize() != null) {
            sql.append(" AND fi.size <= ?");
            params.add(criteria.getMaxSize());
        }
        if (criteria.getParentPath() != null && !criteria.getParentPath().isEmpty()) {
            if (criteria.isIncludeSubfolders()) {
                sql.append(" AND fi.path LIKE ?");
                params.add(criteria.getParentPath() + "%");
            } else {
                sql.append(" AND fi.parent_path = ?");
                params.add(criteria.getParentPath());
            }
        }
        if (criteria.getDriveFilter() != null && !criteria.getDriveFilter().isEmpty()) {
            String drive = criteria.getDriveFilter().toUpperCase().replaceAll("[:\\\\]", "");
            sql.append(" AND fi.path LIKE ?");
            params.add(drive + ":\\%");
        }
        if (criteria.getModifiedAfter() != null) {
            sql.append(" AND fi.last_modified >= ?");
            params.add(criteria.getModifiedAfter());
        }
        if (criteria.getModifiedBefore() != null) {
            sql.append(" AND fi.last_modified <= ?");
            params.add(criteria.getModifiedBefore());
        }
        // MODIFICADO: filtra pelo effective_rating (do JOIN) em vez de fi.rating
        if (criteria.getMinRating() != null) {
            sql.append(" AND COALESCE(fid.rating, fi.rating, 0) >= ?");
            params.add(criteria.getMinRating());
        }
        if (criteria.getTag() != null && !criteria.getTag().isEmpty()) {
            sql.append("""
             AND fi.path IN (
                 SELECT ft.file_path FROM file_tags ft
                 JOIN tags t ON t.id = ft.tag_id
                 WHERE t.name = ? COLLATE NOCASE
                 UNION
                 SELECT fi2.path FROM file_index fi2
                 JOIN file_identity fid2
                      ON fid2.ntfs_file_id = fi2.ntfs_file_id
                      OR fid2.fingerprint  = fi2.fingerprint
                 JOIN file_tags ft2 ON ft2.identity_id = fid2.id
                 JOIN tags t2 ON t2.id = ft2.tag_id
                 WHERE t2.name = ? COLLATE NOCASE
             )
        """);
            params.add(criteria.getTag());
            params.add(criteria.getTag()); // dois ? no UNION
        }
    }


    /**
     * Define parâmetros no PreparedStatement
     * NOVO MÉTODO (extraído para reutilização)
     */
    private void setParameters(PreparedStatement pstmt, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            pstmt.setObject(i + 1, params.get(i));
        }
    }
    /**
     * Classe para resultado paginado
     * NOVA CLASSE INTERNA
     */
    public static class SearchResult {
        private final List<FileInfo> results;
        private final PaginationInfo pagination;

        public SearchResult(List<FileInfo> results, PaginationInfo pagination) {
            this.results = results;
            this.pagination = pagination;
        }

        public List<FileInfo> getResults() {
            return results;
        }

        public PaginationInfo getPagination() {
            return pagination;
        }
    }


}

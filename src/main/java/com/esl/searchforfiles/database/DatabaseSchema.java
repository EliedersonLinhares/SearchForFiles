package com.esl.searchforfiles.database;

public class DatabaseSchema {
    public static final String CREATE_FILE_INDEX_TABLE = """
        CREATE TABLE IF NOT EXISTS file_index (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            path TEXT UNIQUE NOT NULL,
            name TEXT NOT NULL,
            extension TEXT,
            file_type TEXT,
            size BIGINT,
            last_modified BIGINT,
            parent_path TEXT,
            is_directory BOOLEAN,
            indexed_at BIGINT,
            access_count INTEGER DEFAULT 0
        )
    """;

    public static final String CREATE_SEARCH_STATS_TABLE = """
        CREATE TABLE IF NOT EXISTS search_stats (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            query TEXT NOT NULL,
            result_count INTEGER,
            execution_time_ms BIGINT,
            searched_at BIGINT
        )
    """;

    public static final String[] INDEXES = {
            "CREATE INDEX IF NOT EXISTS idx_name ON file_index(name COLLATE NOCASE)",
            "CREATE INDEX IF NOT EXISTS idx_ext ON file_index(extension)",
            "CREATE INDEX IF NOT EXISTS idx_type ON file_index(file_type)",
            "CREATE INDEX IF NOT EXISTS idx_path ON file_index(parent_path)",
            "CREATE INDEX IF NOT EXISTS idx_modified ON file_index(last_modified)",
            "CREATE INDEX IF NOT EXISTS idx_size ON file_index(size)"
    };

    public static final String[] PRAGMAS = {
            "PRAGMA journal_mode=WAL",
            "PRAGMA synchronous=NORMAL",
            "PRAGMA cache_size=10000",
            "PRAGMA temp_store=MEMORY"
    };

    // Tabela de tags definidas pelo usuário
    public static final String CREATE_TAGS_TABLE = """
    CREATE TABLE IF NOT EXISTS tags (
        id   INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE NOT NULL COLLATE NOCASE
    )
""";

    // Tabela de relacionamento arquivo <-> tag
    public static final String CREATE_FILE_TAGS_TABLE = """
    CREATE TABLE IF NOT EXISTS file_tags (
        file_path TEXT NOT NULL,
        tag_id    INTEGER NOT NULL,
        PRIMARY KEY (file_path, tag_id),
        FOREIGN KEY (file_path) REFERENCES file_index(path) ON DELETE CASCADE,
        FOREIGN KEY (tag_id)    REFERENCES tags(id)          ON DELETE CASCADE
    )
""";



    // Em DatabaseSchema, adicione:
    public static final String ALTER_IDENTITY_RATING = """
    ALTER TABLE file_identity ADD COLUMN rating INTEGER DEFAULT 0
""";



    // Tabela que mapeia identidade → path atual
    public static final String CREATE_FILE_IDENTITY_TABLE = """
        CREATE TABLE IF NOT EXISTS file_identity (
            id            INTEGER PRIMARY KEY AUTOINCREMENT,
            ntfs_file_id  TEXT,              -- volumeSerial-fileIndex (pode ser null)
            fingerprint   TEXT NOT NULL,     -- nome:tamanho:dataCriacao
            last_path     TEXT NOT NULL,     -- path mais recente conhecido
            UNIQUE(ntfs_file_id),
            UNIQUE(fingerprint)
        )
    """;

    // Índices para busca rápida
    public static final String IDX_IDENTITY_NTFS = """
        CREATE INDEX IF NOT EXISTS idx_identity_ntfs
        ON file_identity(ntfs_file_id)
    """;
    public static final String IDX_IDENTITY_FP = """
        CREATE INDEX IF NOT EXISTS idx_identity_fp
        ON file_identity(fingerprint)
    """;

    // Migração: adiciona colunas de identidade na file_index
    // (executada uma vez via migrateDatabase())
    public static final String ALTER_FILE_INDEX_NTFS = """
        ALTER TABLE file_index ADD COLUMN ntfs_file_id TEXT
    """;
    public static final String ALTER_FILE_INDEX_FP = """
        ALTER TABLE file_index ADD COLUMN fingerprint TEXT
    """;

    // Migração: vincula ratings e tags à identity_id em vez do path
    public static final String ALTER_FILE_TAGS_IDENTITY = """
        ALTER TABLE file_tags ADD COLUMN identity_id INTEGER
            REFERENCES file_identity(id)
    """;
}

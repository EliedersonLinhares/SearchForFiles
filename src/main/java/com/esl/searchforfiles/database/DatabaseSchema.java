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
}

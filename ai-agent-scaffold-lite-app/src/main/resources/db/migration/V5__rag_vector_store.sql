CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_document (
    document_id VARCHAR(128) PRIMARY KEY,
    file_name VARCHAR(512) NOT NULL,
    chunk_count INTEGER NOT NULL CHECK (chunk_count > 0),
    cleaning_version VARCHAR(64) NOT NULL,
    chunking_version VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(128) NOT NULL,
    embedding_version VARCHAR(64) NOT NULL,
    embedding_dimensions INTEGER NOT NULL CHECK (embedding_dimensions = 1024),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rag_document_chunk (
    chunk_id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(128) NOT NULL,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    content TEXT NOT NULL CHECK (length(content) > 0),
    token_count INTEGER NOT NULL CHECK (token_count > 0),
    context_start_offset INTEGER NOT NULL CHECK (context_start_offset >= 0),
    core_start_offset INTEGER NOT NULL CHECK (core_start_offset >= context_start_offset),
    core_end_offset INTEGER NOT NULL CHECK (core_end_offset > core_start_offset),
    cleaning_version VARCHAR(64) NOT NULL,
    chunking_version VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rag_document_chunk_document
        FOREIGN KEY (document_id) REFERENCES rag_document(document_id) ON DELETE CASCADE,
    CONSTRAINT uk_rag_document_chunk_order UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_rag_document_chunk_document
    ON rag_document_chunk (document_id);

CREATE INDEX IF NOT EXISTS idx_rag_document_chunk_embedding_hnsw
    ON rag_document_chunk
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

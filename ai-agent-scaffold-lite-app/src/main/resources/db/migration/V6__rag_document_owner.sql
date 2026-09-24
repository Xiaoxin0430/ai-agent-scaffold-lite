ALTER TABLE rag_document ADD COLUMN IF NOT EXISTS owner_user_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_rag_document_owner_user_id
    ON rag_document (owner_user_id);

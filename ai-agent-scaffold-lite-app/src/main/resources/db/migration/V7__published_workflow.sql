CREATE TABLE published_workflow (
    workflow_id VARCHAR(128) PRIMARY KEY,
    config_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

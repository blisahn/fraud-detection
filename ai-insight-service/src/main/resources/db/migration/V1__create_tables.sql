CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE processed_events
(
    event_key    VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_processed_events_at ON processed_events (processed_at);

CREATE TABLE fraud_snapshots
(
    transaction_id BIGINT PRIMARY KEY,
    account_id     BIGINT,
    amount         NUMERIC(19, 4),
    currency       VARCHAR(3),
    country        VARCHAR(2),
    merchant_name  VARCHAR(255),
    score          INT,
    status         VARCHAR(32),
    reasons        JSONB,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_fraud_snapshots_status ON fraud_snapshots (status);

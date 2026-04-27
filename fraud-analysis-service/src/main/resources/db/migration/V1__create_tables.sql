CREATE TABLE processed_events(
    event_key VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_processed_events_at on processed_events(processed_at);
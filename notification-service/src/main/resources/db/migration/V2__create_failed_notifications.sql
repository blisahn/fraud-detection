CREATE TABLE failed_notifications
(
    id                BIGSERIAL PRIMARY KEY,
    transaction_id    BIGINT       NOT NULL,
    payload           TEXT         NOT NULL,
    notification_type VARCHAR(32)  NOT NULL,
    last_error        TEXT,
    attempts          INT          NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_failed_notifications_due
    ON failed_notifications (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_failed_notifications_tx
    ON failed_notifications (transaction_id);

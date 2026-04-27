-- Hesap bilgileri
CREATE TABLE accounts (
                          id             BIGSERIAL PRIMARY KEY,
                          account_number VARCHAR(20)  NOT NULL UNIQUE,
                          holder_name    VARCHAR(100) NOT NULL,
                          risk_level     VARCHAR(10)  DEFAULT 'LOW'
                              CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
                          created_at     TIMESTAMPTZ DEFAULT NOW()
);

-- Islem kayitlari
CREATE TABLE transactions (
                              id            BIGSERIAL PRIMARY KEY,
                              account_id    BIGINT         NOT NULL REFERENCES accounts(id),
                              amount        NUMERIC(19, 4) NOT NULL,
                              currency      VARCHAR(3)     NOT NULL,
                              merchant_name VARCHAR(255),
                              country       VARCHAR(2),
                              created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                              status        VARCHAR(15)    NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'APPROVED', 'FLAGGED', 'REJECTED','REVIEW_NEEDED'))
);

CREATE INDEX idx_txn_account_id ON transactions(account_id);
CREATE INDEX idx_txn_status ON transactions(status);

-- Transactional Outbox tablosu
-- NEDEN: Transaction ile ayni DB'de — @Transactional ile atomik garanti
CREATE TABLE outbox_events (
                               id             UUID PRIMARY KEY,
                               aggregate_type VARCHAR(255) NOT NULL,  -- Hangi entity tipi: "Transaction"
                               aggregate_id   VARCHAR(255) NOT NULL,  -- Entity'nin ID'si
                               event_type     VARCHAR(255) NOT NULL,  -- Event tipi: "TransactionCreated"
                               payload        JSONB        NOT NULL,  -- Event icerig (JSON)
                               created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                               published      BOOLEAN      NOT NULL DEFAULT FALSE  -- Kafka'ya gonderildi mi?
);

-- Partial index: Sadece henuz yayinlanmamis eventleri hizli bul
-- NEDEN: OutboxPoller her saniye bu sorguyu calistirir.
-- Partial index sayesinde sadece published=false olan satirlar index'lenir.
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at)
    WHERE published = FALSE;
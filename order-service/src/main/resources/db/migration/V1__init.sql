CREATE TABLE orders (
    id              UUID           PRIMARY KEY,
    customer_id     VARCHAR(100)   NOT NULL,
    amount          NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    idempotency_key VARCHAR(100)   NOT NULL,
    cancel_reason   VARCHAR(255),
    version         BIGINT         NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    -- The unique constraint is what makes order creation idempotent, even under concurrent retries.
    CONSTRAINT uq_orders_idempotency_key UNIQUE (idempotency_key)
);

-- Transactional outbox: written in the same transaction as the business change,
-- then relayed to Kafka by a background publisher.
CREATE TABLE outbox_events (
    id           UUID         PRIMARY KEY,
    aggregate_id UUID         NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;

-- Consumer-side deduplication: an event id is recorded the first time it is handled.
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

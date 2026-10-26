CREATE TABLE payments (
    id                UUID           PRIMARY KEY,
    order_id          UUID           NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    status            VARCHAR(32)    NOT NULL,
    gateway_reference VARCHAR(100)   NOT NULL,
    failure_reason    VARCHAR(255),
    version           BIGINT         NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    -- One payment per order: this makes handling OrderCreated idempotent.
    CONSTRAINT uq_payments_order_id UNIQUE (order_id),
    CONSTRAINT uq_payments_gateway_reference UNIQUE (gateway_reference)
);

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

-- Webhook deduplication: gateways deliver at-least-once, so the same event id can arrive many times.
CREATE TABLE processed_webhooks (
    event_id     VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

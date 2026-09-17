CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE products (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku         VARCHAR(64) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE inventory (
    product_id      UUID PRIMARY KEY REFERENCES products (id),
    available_qty   INTEGER NOT NULL CHECK (available_qty >= 0),
    reserved_qty    INTEGER NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id                 UUID NOT NULL,
    product_id                  UUID NOT NULL REFERENCES products (id),
    qty                         INTEGER NOT NULL CHECK (qty > 0),
    status                      VARCHAR(16) NOT NULL
        CHECK (status IN ('PENDING', 'RESERVED', 'PAID', 'EXPIRED', 'CANCELLED')),
    reservation_expires_at      TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders (id),
    event_type  VARCHAR(32) NOT NULL,
    payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
    key             VARCHAR(128) PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders (id),
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- §4.1 index strategy

CREATE UNIQUE INDEX idx_products_sku ON products (sku);

CREATE INDEX idx_orders_status_expiry ON orders (status, reservation_expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);

CREATE INDEX idx_order_events_order_time ON order_events (order_id, created_at DESC);

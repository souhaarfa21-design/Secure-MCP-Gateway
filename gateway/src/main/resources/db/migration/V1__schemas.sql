-- V1: base schema for the support-bot data the agent reaches through the gateway.
-- Flyway runs this once at gateway startup. Never edit it after it has run:
-- add a V2__..., V3__... file instead.

CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(120) NOT NULL,
    email           VARCHAR(160) NOT NULL UNIQUE,
    phone           VARCHAR(40),
    address         VARCHAR(255),
    plan            VARCHAR(20)  NOT NULL CHECK (plan IN ('FREE', 'BASIC', 'PRO', 'ENTERPRISE')),
    -- which support user owns this customer; row-level security uses it in week 2
    account_manager VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tickets (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    subject     VARCHAR(200) NOT NULL,
    body        TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'PENDING', 'CLOSED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_tickets_customer ON tickets (customer_id);

-- One row per tool call the gateway handles (filled by the AOP aspect in week 2).
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    username    VARCHAR(60),
    agent       VARCHAR(60),
    tool        VARCHAR(80),
    decision    VARCHAR(20),   -- ALLOWED / BLOCKED
    reason      TEXT,
    row_count   INTEGER,
    duration_ms BIGINT
);

CREATE INDEX idx_audit_created ON audit_log (created_at);

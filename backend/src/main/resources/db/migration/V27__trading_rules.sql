CREATE TABLE IF NOT EXISTS trading_rules (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    name          VARCHAR(120) NOT NULL,
    mode          VARCHAR(8)  NOT NULL DEFAULT 'PAPER',
    status        VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    definition    JSONB       NOT NULL,
    promoted_from BIGINT      NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_trading_rules_user_mode
    ON trading_rules (user_id, mode);

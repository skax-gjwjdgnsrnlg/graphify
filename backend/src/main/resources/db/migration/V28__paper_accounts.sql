-- 모의투자 가상 계좌/포지션/주문/체결/스냅샷 (DESIGN.md [v1.3.0] 5절)
CREATE TABLE IF NOT EXISTS paper_accounts (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    base_cash     NUMERIC(20,4) NOT NULL DEFAULT 10000000,
    cash          NUMERIC(20,4) NOT NULL DEFAULT 10000000,
    eval_interval VARCHAR(8)   NOT NULL DEFAULT 'EOD',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_paper_accounts_user ON paper_accounts (user_id);

CREATE TABLE IF NOT EXISTS paper_positions (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    symbol     VARCHAR(32)  NOT NULL,
    qty        NUMERIC(20,4) NOT NULL DEFAULT 0,
    avg_price  NUMERIC(20,4) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_paper_positions_acct_symbol
    ON paper_positions (account_id, symbol);

CREATE TABLE IF NOT EXISTS paper_orders (
    id           BIGSERIAL PRIMARY KEY,
    account_id   BIGINT       NOT NULL,
    rule_id      BIGINT,
    symbol       VARCHAR(32)  NOT NULL,
    side         VARCHAR(4)   NOT NULL,
    qty          NUMERIC(20,4) NOT NULL,
    price        NUMERIC(20,4) NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'FILLED',
    simulated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_paper_orders_acct ON paper_orders (account_id, simulated_at);

CREATE TABLE IF NOT EXISTS paper_trades (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    rule_id    BIGINT,
    symbol     VARCHAR(32)  NOT NULL,
    side       VARCHAR(4)   NOT NULL,
    qty        NUMERIC(20,4) NOT NULL,
    price      NUMERIC(20,4) NOT NULL,
    pnl        NUMERIC(20,4),
    traded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_paper_trades_acct ON paper_trades (account_id, traded_at);

CREATE TABLE IF NOT EXISTS paper_equity_snapshots (
    id         BIGSERIAL PRIMARY KEY,
    account_id BIGINT       NOT NULL,
    ts         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    equity     NUMERIC(20,4) NOT NULL,
    cash       NUMERIC(20,4) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_paper_equity_acct ON paper_equity_snapshots (account_id, ts);

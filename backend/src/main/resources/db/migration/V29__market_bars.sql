-- 시세 적재 계층 (DESIGN.md [v1.3.0] 데이터 파이프라인)
-- 일봉 OHLCV
CREATE TABLE IF NOT EXISTS market_bars (
    id           BIGSERIAL PRIMARY KEY,
    symbol       VARCHAR(32)  NOT NULL,
    trading_date DATE         NOT NULL,
    open         NUMERIC(20,4),
    high         NUMERIC(20,4),
    low          NUMERIC(20,4),
    close        NUMERIC(20,4) NOT NULL,
    volume       BIGINT,
    source       VARCHAR(16)  NOT NULL DEFAULT 'YAHOO',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_market_bars_symbol_date
    ON market_bars (symbol, trading_date);

-- 분봉(인트라데이) OHLCV
CREATE TABLE IF NOT EXISTS market_bars_intraday (
    id         BIGSERIAL PRIMARY KEY,
    symbol     VARCHAR(32)  NOT NULL,
    ts         TIMESTAMPTZ  NOT NULL,
    interval   VARCHAR(8)   NOT NULL DEFAULT '5m',
    open       NUMERIC(20,4),
    high       NUMERIC(20,4),
    low        NUMERIC(20,4),
    close      NUMERIC(20,4) NOT NULL,
    volume     BIGINT,
    source     VARCHAR(16)  NOT NULL DEFAULT 'YAHOO',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_market_bars_intraday
    ON market_bars_intraday (symbol, interval, ts);
CREATE INDEX IF NOT EXISTS idx_market_bars_intraday_symbol
    ON market_bars_intraday (symbol, ts);

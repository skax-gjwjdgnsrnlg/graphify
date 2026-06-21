-- V33: add backtested flag to trading_rules — set to true after first successful backtest run
ALTER TABLE trading_rules ADD COLUMN IF NOT EXISTS backtested BOOLEAN NOT NULL DEFAULT FALSE;

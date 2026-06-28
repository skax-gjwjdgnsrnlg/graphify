-- V37: paper_runs 엔티티 도입 + paper_trades에 run_id 태깅
-- run = 룰 start→stop 1회 (D6). 재실행 = 새 행(회차).

CREATE TABLE IF NOT EXISTS paper_runs (
    id                BIGSERIAL    PRIMARY KEY,
    rule_id           BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    started_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    ended_at          TIMESTAMPTZ,                        -- NULL = 진행중
    status            VARCHAR(8)   NOT NULL DEFAULT 'RUNNING',  -- RUNNING | STOPPED
    universe_snapshot TEXT,                               -- JSON array: 시작 시점 종목 목록
    CONSTRAINT chk_paper_run_status CHECK (status IN ('RUNNING','STOPPED'))
);

CREATE INDEX IF NOT EXISTS idx_paper_runs_rule   ON paper_runs (rule_id);
CREATE INDEX IF NOT EXISTS idx_paper_runs_user   ON paper_runs (user_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_paper_runs_status ON paper_runs (status);

-- paper_trades에 run_id 추가 (rule_id는 이미 존재)
ALTER TABLE paper_trades
    ADD COLUMN IF NOT EXISTS run_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_paper_trades_run ON paper_trades (run_id);
-- 기존 trades는 run_id = NULL (go-forward only)

-- 현재 RUNNING 중인 룰에 대해 synthetic 최초 run 생성 (orphaned RUNNING 백필)
INSERT INTO paper_runs (rule_id, user_id, started_at, status, universe_snapshot)
SELECT tr.id, tr.user_id, tr.updated_at, 'RUNNING', NULL
FROM trading_rules tr
WHERE tr.run_status = 'RUNNING';

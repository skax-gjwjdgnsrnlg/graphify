# Phase 4 Context: 대시보드·룰 생애주기·모니터·리포트 UI

## Goal

모의 대시보드(잔고·포지션·손익), 룰 상태 전환 UI(DRAFT→PAPER_LIVE→LIVE), 실시간 신호 모니터, 성과 리포트 페이지를 완성한다.

## What Already Exists

### Backend
- `PaperRuleController` — GET/POST/PUT/DELETE `/api/v1/trading/paper/rules`
- `PaperRuleService` — DRAFT/ACTIVE/PAUSED statuses only (no PAPER_LIVE promotion endpoint)
- `PaperAccountRepository`, `PaperPositionRepository`, `PaperTradeRepository` — entities from Phase 3
- `PaperEquitySnapshotRepository`, `PaperSignalLogRepository` — from Phase 3
- `TradingRuleRepository.findByUserIdAndModeOrderByUpdatedAtDesc()` — exists

### Frontend (all stubs — "준비 중" placeholder only)
- `PaperDashboardPage.tsx` — stub
- `TradingMonitorPage.tsx` — stub
- `PaperReportPage.tsx` — stub
- `TradingRulesPage.tsx` — stub
- `PaperRulesPage.tsx` — stub (separate from TradingRulesPage)
- `PaperBacktestPage.tsx` — FULLY IMPLEMENTED (reference for patterns)
- Routes already wired in `router/index.tsx`

### Frontend Patterns (from PaperBacktestPage)
- `useQuery` / `useMutation` from `@tanstack/react-query`
- `apiGet`, `apiPost`, `apiPut`, `apiDelete` from `@/lib/apiClient`
- Types in `@/types/trading.ts`
- API functions in `@/lib/ruleApi.ts`
- Tailwind dark-themed UI (bg-gray-900, text-white, text-gray-400)

## Rule Lifecycle States

Current backend supports: DRAFT, ACTIVE, PAUSED (these are legacy/unused names)
Phase 4 target lifecycle: **DRAFT → BACKTESTED → PAPER_LIVE → PAUSED → LIVE**

Key constraints:
- Only rules with ≥1 backtest run can be promoted to PAPER_LIVE
- LIVE rules cannot be edited (must create DRAFT copy)
- PAPER_LIVE rules can be paused/resumed

## New Backend APIs Needed

### Plan 04-01: Paper Dashboard API
- `GET /api/v1/trading/paper/dashboard` → `PaperDashboardDto` (account + positions + today's realized PnL + active rule count)

### Plan 04-02: Rule Lifecycle API  
- `POST /api/v1/trading/paper/rules/{id}/promote` → promote DRAFT→PAPER_LIVE (requires backtest check)
- `POST /api/v1/trading/paper/rules/{id}/pause` → PAPER_LIVE→PAUSED
- `POST /api/v1/trading/paper/rules/{id}/resume` → PAUSED→PAPER_LIVE
- `POST /api/v1/trading/paper/rules/{id}/copy` → creates DRAFT copy (for LIVE rule editing)
- Update `PaperRuleService.normalizeStatus` to accept PAPER_LIVE, BACKTESTED, LIVE

### Plan 04-03: Monitor API
- `GET /api/v1/trading/paper/monitor` → `MonitorDto` (last scheduler run, market status, recent signals)

### Plan 04-04: Report API  
- `GET /api/v1/trading/paper/report` → `ReportDto` (equity curve, win rate, MDD, Sharpe, Sortino, trade count)

## Design Decisions

- PaperDashboard loads account by current userId via PaperAccountRepository
- Positions marked-to-market using latest bar from MarketBarIntradayRepository
- Monitor page polls every 30s (React Query refetchInterval)
- Report equity curve is `paper_equity_snapshots` ordered by ts ASC (last 7 days default)
- Sharpe/Sortino reused from IntradayBacktestEngine static methods where possible

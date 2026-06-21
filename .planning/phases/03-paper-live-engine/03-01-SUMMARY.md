---
phase: "03"
plan: "01"
subsystem: "paper-execution"
tags: [paper-trading, jpa, tdd, db-write-through, signal-log]
dependency_graph:
  requires: [02-02]
  provides: [PaperExecutor, OrderExecutorPort, TradeResult, paper_signal_log migration]
  affects: [03-02-LiveEvaluationService]
tech_stack:
  added: [JPA entities (PaperAccount, PaperPosition, PaperTrade, PaperEquitySnapshot, PaperSignalLog)]
  patterns: [DB write-through per tick, OrderExecutorPort strategy interface, TDD RED-GREEN]
key_files:
  created:
    - backend/src/main/resources/db/migration/V32__paper_signal_log.sql
    - backend/src/main/java/com/graphify/trading/paper/PaperAccount.java
    - backend/src/main/java/com/graphify/trading/paper/PaperPosition.java
    - backend/src/main/java/com/graphify/trading/paper/PaperTrade.java
    - backend/src/main/java/com/graphify/trading/paper/PaperEquitySnapshot.java
    - backend/src/main/java/com/graphify/trading/paper/PaperSignalLog.java
    - backend/src/main/java/com/graphify/trading/paper/PaperAccountRepository.java
    - backend/src/main/java/com/graphify/trading/paper/PaperPositionRepository.java
    - backend/src/main/java/com/graphify/trading/paper/PaperTradeRepository.java
    - backend/src/main/java/com/graphify/trading/paper/PaperEquitySnapshotRepository.java
    - backend/src/main/java/com/graphify/trading/paper/PaperSignalLogRepository.java
    - backend/src/main/java/com/graphify/trading/paper/OrderExecutorPort.java
    - backend/src/main/java/com/graphify/trading/paper/TradeResult.java
    - backend/src/main/java/com/graphify/trading/paper/PaperExecutor.java
    - backend/src/test/java/com/graphify/trading/paper/PaperExecutorTest.java
  modified: []
decisions:
  - "PaperExecutor auto-creates a 10M KRW default account on first execute (eliminates bootstrap ceremony)"
  - "Signal log errors are swallowed (warn-only) so DB glitches never block evaluation pipeline"
  - "Sizing uses RuleDefinition JSON parse with 1M KRW fallback if parse fails"
  - "OrderExecutorPort as strategy interface allows LiveExecutor swap in Phase 6 without touching evaluation logic"
metrics:
  duration: "3m"
  completed_date: "2026-06-21"
  tasks_completed: 2
  files_created: 15
---

# Phase 03 Plan 01: Paper Execution Layer Summary

**One-liner:** DB write-through paper executor with BUY/SELL/HOLD guards, PnL tracking, and per-tick signal log via OrderExecutorPort strategy interface.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Infrastructure (entities, repos, ports) | 070ba94 | V32 migration + 5 entities + 5 repos + OrderExecutorPort + TradeResult |
| 2 | PaperExecutor TDD RED→GREEN | 6de2980 | PaperExecutor.java + PaperExecutorTest.java (6 tests) |

## What Was Built

**V32 migration** — `paper_signal_log` table with indexed `(rule_id, ts DESC)` for per-tick signal audit trail. JSONB `indicator_snapshot` column holds RSI/SMA/price at evaluation time.

**JPA entities** in `com.graphify.trading.paper`:
- `PaperAccount` — 1 account per user, tracks `cash` with `setCash()` for write-through flush
- `PaperPosition` — per-symbol long position with `qty` and `avgPrice` (BigDecimal)
- `PaperTrade` — immutable trade record with `side` (BUY/SELL), `pnl` (nullable on BUY)
- `PaperEquitySnapshot` — point-in-time equity + cash snapshot per tick
- `PaperSignalLog` — per-tick per-rule per-symbol signal with executed flag + indicator JSON

**Repositories** — all extend `JpaRepository<T, Long>` with targeted finder methods:
- `PaperAccountRepository.findByUserId(Long)`
- `PaperPositionRepository.findByAccountIdAndSymbol()` + `deleteByAccountIdAndSymbol()`
- `PaperSignalLogRepository.findTop50ByOrderByTsDesc()` for recent signal feed

**`OrderExecutorPort`** — strategy interface: `execute(Signal, TradingRule, String symbol, double price, Instant ts, String indicatorJson) → TradeResult`

**`TradeResult`** — record with static factories `filled()` and `skipped(signal, symbol, reason)`

**`PaperExecutor`** — `@Service @Transactional` implementing `OrderExecutorPort`:
- BUY path: ALREADY_HOLDS guard → INSUFFICIENT_CASH guard → FillSimulator.fillPrice → sizing resolution → persist position + trade → deduct cash → flush account
- SELL path: NO_POSITION guard → FillSimulator.fillPrice → PnL = proceeds − cost basis → delete position → persist trade → add proceeds → flush account
- HOLD path: immediate skip, zero DB interaction
- `resolveSizingCash()`: parses `RuleDefinition.sizing()` — `fixed_cash` caps alloc at rule value, `full_cash` uses all available, fallback 1M KRW
- `saveSignalLog()`: swallows exceptions to prevent signal log failures from blocking execution

## Test Results

```
PaperExecutorTest (6 tests, Mockito — no DB required)
  buy_success_creates_position_and_deducts_cash    PASSED
  buy_already_holds_returns_skipped                PASSED
  buy_insufficient_cash_returns_skipped            PASSED
  sell_success_removes_position_and_records_pnl    PASSED
  sell_no_position_returns_skipped                 PASSED
  hold_always_returns_skipped                      PASSED
```

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check

Files exist:
- backend/src/main/java/com/graphify/trading/paper/PaperExecutor.java — FOUND
- backend/src/main/java/com/graphify/trading/paper/OrderExecutorPort.java — FOUND
- backend/src/main/java/com/graphify/trading/paper/TradeResult.java — FOUND
- backend/src/main/resources/db/migration/V32__paper_signal_log.sql — FOUND
- backend/src/test/java/com/graphify/trading/paper/PaperExecutorTest.java — FOUND

Commits:
- 070ba94 — feat(03-01): paper execution layer — entities, repositories, ports
- 6de2980 — feat(03-01): PaperExecutor — DB write-through paper execution (TDD GREEN)

## Self-Check: PASSED

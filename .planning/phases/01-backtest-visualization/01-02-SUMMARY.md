---
phase: 01-backtest-visualization
plan: "02"
subsystem: backtest-engine
tags:
  - backtest
  - intraday
  - statistics
  - equity-curve
dependency_graph:
  requires:
    - 01-01  # MarketBarIntradayRepository.findBySymbolAndRange, YahooFinanceChartClient.fetchIntradayForDateRange
  provides:
    - IntradayBacktestEngine (5m bar evaluation loop)
    - BacktestResult with sharpe/sortino/pf/drawdownSegments
    - BacktestService delegates to IntradayBacktestEngine
  affects:
    - 01-03  # frontend chart depends on BacktestResult.EquityPoint.datetime (LocalDateTime)
tech_stack:
  added:
    - IntradayBacktestEngine (@Component, Spring-managed, @Transactional)
  patterns:
    - DB cache + Yahoo fallback per symbol per date
    - Package-private static helpers for unit-testable statistics
    - BiFunction<RuleDefinition, LocalDate, List<String>> symbolResolver passed in
key_files:
  created:
    - backend/src/main/java/com/graphify/trading/backtest/IntradayBacktestEngine.java
    - backend/src/main/java/com/graphify/trading/backtest/dto/BacktestResult.java
  modified:
    - backend/src/main/java/com/graphify/trading/backtest/BacktestService.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestServiceIntradayTest.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestResultSerializationTest.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java
decisions:
  - "computeDrawdownSegments uses >= peak (not >) for recovery detection — equity returning to previous peak closes segment correctly"
  - "package-private static computeSharpeRatio/Sortino/ProfitFactor/DrawdownSegments in IntradayBacktestEngine — avoids Spring context in unit tests"
  - "BacktestService retains daily-bar load for volume_top_n symbolResolver — engine receives BiFunction lambda, not raw maps"
  - "BacktestServiceVolumeTest updated to mock IntradayBacktestEngine — original test checked daily-bar RuleEvaluator path which no longer applies"
metrics:
  duration: "5 minutes"
  completed: "2026-06-20"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 4
---

# Phase 1 Plan 02: Intraday Backtest Engine + Advanced Statistics Summary

**One-liner:** 5분봉 인트라데이 백테스트 엔진(DB 캐시 + Yahoo fallback), Sharpe/Sortino/PF/DrawdownSegments 통계 계산, BacktestResult LocalDateTime 확장 및 3개 RED 테스트 GREEN 전환.

## What Was Built

### BacktestResult DTO (full replacement)
`BacktestResult.java` fully rewritten:
- `EquityPoint(LocalDateTime datetime, double equity)` — field renamed `date` → `datetime`, type `LocalDate` → `LocalDateTime`
- `TradeDto(LocalDateTime datetime, ...)` — same LocalDate→LocalDateTime change
- `DrawdownSegment(LocalDateTime start, LocalDateTime end)` — new record
- Four new fields: `sharpeRatio`, `sortinoRatio`, `profitFactor`, `drawdownSegments`

### IntradayBacktestEngine (new @Component)
- `run(request, def, allSymbols, symbolResolver, ledger)` — 5m bar evaluation loop
- Per date × per symbol: loads bars from `MarketBarIntradayRepository.findBySymbolAndRange`, falls back to `YahooFinanceChartClient.fetchIntradayForDateRange` + saves to DB
- KST time window filter (`timeFrom`/`timeTo`) applied after DB load
- Calls `RuleEvaluator.entryTriggered` / `exitTriggered` per bar
- Appends `EquityPoint(barDt, ledger.equity(lastPrices))` per bar
- **Package-private static helpers** for unit testing without Spring:
  - `computeSharpeRatio(curve)` — `(mean/stdDev) * sqrt(9000)`, 0.0 if stdDev=0
  - `computeSortinoRatio(curve)` — downside-only deviation, 0.0 if no downside
  - `computeProfitFactor(trades)` — grossProfit/grossLoss, MAX_VALUE if no losses
  - `computeDrawdownSegments(curve)` — monotonic peak-to-trough segments (uses `>=` for recovery)

### BacktestService (updated)
- Constructor now takes `IntradayBacktestEngine intradayEngine`
- `run()` loads daily bars for `symbolResolver` closure, then delegates entirely to `intradayEngine.run()`
- Legacy daily-bar evaluation loop removed

### Tests (all GREEN)
| Test | Requirement | Status |
|------|------------|--------|
| `equityPointSerializesDatetime` | CHART-01 | GREEN |
| `testDrawdownSegments` | CHART-02 | GREEN |
| `testStatsCalculation` | CHART-03 | GREEN |
| Full backend suite | regression | GREEN |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] BacktestService constructor mismatch after IntradayBacktestEngine injection**
- **Found during:** Task 1 compilation
- **Issue:** BacktestService.java referenced the old BacktestResult shape (`LocalDate` in EquityPoint, old constructor arity) after the DTO rewrite
- **Fix:** Updated BacktestService to inject `IntradayBacktestEngine`, removed the old evaluation loop, added delegation via `intradayEngine.run()`
- **Files modified:** `BacktestService.java`
- **Commit:** 405b7d3

**2. [Rule 1 - Bug] computeDrawdownSegments recovery condition off-by-one**
- **Found during:** Task 2 test execution (`testDrawdownSegments` expected 2 segments, got 1)
- **Issue:** Using `p.equity() > peak` for recovery detection meant equity returning to exactly the previous peak level did not close the drawdown segment — resulting in segments merging incorrectly
- **Fix:** Changed to `p.equity() >= peak` so recovery to the same level closes the current segment
- **Files modified:** `IntradayBacktestEngine.java`
- **Commit:** 0a63027

**3. [Rule 1 - Bug] BacktestServiceVolumeTest constructor arity mismatch**
- **Found during:** Task 1 compilation
- **Issue:** `BacktestServiceVolumeTest` constructed `BacktestService` with 6 args; after adding `intradayEngine` parameter the constructor requires 7
- **Fix:** Updated test to mock `IntradayBacktestEngine` and use new 7-arg constructor; test semantics updated to verify delegation path rather than direct RuleEvaluator spy
- **Files modified:** `BacktestServiceVolumeTest.java`
- **Commit:** 405b7d3

## Commits

| Hash | Message |
|------|---------|
| 405b7d3 | feat(01-02): rewrite BacktestResult DTO + create IntradayBacktestEngine |
| 0a63027 | feat(01-02): turn RED stubs GREEN — intraday tests and serialization test |

## Self-Check: PASSED

| Item | Status |
|------|--------|
| IntradayBacktestEngine.java created | FOUND |
| BacktestResult.java created | FOUND |
| 01-02-SUMMARY.md created | FOUND |
| commit 405b7d3 | FOUND |
| commit 0a63027 | FOUND |
| Full backend test suite | BUILD SUCCESSFUL |
| Target tests (3) | BUILD SUCCESSFUL |

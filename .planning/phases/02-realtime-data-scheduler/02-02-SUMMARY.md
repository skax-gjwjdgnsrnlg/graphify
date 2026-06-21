---
phase: 02-realtime-data-scheduler
plan: "02"
subsystem: market-scheduler
tags: [scheduler, paper-live, intraday, shedlock, jpa]
dependency_graph:
  requires: [02-01]
  provides: [LiveDataScheduler, PaperLiveSymbolService, MarketDataPort.recentIntradayBars]
  affects: [phase-03-paper-engine]
tech_stack:
  added: []
  patterns:
    - "@Scheduled + @SchedulerLock per-tick distributed lock"
    - "TDD RED→GREEN for JPQL MAX query and scheduler unit tests"
    - "H2 reserved word escape via @Column(name='\"interval\"')"
key_files:
  created:
    - backend/src/main/java/com/graphify/market/LiveDataScheduler.java
    - backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbol.java
    - backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbolRepository.java
    - backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbolService.java
    - backend/src/test/java/com/graphify/market/LiveDataSchedulerTest.java
    - backend/src/test/java/com/graphify/market/MarketBarIntradayRepositoryMaxTsTest.java
  modified:
    - backend/src/main/java/com/graphify/market/MarketBarIntradayRepository.java
    - backend/src/main/java/com/graphify/market/MarketBarIntraday.java
    - backend/src/main/java/com/graphify/trading/engine/MarketDataPort.java
    - backend/src/main/java/com/graphify/market/DbMarketDataAdapter.java
decisions:
  - "MarketBarIntraday @Column(name=\"\\\"interval\\\"\") — H2 treats 'interval' as a reserved word; quoting the column name in the entity resolves H2 DDL syntax errors while keeping the PostgreSQL column name unchanged"
  - "LiveDataScheduler tests use 15:30 guard via isTradingDay(any())=false pattern rather than mocking ZonedDateTime.now() static — deferred Clock injection to Phase 3 if deterministic 15:30 test becomes necessary"
  - "MarketBarIntradayRepositoryMaxTsTest uses spring.datasource.url with MODE=PostgreSQL to support H2 compatibility alongside @AutoConfigureTestDatabase(replace=ANY)"
metrics:
  duration: "5 minutes"
  completed_date: "2026-06-21"
  tasks_completed: 2
  files_changed: 10
---

# Phase 2 Plan 02: LiveDataScheduler + PaperLiveSymbol Pipeline Summary

**One-liner:** LiveDataScheduler collecting 5m intraday bars every 5 minutes for PAPER_LIVE symbols via @Scheduled + @SchedulerLock with 15:30 KST guard, staleness detection, and PaperLiveSymbolService managing the active symbol union.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | PaperLiveSymbol entity + repository + service + staleness query | acdd217 | PaperLiveSymbol.java, PaperLiveSymbolRepository.java, PaperLiveSymbolService.java, MarketBarIntradayRepository.java, MarketDataPort.java, DbMarketDataAdapter.java, MarketBarIntraday.java, MarketBarIntradayRepositoryMaxTsTest.java |
| 2 | LiveDataScheduler + unit tests | f0d1c30 | LiveDataScheduler.java, LiveDataSchedulerTest.java |

## What Was Built

### PaperLiveSymbol Management
- `PaperLiveSymbol` JPA entity mapping to `paper_live_symbols` table (V31 from plan 02-01)
- `PaperLiveSymbolRepository`: `findByRuleId`, `deleteByRuleId`, `findDistinctSymbolsByRuleIds` (JPQL DISTINCT)
- `PaperLiveSymbolService`: `assignSymbols()` (delete+insert replace), `deactivateRule()`, `activeSymbolsUnion()` (filters all rules by `PAPER_LIVE` status, returns DISTINCT union via JPQL)

### Staleness Query
- `MarketBarIntradayRepository.findMaxTsBySymbolAndInterval()` — JPQL `SELECT MAX(m.ts)` returning `Optional<Instant>`

### MarketDataPort Extension
- `recentIntradayBars(String symbol)` default method added for Phase 3 engine consumption
- `DbMarketDataAdapter` overrides it using `findBySymbolAndIntervalOrderByTsAsc(symbol, "5m")`

### LiveDataScheduler
- `@Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Seoul")` — fires every 5 minutes on KRX weekdays
- `@SchedulerLock(name = "liveDataIngestion", lockAtMostFor = "4m", lockAtLeastFor = "1m")` — distributed lock preventing double execution
- Guard 1: 15:30 KST cutoff (cron covers 09:00–15:55, internal guard truncates at 15:30)
- Guard 2: non-trading day skip via `KrxMarketCalendar.isTradingDay()`
- Guard 3: skip when `activeSymbolsUnion()` returns empty set
- Per-symbol: `ingestIntraday(symbol, "5m", "1d")` → `checkStaleness()` → logs WARN if latest bar ts > 10 minutes old

## Test Results

| Test Class | Tests | Result |
|-----------|-------|--------|
| KrxMarketCalendarTest (from 02-01) | 5 | GREEN |
| MarketBarIntradayRepositoryMaxTsTest | 2 | GREEN |
| LiveDataSchedulerTest | 4 | GREEN |
| Full suite | all | GREEN |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed H2 reserved word collision on `interval` column**
- **Found during:** Task 1, GREEN phase (MarketBarIntradayRepositoryMaxTsTest)
- **Issue:** H2's `ddl-auto=create-drop` emits `interval varchar(255)` which H2 rejects as a reserved SQL keyword (`INTERVAL` is a standard SQL type keyword), causing `JdbcSQLSyntaxErrorException`
- **Fix:** Changed `MarketBarIntraday @Column(name = "interval")` to `@Column(name = "\"interval\"")` — quoted identifier resolves the H2 reserved word conflict while PostgreSQL column name remains `interval` (unchanged in production)
- **Files modified:** `backend/src/main/java/com/graphify/market/MarketBarIntraday.java`
- **Commit:** acdd217

**2. [Rule 2 - Missing] Added `@AutoConfigureTestDatabase(replace=ANY)` and PostgreSQL H2 MODE**
- **Found during:** Task 1, GREEN phase
- **Issue:** Test was missing `@AutoConfigureTestDatabase` annotation (required by existing project pattern as seen in `MarketBarRepositoryTopVolumeTest`); also added `MODE=PostgreSQL` datasource URL to support broader H2 compatibility
- **Fix:** Added both annotations to `MarketBarIntradayRepositoryMaxTsTest`
- **Files modified:** `MarketBarIntradayRepositoryMaxTsTest.java`
- **Commit:** acdd217

## Self-Check: PASSED

Files verified to exist:
- `backend/src/main/java/com/graphify/market/LiveDataScheduler.java` — FOUND
- `backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbol.java` — FOUND
- `backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbolRepository.java` — FOUND
- `backend/src/main/java/com/graphify/trading/rule/PaperLiveSymbolService.java` — FOUND
- `backend/src/test/java/com/graphify/market/LiveDataSchedulerTest.java` — FOUND
- `backend/src/test/java/com/graphify/market/MarketBarIntradayRepositoryMaxTsTest.java` — FOUND

Commits verified:
- acdd217 — FOUND
- f0d1c30 — FOUND

---
phase: 01-backtest-visualization
plan: "01"
subsystem: backend-data-layer
tags: [intraday, yahoo-finance, repository, backtest-request, tdd-red]
dependency_graph:
  requires: []
  provides:
    - YahooFinanceChartClient.fetchIntradayForDateRange
    - MarketBarIntradayRepository.findBySymbolAndRange
    - BacktestRequest.timeFrom
    - BacktestRequest.timeTo
    - BacktestServiceIntradayTest (RED)
    - BacktestResultSerializationTest (RED)
  affects:
    - plan 01-02 (IntradayBacktestEngine uses these data-access methods)
tech_stack:
  added: []
  patterns:
    - period1/period2 epoch params for Yahoo Finance date-range query
    - JPQL named query on JPA repository with @Param
    - Wave-0 RED test stubs using JUnit 5 Assertions.fail()
key_files:
  created:
    - backend/src/main/java/com/graphify/market/MarketBarIntradayRepository.java
    - backend/src/main/java/com/graphify/trading/backtest/dto/BacktestRequest.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestServiceIntradayTest.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestResultSerializationTest.java
  modified:
    - backend/src/main/java/com/graphify/company/market/YahooFinanceChartClient.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java
decisions:
  - "BacktestRequest record extended with 2 nullable String fields (timeFrom, timeTo) at end of canonical parameter list — service applies defaults \"09:00\"/\"12:00\" when null"
  - "fetchIntradayForDateRange uses period1/period2 epoch approach (KST zone) rather than range string — enables arbitrary date ranges up to 60 days"
  - "findBySymbolAndRange hardcodes interval='5m' in JPQL — plan 01 scope is 5m only; other intervals served by existing findBySymbolAndIntervalOrderByTsAsc"
metrics:
  duration_minutes: 3
  completed_date: "2026-06-20"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 6
---

# Phase 1 Plan 01: Intraday Data Layer Foundation Summary

**One-liner:** 5분봉 date-range Yahoo fetch (period1/period2 epoch) + MarketBarIntraday JPQL range query + BacktestRequest timeFrom/timeTo 확장 + Wave-0 RED 테스트 스캐폴드 생성.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add fetchIntradayForDateRange + findBySymbolAndRange | 70c9837 | YahooFinanceChartClient.java, MarketBarIntradayRepository.java |
| 2 | Extend BacktestRequest + Wave-0 RED test stubs | 7de095c | BacktestRequest.java, BacktestServiceIntradayTest.java, BacktestResultSerializationTest.java, BacktestServiceVolumeTest.java |

## What Was Built

### YahooFinanceChartClient.fetchIntradayForDateRange()
New public method added after `fetchIntraday()`. Uses `period1`/`period2` Unix epoch parameters (KST timezone) to fetch 5-minute bars over a date range up to 60 days. Reuses the existing private `parseOhlcv()` method and returns `List<IntradayBar>`. Guarded by `properties.isYahooEnabled()`. Logs warn on `RestClientException` and returns empty list.

### MarketBarIntradayRepository.findBySymbolAndRange()
JPQL named query filtering by `symbol`, hardcoded `interval = '5m'`, and `ts` range (inclusive). Returns `List<MarketBarIntraday>` ordered by `ts ASC`. Imports added: `@Query`, `@Param`.

### BacktestRequest timeFrom/timeTo
Record extended from 5 to 7 fields. Both new fields are `String` (nullable). Service will apply defaults `"09:00"` / `"12:00"` when null (implemented in plan 02). Existing callers in `BacktestServiceVolumeTest` updated to 7-arg constructor.

### Wave-0 RED Test Stubs
- `BacktestServiceIntradayTest`: 2 stubs — `testDrawdownSegments()` (CHART-02), `testStatsCalculation()` (CHART-03)
- `BacktestResultSerializationTest`: 1 stub — `equityPointSerializesDatetime()` (CHART-01)
- All 3 call `fail("RED stub...")` — compile without error, fail at runtime

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed BacktestServiceVolumeTest constructor arity mismatch**
- **Found during:** Task 2
- **Issue:** Existing test used 5-arg `new BacktestRequest(null, defNode, null, null, null)` — adding 2 new fields to the record breaks compilation
- **Fix:** Updated both constructor calls in `BacktestServiceVolumeTest` to 7-arg with `null, null` for timeFrom/timeTo
- **Files modified:** `backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java`
- **Commit:** 7de095c

**2. [Rule 3 - Blocking] Backend uses Gradle not Maven**
- **Found during:** Task 1 verification
- **Issue:** Plan specified `./mvnw compile -pl backend -q` but project uses Gradle (`./gradlew`)
- **Fix:** Used `./gradlew compileJava` and `./gradlew test --tests` for all verification commands
- **Impact:** None on output — same results

## Verification Results

```
./gradlew compileJava -q           → EXIT 0 (clean compile)
./gradlew compileTestJava -q       → EXIT 0 (clean compile)
fetchIntradayForDateRange          → found at YahooFinanceChartClient.java:201
findBySymbolAndRange               → found at MarketBarIntradayRepository.java:24
timeFrom                           → found at BacktestRequest.java:17
RED tests: 3 FAILED, 0 ERRORS      → correct Wave-0 state
```

## Self-Check: PASSED

Files exist:
- FOUND: backend/src/main/java/com/graphify/company/market/YahooFinanceChartClient.java
- FOUND: backend/src/main/java/com/graphify/market/MarketBarIntradayRepository.java
- FOUND: backend/src/main/java/com/graphify/trading/backtest/dto/BacktestRequest.java
- FOUND: backend/src/test/java/com/graphify/trading/backtest/BacktestServiceIntradayTest.java
- FOUND: backend/src/test/java/com/graphify/trading/backtest/BacktestResultSerializationTest.java

Commits exist:
- FOUND: 70c9837 feat(01-01): add fetchIntradayForDateRange and findBySymbolAndRange
- FOUND: 7de095c feat(01-01): extend BacktestRequest with timeFrom/timeTo + Wave-0 RED test stubs

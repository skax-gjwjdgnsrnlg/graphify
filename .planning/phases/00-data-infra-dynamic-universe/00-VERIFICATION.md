---
phase: 00-data-infra-dynamic-universe
verified: 2026-06-20T18:56:00+09:00
status: passed
score: 6/6 must-haves verified
re_verification: false
---

# Phase 00: Data Infrastructure & Dynamic Universe Verification Report

**Phase Goal:** Establish data infrastructure and dynamic universe selection so that volume_top_n backtests can run without look-ahead bias.
**Verified:** 2026-06-20T18:56:00+09:00
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | VOLUME > 30000 rule with volume=50000 bar generates BUY signal | VERIFIED | `BacktestService` extracts `volumes[]` via `bars.get(i).volume()` (line 85); `RuleEvaluator` VOLUME case uses `volumes[i]` value; BacktestServiceVolumeTest test 1 GREEN |
| 2 | BacktestService passes non-null Double[] volumes to RuleEvaluator | VERIFIED | `volumesBySymbol` declared line 69, populated line 91, retrieved line 119; BacktestServiceVolumeTest test 2 (ArgumentCaptor) GREEN |
| 3 | companies table has in_kospi200 BOOLEAN column + KOSPI 200 seed data | VERIFIED | V30__kospi200_universe.sql: `ALTER TABLE companies ADD COLUMN IF NOT EXISTS in_kospi200 BOOLEAN NOT NULL DEFAULT FALSE` + UPDATE with 94 KOSPI tickers; CompanyEntityTest 4/4 GREEN |
| 4 | ingestDailyForKospi200() processes only in_kospi200=true companies, skips null tickers, returns count | VERIFIED | `MarketDataIngestionService.ingestDailyForKospi200()` calls `companyRepository.findByInKospi200True()` (line 74), null-ticker guard (line 77), count>0 logic (line 81); MarketDataIngestionServiceKospi200Test 5/5 GREEN |
| 5 | volume_top_n Universe JSON deserializes correctly; RuleDefinitionValidator allows volume_top_n type | VERIFIED | `Universe` record has `market`, `topN`, `additionalSymbols` fields; `UNIVERSE_TYPES = Set.of("symbols", "watchlist", "volume_top_n")`; topN>0 validation; RuleDefinitionUniverseTest 4/4 GREEN |
| 6 | BacktestService selects top-N volume symbols per date dynamically without look-ahead bias | VERIFIED | `resolveInitialSymbols()` loads all KOSPI 200 candidates via `symbolsByMarket()` (line 226); `resolveSymbolsForDate()` calls `marketData.topVolumeSymbols(date, topN)` per date (line 262); query uses only that date's market_bars; MarketBarRepositoryTopVolumeTest 4/4 GREEN |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/graphify/trading/backtest/BacktestService.java` | volumesBySymbol, resolveInitialSymbols, resolveSymbolsForDate | VERIFIED | All three present and substantive (307 lines) |
| `backend/src/main/resources/db/migration/V30__kospi200_universe.sql` | in_kospi200 column + KOSPI 200 seed data | VERIFIED | ALTER TABLE + index + UPDATE with 94 tickers (116 lines) |
| `backend/src/main/java/com/graphify/company/Company.java` | inKospi200 field, isInKospi200() getter | VERIFIED | `@Column(name="in_kospi200")` field line 48–49; getter line 106–108; setter line 154–156 |
| `backend/src/main/java/com/graphify/company/CompanyRepository.java` | findByInKospi200True() | VERIFIED | Derived query method present line 47 |
| `backend/src/main/java/com/graphify/market/MarketDataIngestionService.java` | ingestDailyForKospi200() | VERIFIED | Full implementation lines 73–88 with null-ticker guard and count logic |
| `backend/src/main/java/com/graphify/trading/rule/definition/RuleDefinition.java` | Universe with market/topN/additionalSymbols | VERIFIED | Record fields lines 22–27 |
| `backend/src/main/java/com/graphify/trading/rule/definition/RuleDefinitionValidator.java` | volume_top_n allowed | VERIFIED | `UNIVERSE_TYPES` line 21; validation branch lines 47–50 |
| `backend/src/main/java/com/graphify/trading/engine/MarketDataPort.java` | topVolumeSymbols(), symbolsByMarket() | VERIFIED | Both default methods lines 18–28 with LocalDate/int/String signatures |
| `backend/src/main/java/com/graphify/market/MarketBarRepository.java` | findTopVolumeSymbolsOnDate(), findDistinctKospi200Symbols() | VERIFIED | JPQL queries lines 23–47; inKospi200=true + volume IS NOT NULL + ORDER BY volume DESC |
| `backend/src/main/java/com/graphify/market/DbMarketDataAdapter.java` | implements topVolumeSymbols(), symbolsByMarket() | VERIFIED | Both @Override methods lines 45–52; delegates to repository with PageRequest.of(0, topN) |
| `backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java` | DATA-05 regression tests | VERIFIED | 2 tests, 0 failures |
| `backend/src/test/java/com/graphify/trading/engine/RuleEvaluatorVolumeTest.java` | VOLUME indicator unit tests | VERIFIED | 3 tests, 0 failures |
| `backend/src/test/java/com/graphify/company/CompanyEntityTest.java` | DATA-01 JPA mapping tests | VERIFIED | 4 tests, 0 failures |
| `backend/src/test/java/com/graphify/market/MarketDataIngestionServiceKospi200Test.java` | DATA-02 unit tests | VERIFIED | 5 tests, 0 failures |
| `backend/src/test/java/com/graphify/trading/rule/definition/RuleDefinitionUniverseTest.java` | DATA-03 deserialization tests | VERIFIED | 4 tests, 0 failures |
| `backend/src/test/java/com/graphify/market/MarketBarRepositoryTopVolumeTest.java` | DATA-04 top-volume query tests | VERIFIED | 4 tests, 0 failures |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `BacktestService.run()` | `RuleEvaluator.entryTriggered()` | `volumesBySymbol.get(symbol)` | WIRED | Line 119: `volumesBySymbol.getOrDefault(symbol, null)` → passed as `vols` arg at line 133 |
| `RuleEvaluator.operand()` | `volumes[i]` | `case VOLUME` | WIRED | Confirmed by RuleEvaluatorVolumeTest: null→false, 50000→true |
| `BacktestService.resolveInitialSymbols()` | `MarketDataPort.symbolsByMarket()` | `marketData.symbolsByMarket(market)` | WIRED | Line 226 |
| `BacktestService.resolveSymbolsForDate()` | `MarketDataPort.topVolumeSymbols()` | `marketData.topVolumeSymbols(date, topN)` | WIRED | Line 262 |
| `DbMarketDataAdapter.topVolumeSymbols()` | `MarketBarRepository.findTopVolumeSymbolsOnDate()` | `PageRequest.of(0, topN)` | WIRED | Line 46 |
| `DbMarketDataAdapter.symbolsByMarket()` | `MarketBarRepository.findDistinctKospi200Symbols()` | direct delegation | WIRED | Line 51 |
| `MarketBarRepository.findTopVolumeSymbolsOnDate()` | `Company.inKospi200` | `JOIN Company c ON c.ticker = b.symbol WHERE c.inKospi200 = true` | WIRED | Lines 26–28; confirmed by MarketBarRepositoryTopVolumeTest test 2 |
| `Company.java` | `companies.in_kospi200` | `@Column(name = "in_kospi200")` | WIRED | Line 48 |
| `CompanyRepository.findByInKospi200True()` | `Company.inKospi200` | Spring Data JPA derived query | WIRED | Line 47 |
| `MarketDataIngestionService.ingestDailyForKospi200()` | `CompanyRepository.findByInKospi200True()` | `companyRepository.findByInKospi200True()` | WIRED | Line 74 |
| `ingestDailyForKospi200()` | `ingestDaily(ticker)` | null-ticker guard + delegation | WIRED | Lines 77–82 |
| `RuleDefinitionValidator.validateUniverse()` | `UNIVERSE_TYPES` | `Set.of("symbols","watchlist","volume_top_n")` | WIRED | Line 21 + branch lines 47–50 |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| DATA-01 | 00-02 | KOSPI 200 종목 리스트 관리, in_kospi200 플래그 | SATISFIED | V30 migration, Company.inKospi200, CompanyRepository.findByInKospi200True(); 4 tests GREEN |
| DATA-02 | 00-03 | KOSPI 200 전체 종목 일봉 수집 | SATISFIED | ingestDailyForKospi200() iterates findByInKospi200True() results; 5 tests GREEN |
| DATA-03 | 00-04 | Universe volume_top_n 타입 + additionalSymbols 지원 | SATISFIED | Universe record extended; Validator allows volume_top_n; 4 deserialization tests GREEN |
| DATA-04 | 00-04 | 백테스트 날짜별 거래량 상위 N 동적 선정 | SATISFIED | resolveSymbolsForDate() per-date call; findTopVolumeSymbolsOnDate() date-scoped; 4 repository tests GREEN |
| DATA-05 | 00-01 | BacktestService volume null 버그 수정 | SATISFIED | volumesBySymbol populated from bars.get(i).volume(); no null passed to RuleEvaluator; 2+3=5 tests GREEN |

### Anti-Patterns Found

None. No TODO/FIXME/PLACEHOLDER/stub returns found in any of the 9 scanned main-source files.

### Human Verification Required

None required for automated checks. The following items are observable only at runtime with real DB data but are not blockers for goal verification:

1. **KOSPI 200 seed data application at startup**
   - Test: Run `./gradlew bootRun` against a real PostgreSQL DB; check `SELECT COUNT(*) FROM companies WHERE in_kospi200 = TRUE`.
   - Expected: Count > 0 (if KOSPI companies already exist in the companies table with matching tickers and market values).
   - Why human: V30 UPDATE is conditional on existing companies table data; test DB uses `create-drop` so seed data is not validated by automated tests.

2. **volume_top_n backtest end-to-end with real market data**
   - Test: POST to `/api/backtest` with `{"type":"volume_top_n","market":"KOSPI","topN":10}` universe after running `POST /internal/market/ingest-daily-kospi200`.
   - Expected: 200 OK with `trades` array (may be empty if no entry conditions met, but no ERR_BACKTEST_002 error).
   - Why human: Requires real Yahoo Finance data to be ingested first.

### Test Suite Summary

| Test Class | Tests | Failures | Errors | Requirement |
|-----------|-------|----------|--------|-------------|
| BacktestServiceVolumeTest | 2 | 0 | 0 | DATA-05 |
| RuleEvaluatorVolumeTest | 3 | 0 | 0 | DATA-05 |
| CompanyEntityTest | 4 | 0 | 0 | DATA-01 |
| MarketDataIngestionServiceKospi200Test | 5 | 0 | 0 | DATA-02 |
| RuleDefinitionUniverseTest | 4 | 0 | 0 | DATA-03 |
| MarketBarRepositoryTopVolumeTest | 4 | 0 | 0 | DATA-04 |
| **Total (phase-specific)** | **22** | **0** | **0** | |
| Full suite (all 11 classes) | 29 | 0 | 0 | |

`./gradlew clean test` — BUILD SUCCESSFUL in 16s

### Gaps Summary

No gaps. All 6 observable truths verified, all 16 artifacts exist and are substantive and wired, all 5 requirements satisfied, all 22 phase-specific tests GREEN, full suite of 29 tests GREEN.

---

_Verified: 2026-06-20T18:56:00+09:00_
_Verifier: Claude (gsd-verifier)_

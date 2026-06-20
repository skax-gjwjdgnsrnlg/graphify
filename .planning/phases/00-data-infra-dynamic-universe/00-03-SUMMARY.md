---
phase: 00-data-infra-dynamic-universe
plan: 03
subsystem: market-data
tags: [kospi200, ingestion, tdd, mockito]
dependency_graph:
  requires: [in_kospi200 flag (00-02), findByInKospi200True() (00-02), ingestDaily() (existing)]
  provides: [ingestDailyForKospi200(), findTopVolumeSymbolsOnDate() (DATA-04 unblocked), RuleDefinition.Universe market/topN/additionalSymbols (DATA-03 unblocked)]
  affects: [DATA-02 daily bar collection, DATA-04 dynamic universe selection]
tech_stack:
  added: []
  patterns: [Mockito Spy + doReturn for method stub, @ExtendWith(MockitoExtension.class)]
key_files:
  created:
    - backend/src/test/java/com/graphify/market/MarketDataIngestionServiceKospi200Test.java
  modified:
    - backend/src/main/java/com/graphify/market/MarketDataIngestionService.java
    - backend/src/main/java/com/graphify/market/MarketBarRepository.java
    - backend/src/main/java/com/graphify/trading/rule/definition/RuleDefinition.java
decisions:
  - "No @Scheduled annotation added — Phase 2 will add scheduler; current trigger is HTTP endpoint (InternalMarketController) per RESEARCH.md decision"
  - "Mockito Spy + doReturn pattern used to stub ingestDaily() in count-verification tests (Tests 4 & 5) — ingestDaily() calls external Yahoo API so cannot use @Mock alone"
  - "Rule 3 auto-fix: added findTopVolumeSymbolsOnDate() to MarketBarRepository and market/topN/additionalSymbols to RuleDefinition.Universe to unblock compilation from pre-existing RED stubs (00-04/00-05 forward)"
metrics:
  duration: 2m 25s
  completed: 2026-06-20
  tasks_completed: 2
  files_changed: 4
---

# Phase 0 Plan 03: ingestDailyForKospi200() 메서드 추가 Summary

**One-liner:** MarketDataIngestionService에 `ingestDailyForKospi200()` 추가 — `findByInKospi200True()` 결과를 순회하며 ticker=null 건너뜀, 5개 Mockito 단위 테스트 GREEN.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| RED | MarketDataIngestionServiceKospi200Test 작성 (실패 테스트) | 4256718 | MarketDataIngestionServiceKospi200Test.java |
| GREEN | ingestDailyForKospi200() 구현 + 블로킹 이슈 해결 | d3695e7 | MarketDataIngestionService.java, MarketBarRepository.java, RuleDefinition.java |

## What Was Built

- **MarketDataIngestionService.ingestDailyForKospi200()**: `companyRepository.findByInKospi200True()`로 KOSPI 200 종목 전체 조회 → ticker=null 종목 skip → `ingestDaily(ticker)` 호출 → >0 반환 종목만 카운트 → 결과 반환. `@Scheduled` 없음 (Phase 2 예정).
- **MarketDataIngestionServiceKospi200Test**: 5개 단위 테스트 (MockitoExtension + Spy pattern)
  - Test 1: 2개 종목 → ingestDaily() 2번 호출
  - Test 2: ticker=null → skip (API 호출 없음)
  - Test 3: 빈 리스트 → 반환값 0
  - Test 4: ingestDaily() > 0 → 카운트 포함
  - Test 5: ingestDaily() == 0 → 카운트 미포함

## Verification

```
./gradlew test --tests "com.graphify.market.MarketDataIngestionServiceKospi200Test"
BUILD SUCCESSFUL — 5 tests, 0 failures

./gradlew test --tests "com.graphify.market.*"
BUILD SUCCESSFUL — all market tests GREEN
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing RED stubs in 00-04/00-05 blocked compileTestJava**
- **Found during:** GREEN phase — test compilation
- **Issue:** `RuleDefinitionUniverseTest` referenced `universe.topN()` and `universe.additionalSymbols()` methods not yet in `RuleDefinition.Universe` record. `MarketBarRepositoryTopVolumeTest` referenced `barRepository.findTopVolumeSymbolsOnDate()` not yet in `MarketBarRepository`. Both were forward RED stubs written by earlier plans, blocking all test compilation.
- **Fix:**
  - Extended `RuleDefinition.Universe` record with `market`, `topN`, `additionalSymbols` fields (DATA-03 contract)
  - Added `findTopVolumeSymbolsOnDate()` JPQL query to `MarketBarRepository` (DATA-04 contract) — filters `in_kospi200=true`, excludes `volume IS NULL`, orders by `volume DESC`, uses `Pageable` for topN
  - Added `findDistinctKospi200Symbols()` (linter addition, also needed for DATA-04)
- **Files modified:** `MarketBarRepository.java`, `RuleDefinition.java`
- **Commit:** d3695e7

## Self-Check: PASSED

| Item | Status |
|------|--------|
| MarketDataIngestionService.java (ingestDailyForKospi200) | FOUND |
| MarketDataIngestionServiceKospi200Test.java | FOUND |
| MarketBarRepository.java (findTopVolumeSymbolsOnDate) | FOUND |
| RuleDefinition.java (topN/additionalSymbols) | FOUND |
| 00-03-SUMMARY.md | FOUND |
| Commit 4256718 (RED) | FOUND |
| Commit d3695e7 (GREEN) | FOUND |

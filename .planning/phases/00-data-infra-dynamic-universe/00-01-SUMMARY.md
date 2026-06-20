---
phase: 00-data-infra-dynamic-universe
plan: "01"
subsystem: trading-backtest-engine
tags: [bug-fix, tdd, volume, backtest, data-pipeline]
dependency_graph:
  requires: []
  provides: [BacktestService-volume-fix, RuleEvaluatorVolumeTest, BacktestServiceVolumeTest]
  affects: [BacktestService, RuleEvaluator]
tech_stack:
  added: []
  patterns: [TDD RED-GREEN, Mockito Spy + ArgumentCaptor]
key_files:
  created:
    - backend/src/test/java/com/graphify/trading/engine/RuleEvaluatorVolumeTest.java
    - backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java
  modified:
    - backend/src/main/java/com/graphify/trading/backtest/BacktestService.java
decisions:
  - "BacktestService는 volumesBySymbol Map<String, Double[]>를 선언하고 symbol 루프 내 closes[] 추출과 동일한 for 루프에서 volumes[]를 추출한다"
  - "평가 루프에서 null 하드코딩 대신 volumesBySymbol.getOrDefault(symbol, null)을 사용해 volumes를 RuleEvaluator에 전달한다"
metrics:
  duration: "4 minutes"
  completed_date: "2026-06-20"
  tasks_completed: 2
  files_changed: 3
---

# Phase 0 Plan 01: BacktestService Volume Null Bug Fix Summary

**One-liner:** BacktestService가 closes[]와 나란히 volumes[] (Double[] nullable boxed)를 추출해 RuleEvaluator에 전달하도록 수정 — VOLUME 지표 기반 진입/청산 룰이 정상 동작.

## What Was Built

TDD RED-GREEN 방식으로 BacktestService의 volume null 전달 버그(DATA-05)를 수정했다.

**버그 원인:** BacktestService.run()이 symbol 루프에서 closes[]만 추출하고 volumes[]를 추출하지 않아, exitTriggered/entryTriggered 호출 시 항상 `null`을 전달했다. RuleEvaluator의 VOLUME case는 `volumes != null && volumes[i] != null`일 때만 실제 값을 반환하므로 VOLUME 조건이 항상 false(NaN fallback)였다.

**수정 내용 (BacktestService.java):**
1. `Map<String, Double[]> volumesBySymbol = new LinkedHashMap<>()` 선언 추가
2. symbol 루프 내 for 루프에 `volumes[i] = bars.get(i).volume()` 추출 추가
3. 평가 루프에서 `null` → `volumesBySymbol.getOrDefault(symbol, null)` 대체

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| RED  | 실패 테스트 작성 | 7029a88 | RuleEvaluatorVolumeTest.java, BacktestServiceVolumeTest.java |
| GREEN | BacktestService 수정 | bb66244 | BacktestService.java |

## Test Results

- `RuleEvaluatorVolumeTest` (3 tests): volumes=[50000.0]일 때 VOLUME > 30000 → true, volumes=null → false, volumes=[10000.0] → false
- `BacktestServiceVolumeTest` (2 tests): volume=50000 봉에서 BUY 신호 생성 확인, ArgumentCaptor로 non-null Double[] 전달 검증
- 전체 5 tests GREEN

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Pre-existing CompanyEntityTest compilation error**
- **Found during:** RED test 실행 시
- **Issue:** CompanyEntityTest가 `setInKospi200`, `findByInKospi200True` 심볼을 찾지 못해 컴파일 실패 (stale Gradle cache 문제)
- **Fix:** `./gradlew clean` 후 재실행으로 캐시 무효화 — 실제 코드 수정 불필요 (Company.java와 CompanyRepository는 이미 올바른 구현을 포함)
- **Files modified:** 없음 (빌드 캐시 재생성으로 해결)

## Decisions Made

1. `volumesBySymbol`의 타입은 `Map<String, Double[]>` (nullable boxed) — `Bar.volume()`이 `Double` nullable이므로 primitive `double[]`로 선언하면 NPE 발생
2. `getOrDefault(symbol, null)` 패턴 사용 — 데이터 없는 봉은 null volumes를 그대로 전달해 RuleEvaluator의 NaN fallback이 작동하도록 유지

## Self-Check: PASSED

Files exist:
- FOUND: backend/src/main/java/com/graphify/trading/backtest/BacktestService.java
- FOUND: backend/src/test/java/com/graphify/trading/engine/RuleEvaluatorVolumeTest.java
- FOUND: backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java

Commits:
- FOUND: 7029a88 (RED tests)
- FOUND: bb66244 (GREEN fix)

Tests: 5/5 GREEN

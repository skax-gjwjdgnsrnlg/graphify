---
phase: 02-realtime-data-scheduler
verified: 2026-06-21T00:00:00Z
status: passed
score: 4/4 must-haves verified
---

# Phase 2: 실시간 데이터 수집 & 스케줄러 인프라 Verification Report

**Phase Goal:** KRX 장 중(09:00-15:30 KST, 거래일)에만 5분마다 활성 종목 분봉을 수집하고, 다중 인스턴스 환경에서 이중 수집이 발생하지 않도록 ShedLock 분산 잠금을 적용한다.
**Verified:** 2026-06-21
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth                                                                                       | Status     | Evidence                                                                                                                                                                |
| --- | ------------------------------------------------------------------------------------------- | ---------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | 장 중 5분마다 활성 종목의 인트라데이 봉이 적재된다 (LIVE-01)                                  | ✓ VERIFIED | `LiveDataScheduler.collectLiveData()` @Scheduled(cron="0 */5 9-15 * * MON-FRI", zone="Asia/Seoul") + 15:30 guard (line 66) + loop `ingestIntraday(symbol,"5m","1d")` (line 86) |
| 2   | KRX 공휴일/주말에는 수집·평가가 실행되지 않는다 (LIVE-02)                                    | ✓ VERIFIED | `KrxMarketCalendar.isTradingDay()` rejects Sat/Sun without DB call, rejects holidays via `existsByHolidayDate`; scheduler Guard 2 (line 72); V31 pre-inserts 16 KRX 2026 holidays |
| 3   | 다중 인스턴스 환경에서 분산 잠금으로 수집이 1회만 실행된다 (LIVE-03)                          | ✓ VERIFIED | `@SchedulerLock(name="liveDataIngestion", lockAtMostFor="4m", lockAtLeastFor="1m")` (line 61); `SchedulerConfig` @EnableSchedulerLock + JdbcTemplateLockProvider.usingDbTime(); V31 creates `shedlock` table |
| 4   | 최신 봉이 10분 이상 오래되면 WARNING 기록 후 평가 건너뜀 (LIVE-04)                            | ✓ VERIFIED | `checkStaleness()` compares maxTs against now-10m threshold, `log.warn` on stale (lines 100-107); `findMaxTsBySymbolAndInterval` JPQL MAX(ts) query                      |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact                                                | Expected                                               | Status     | Details                                                            |
| ------------------------------------------------------- | ------------------------------------------------------ | ---------- | ----------------------------------------------------------------- |
| `db/migration/V31__live_scheduler_infra.sql`            | DDL for 3 tables + 2026 KRX holidays                   | ✓ VERIFIED | market_holidays + shedlock + paper_live_symbols; 16 holidays INSERT ON CONFLICT |
| `config/SchedulerConfig.java`                           | @EnableScheduling + @EnableSchedulerLock + LockProvider | ✓ VERIFIED | All annotations present; JdbcTemplateLockProvider.usingDbTime()    |
| `market/KrxMarketCalendar.java`                         | isTradingDay() weekend + holiday guard                 | ✓ VERIFIED | Weekend short-circuit (no DB call), holiday via repository         |
| `market/MarketHoliday.java` + Repository                | JPA entity + existsByHolidayDate                       | ✓ VERIFIED | Maps to market_holidays; existsByHolidayDate present               |
| `market/LiveDataScheduler.java`                         | @Scheduled + @SchedulerLock collection adapter         | ✓ VERIFIED | 3 guards + ingestion loop + staleness check; WIRED                 |
| `trading/rule/PaperLiveSymbolService.java`              | activeSymbolsUnion / assignSymbols / deactivateRule    | ✓ VERIFIED | DISTINCT union via findDistinctSymbolsByRuleIds filtered to PAPER_LIVE |
| `trading/rule/PaperLiveSymbol.java` + Repository        | JPA entity + repo queries                              | ✓ VERIFIED | Maps to paper_live_symbols; findByRuleId/deleteByRuleId/findDistinctSymbolsByRuleIds |
| `market/MarketBarIntradayRepository.java`               | findMaxTsBySymbolAndInterval JPQL                      | ✓ VERIFIED | `SELECT MAX(m.ts) ... WHERE symbol AND interval` returns Optional<Instant> |
| `trading/engine/MarketDataPort.java`                    | recentIntradayBars() default method                    | ✓ VERIFIED | Default returns List.of(); overridden in DbMarketDataAdapter       |
| `market/DbMarketDataAdapter.java`                       | override recentIntradayBars()                          | ✓ VERIFIED | Delegates to findBySymbolAndIntervalOrderByTsAsc(symbol,"5m")      |
| Test classes (3)                                        | Unit coverage LIVE-01..04                              | ✓ VERIFIED | KrxMarketCalendarTest(5), LiveDataSchedulerTest(5), MaxTsTest(3); market suite exit 0 |

### Key Link Verification

| From                              | To                                            | Via                               | Status  | Details                                              |
| --------------------------------- | --------------------------------------------- | --------------------------------- | ------- | ---------------------------------------------------- |
| SchedulerConfig                   | shedlock table (PostgreSQL)                   | JdbcTemplateLockProvider          | ✓ WIRED | LockProvider bean with DataSource injection          |
| KrxMarketCalendar                 | market_holidays table                         | existsByHolidayDate()             | ✓ WIRED | Repository call in isTradingDay()                    |
| LiveDataScheduler                 | KrxMarketCalendar.isTradingDay()              | constructor injection, Guard 2    | ✓ WIRED | `calendar.isTradingDay(now.toLocalDate())` line 72   |
| LiveDataScheduler                 | ingestIntraday(symbol,"5m","1d")              | loop over activeSymbolsUnion()    | ✓ WIRED | line 86                                              |
| LiveDataScheduler                 | findMaxTsBySymbolAndInterval()                | staleness check per symbol        | ✓ WIRED | line 102                                             |
| LiveDataScheduler                 | activeSymbolsUnion()                           | symbolService injection           | ✓ WIRED | line 78, Guard 3                                     |
| DbMarketDataAdapter               | MarketDataPort.recentIntradayBars()           | @Override default method          | ✓ WIRED | line 58                                              |

### Requirements Coverage

| Requirement | Source Plan  | Description                                                          | Status      | Evidence                                       |
| ----------- | ------------ | ------------------------------------------------------------------- | ----------- | ---------------------------------------------- |
| LIVE-01     | 02-01, 02-02 | 장 중 5분마다 활성 종목 인트라데이 봉 수집                            | ✓ SATISFIED | @Scheduled cron + 15:30 guard + ingestion loop |
| LIVE-02     | 02-01        | KRX 공휴일 유지, 장 외 평가 건너뜀                                    | ✓ SATISFIED | KrxMarketCalendar + V31 holiday data + Guard 2 |
| LIVE-03     | 02-01, 02-02 | 다중 인스턴스 분산 잠금(ShedLock)                                     | ✓ SATISFIED | @SchedulerLock + LockProvider + shedlock table |
| LIVE-04     | 02-02        | 수집 후 10분 이상 오래된 봉 평가 건너뜀 + 경고                        | ✓ SATISFIED | checkStaleness() WARNING + findMaxTs query     |

No orphaned requirements — all four IDs declared in plan frontmatter map to REQUIREMENTS.md (lines 26-29, 127-130, all marked Complete).

### Anti-Patterns Found

None. No TODO/FIXME/PLACEHOLDER/stub patterns in any new file.

### Human Verification Required

None blocking. Optional runtime confirmations (not required for goal verification):
- Live cron firing during actual KRX hours (09:00-15:30 KST) inserting rows — deterministic behavior verified via unit guards; `ZonedDateTime.now()` is a static call not mockable, so the 15:30 boundary is validated by code inspection rather than a clock-injected test (documented deferral in plan 02-02).
- Multi-instance lock contention against a real PostgreSQL `shedlock` row.

### Gaps Summary

No gaps. All four observable truths verified at exists/substantive/wired levels. All key links connected. All four requirements (LIVE-01..04) satisfied with implementation evidence and unit test coverage; the market test suite passes (exit 0).

**Notable (non-blocking):** `PaperLiveSymbolService.assignSymbols()`/`deactivateRule()` have no production callers yet — the rule-promotion lifecycle that populates `paper_live_symbols` is expected in a later phase. This does not affect Phase 2 goal: the critical `activeSymbolsUnion()` read path feeding the scheduler is fully wired. Additionally, `LiveDataScheduler` already invokes `LiveEvaluationService.evaluateTick()` (Phase 3 work present in the codebase); this extends beyond Phase 2 scope but does not break Phase 2 behavior and compiles cleanly (the dependency class exists).

---

_Verified: 2026-06-21_
_Verifier: Claude (gsd-verifier)_

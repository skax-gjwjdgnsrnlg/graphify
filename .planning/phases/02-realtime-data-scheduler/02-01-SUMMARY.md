---
phase: 02-realtime-data-scheduler
plan: 01
subsystem: scheduler-infra
tags: [shedlock, flyway, krx-calendar, distributed-lock]
dependency_graph:
  requires: []
  provides: [KrxMarketCalendar, SchedulerConfig, LockProvider, V31-migration]
  affects: [02-02-intraday-tick-scheduler]
tech_stack:
  added:
    - shedlock-spring:7.7.0
    - shedlock-provider-jdbc-template:7.7.0
  patterns:
    - JdbcTemplateLockProvider with usingDbTime()
    - Flyway ON CONFLICT DO NOTHING seed data
    - Mockito @InjectMocks + @Mock for Spring service unit tests
key_files:
  created:
    - backend/build.gradle.kts (modified — ShedLock deps added)
    - backend/src/main/resources/db/migration/V31__live_scheduler_infra.sql
    - backend/src/main/java/com/graphify/config/SchedulerConfig.java
    - backend/src/main/java/com/graphify/market/MarketHoliday.java
    - backend/src/main/java/com/graphify/market/MarketHolidayRepository.java
    - backend/src/main/java/com/graphify/market/KrxMarketCalendar.java
    - backend/src/test/java/com/graphify/market/KrxMarketCalendarTest.java
  modified: []
decisions:
  - "ShedLock 7.7.0 with JdbcTemplateLockProvider + usingDbTime() — uses DB clock to avoid clock skew between instances"
  - "V31 migration seeds 2026 KRX holidays with ON CONFLICT DO NOTHING — safe for re-runs"
  - "KrxMarketCalendar short-circuits on weekend before hitting DB — avoids unnecessary repository calls"
metrics:
  duration: "2 minutes"
  completed_date: "2026-06-21"
  tasks_completed: 2
  files_created: 7
---

# Phase 02 Plan 01: Live Scheduler Infrastructure Summary

**One-liner:** ShedLock 7.7.0 distributed lock via JdbcTemplateLockProvider, V31 Flyway migration with market_holidays/shedlock/paper_live_symbols tables and 2026 KRX holidays, and KrxMarketCalendar weekend+holiday guard with 5 passing Mockito unit tests.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | V31 migration + ShedLock dependency + SchedulerConfig | 1248228 | build.gradle.kts, V31__live_scheduler_infra.sql, SchedulerConfig.java |
| 2 (RED) | KrxMarketCalendarTest (failing tests) | 48f047d | KrxMarketCalendarTest.java |
| 2 (GREEN) | MarketHoliday entity + repository + KrxMarketCalendar | 4d72952 | MarketHoliday.java, MarketHolidayRepository.java, KrxMarketCalendar.java |

## What Was Built

### V31 Flyway Migration
Three tables created:
- `market_holidays` — stores KRX public holidays; 16 2026 dates pre-seeded
- `shedlock` — PostgreSQL distributed lock table used by ShedLock
- `paper_live_symbols` — maps paper-live trading rules to their active symbols (FK to trading_rules ON DELETE CASCADE)

### SchedulerConfig
`@Configuration @EnableScheduling @EnableSchedulerLock(defaultLockAtMostFor = "4m")` with a `LockProvider` bean using `JdbcTemplateLockProvider.Configuration.builder().usingDbTime()`. No new infrastructure needed — uses existing DataSource.

### KrxMarketCalendar
`@Service` with `isTradingDay(LocalDate)`:
- Returns false immediately for Saturday/Sunday (no DB call)
- Returns false if date present in `market_holidays` via `existsByHolidayDate()`
- Returns true for weekdays not in holidays

## Verification Results

```
BUILD SUCCESSFUL in 13s
5 actionable tasks: 4 executed, 1 up-to-date
```

All 5 `KrxMarketCalendarTest` tests pass:
- `weekday_not_holiday_is_trading_day` — true
- `saturday_is_not_trading_day` — false, no repo interaction
- `sunday_is_not_trading_day` — false, no repo interaction
- `weekday_in_market_holidays_is_not_trading_day` — false
- `weekday_not_in_market_holidays_is_trading_day` — true

## Deviations from Plan

None — plan executed exactly as written.

## Self-Check: PASSED

All 6 created files confirmed on disk. All 3 commits (1248228, 48f047d, 4d72952) confirmed in git log.

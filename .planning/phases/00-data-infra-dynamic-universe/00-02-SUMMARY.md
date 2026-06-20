---
phase: 00-data-infra-dynamic-universe
plan: 02
subsystem: company-data
tags: [kospi200, jpa, flyway, tdd]
dependency_graph:
  requires: []
  provides: [in_kospi200 flag, findByInKospi200True(), V30 migration]
  affects: [DATA-02 daily bar collection, DATA-04 dynamic universe selection]
tech_stack:
  added: [H2 testRuntimeOnly for @DataJpaTest]
  patterns: [Spring Data JPA derived query, @DataJpaTest slice with @AutoConfigureTestDatabase]
key_files:
  created:
    - backend/src/main/resources/db/migration/V30__kospi200_universe.sql
    - backend/src/test/java/com/graphify/company/CompanyEntityTest.java
    - backend/src/test/resources/application.properties
  modified:
    - backend/src/main/java/com/graphify/company/Company.java
    - backend/src/main/java/com/graphify/company/CompanyRepository.java
    - backend/build.gradle.kts
decisions:
  - "@DataJpaTest with @AutoConfigureTestDatabase(replace=ANY) + @TestPropertySource to disable Flyway — avoids PostgreSQL-specific SQL syntax in H2 test context"
  - "H2 added as testRuntimeOnly (not testImplementation) — runtime-only since tests compile against JPA abstractions"
  - "test/resources/application.properties created but @TestPropertySource on class takes precedence for @DataJpaTest slice"
metrics:
  duration: 3m 5s
  completed: 2026-06-20
  tasks_completed: 2
  files_changed: 6
---

# Phase 0 Plan 02: KOSPI 200 마스터 데이터 (in_kospi200 컬럼 + 초기 데이터) Summary

**One-liner:** Flyway V30 migration adds `in_kospi200 BOOLEAN` to companies table, Company JPA entity exposes `isInKospi200()`/`findByInKospi200True()`, verified by 4 @DataJpaTest tests (all GREEN).

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| RED | CompanyEntityTest 작성 (실패 테스트) | 0056be2 | CompanyEntityTest.java, application.properties, build.gradle.kts |
| GREEN | in_kospi200 구현 (SQL + JPA + Repository) | aea7497 | V30__kospi200_universe.sql, Company.java, CompanyRepository.java, CompanyEntityTest.java (annotation fix) |

## What Was Built

- **V30__kospi200_universe.sql**: `ALTER TABLE companies ADD COLUMN IF NOT EXISTS in_kospi200 BOOLEAN NOT NULL DEFAULT FALSE` + partial index on `in_kospi200 = TRUE` + `UPDATE` marking ~100 KOSPI 200 tickers with `in_kospi200 = TRUE` where `market IN ('KOSPI', 'KRX', 'KSC')`
- **Company.java**: `inKospi200` boolean field with `@Column(name = "in_kospi200", nullable = false)`, `isInKospi200()` getter, `setInKospi200()` setter
- **CompanyRepository.java**: `List<Company> findByInKospi200True()` Spring Data derived query
- **CompanyEntityTest.java**: 4 @DataJpaTest tests covering default value (false), findByInKospi200True inclusion, exclusion, and persisted getter

## Verification

```
./gradlew test --tests "com.graphify.company.*"
BUILD SUCCESSFUL — 4 tests, 0 failures
```

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical Dependency] H2 not present for @DataJpaTest**
- **Found during:** GREEN phase — test execution
- **Issue:** Project has no H2 dependency; @DataJpaTest requires an in-memory DB for slice testing without a running PostgreSQL
- **Fix:** Added `testRuntimeOnly("com.h2database:h2")` to build.gradle.kts
- **Files modified:** backend/build.gradle.kts
- **Commit:** aea7497

**2. [Rule 1 - Bug] Flyway still running in @DataJpaTest despite application.properties**
- **Found during:** First GREEN test run — Flyway tried to run V1__baseline.sql against H2, which uses PostgreSQL-specific syntax (BIGSERIAL, TIMESTAMPTZ, etc.)
- **Issue:** `@DataJpaTest` slice context does not honor `src/test/resources/application.properties` for `spring.flyway.enabled=false`; Flyway auto-configuration still matched
- **Fix:** Added `@AutoConfigureTestDatabase(replace = Replace.ANY)` and `@TestPropertySource(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})` directly on the test class
- **Files modified:** backend/src/test/java/com/graphify/company/CompanyEntityTest.java
- **Commit:** aea7497

## Self-Check: PASSED

| Item | Status |
|------|--------|
| V30__kospi200_universe.sql | FOUND |
| Company.java (inKospi200) | FOUND |
| CompanyRepository.java (findByInKospi200True) | FOUND |
| CompanyEntityTest.java | FOUND |
| 00-02-SUMMARY.md | FOUND |
| Commit 0056be2 (RED) | FOUND |
| Commit aea7497 (GREEN) | FOUND |

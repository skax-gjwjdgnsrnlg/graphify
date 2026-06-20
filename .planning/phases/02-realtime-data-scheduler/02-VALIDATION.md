---
phase: 2
slug: realtime-data-scheduler
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-21
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (Spring Boot Test 3.4.5) |
| **Config file** | `backend/src/test/resources/application.properties` |
| **Quick run command** | `./gradlew test --tests "com.graphify.market.*" --tests "com.graphify.trading.rule.PaperLiveSymbol*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "com.graphify.market.*"`
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | LIVE-01, LIVE-02 | unit | `./gradlew test --tests "*.KrxMarketCalendarTest"` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | LIVE-01 | unit | `./gradlew test --tests "*.LiveDataSchedulerTest"` | ❌ W0 | ⬜ pending |
| 2-01-03 | 01 | 1 | LIVE-03 | unit (reflection) | `./gradlew test --tests "*.LiveDataSchedulerTest"` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 2 | LIVE-04 | unit | `./gradlew test --tests "*.LiveDataSchedulerTest"` | ❌ W0 | ⬜ pending |
| 2-02-02 | 02 | 2 | LIVE-04 | unit (H2) | `./gradlew test --tests "*.MarketBarIntradayRepositoryMaxTsTest"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/graphify/market/KrxMarketCalendarTest.java` — covers LIVE-01 (평일/주말), LIVE-02 (공휴일)
- [ ] `backend/src/test/java/com/graphify/market/LiveDataSchedulerTest.java` — covers LIVE-01 (15:30 guard), LIVE-03 (@SchedulerLock annotation), LIVE-04 (staleness warning)
- [ ] `backend/src/test/java/com/graphify/market/MarketBarIntradayRepositoryMaxTsTest.java` — covers LIVE-04 JPQL (H2 @DataJpaTest)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 장 중 5분마다 실제 분봉 수집 확인 | LIVE-01 | 실시간 스케줄러는 장 중 실제 실행 필요 | 장 중 애플리케이션 실행 후 market_bars_intraday 테이블에 5분 간격 레코드 적재 확인 |
| ShedLock 다중 인스턴스 이중 실행 차단 | LIVE-03 | 다중 인스턴스 환경 시뮬레이션 필요 | 두 인스턴스 동시 실행 시 shedlock 테이블에 잠금 레코드 확인 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending

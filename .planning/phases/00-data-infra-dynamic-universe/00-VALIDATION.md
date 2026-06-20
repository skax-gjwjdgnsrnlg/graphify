---
phase: 0
slug: data-infra-dynamic-universe
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-20
---

# Phase 0 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (기존 사용 중) |
| **Config file** | `backend/src/test/java/com/graphify/` |
| **Quick run command** | `./gradlew test --tests "com.graphify.trading.*" --tests "com.graphify.market.*" --tests "com.graphify.company.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60 seconds |

---

## Sampling Rate

- **After every task commit:** Run quick run command above
- **After every plan wave:** Run `./gradlew test`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 00-01-* | 00-01 | 1 | DATA-05 | unit | `./gradlew test --tests "com.graphify.trading.backtest.BacktestServiceVolumeTest"` | ❌ Wave 0 | ⬜ pending |
| 00-01-* | 00-01 | 1 | DATA-05 | unit | `./gradlew test --tests "com.graphify.trading.engine.RuleEvaluatorVolumeTest"` | ❌ Wave 0 | ⬜ pending |
| 00-02-* | 00-02 | 1 | DATA-01 | unit | `./gradlew test --tests "com.graphify.company.CompanyEntityTest"` | ❌ Wave 0 | ⬜ pending |
| 00-03-* | 00-03 | 2 | DATA-02 | unit | `./gradlew test --tests "com.graphify.market.MarketDataIngestionServiceKospi200Test"` | ❌ Wave 0 | ⬜ pending |
| 00-04-* | 00-04 | 2 | DATA-03 | unit | `./gradlew test --tests "com.graphify.trading.rule.definition.RuleDefinitionUniverseTest"` | ❌ Wave 0 | ⬜ pending |
| 00-04-* | 00-04 | 2 | DATA-04 | unit | `./gradlew test --tests "com.graphify.market.MarketBarRepositoryTopVolumeTest"` | ❌ Wave 0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/graphify/trading/backtest/BacktestServiceVolumeTest.java` — DATA-05 volume 전달 검증
- [ ] `backend/src/test/java/com/graphify/trading/engine/RuleEvaluatorVolumeTest.java` — VOLUME 지표 조건 평가
- [ ] `backend/src/test/java/com/graphify/trading/rule/definition/RuleDefinitionUniverseTest.java` — volume_top_n JSON 역직렬화
- [ ] `backend/src/test/java/com/graphify/market/MarketBarRepositoryTopVolumeTest.java` — 날짜별 거래량 상위 N 쿼리
- [ ] `backend/src/test/java/com/graphify/company/CompanyEntityTest.java` — inKospi200 필드 JPA 매핑
- [ ] `backend/src/test/java/com/graphify/market/MarketDataIngestionServiceKospi200Test.java` — KOSPI 200 종목만 처리

---

## Key Validation Scenarios

**DATA-05 (가장 중요 — 버그 수정 검증):**
```
Given: market_bars에 volume=50000인 봉이 있는 종목
When:  VOLUME > 30000 진입 룰로 백테스트 실행
Then:  해당 봉에서 진입 신호 발생 (현재 버그: null volumes로 항상 false)
```

**DATA-04 (동적 유니버스 핵심):**
```
Given: 5개 종목 market_bars, 날짜 2024-01-15, volume [100, 300, 200, 400, 150]
When:  findTopVolumeSymbolsOnDate(2024-01-15, top=3) 호출
Then:  [종목B(400), 종목D(300), 종목C(200)] 순 반환
```

**DATA-03 (Universe JSON 역직렬화):**
```
Given: {"type":"volume_top_n","market":"KOSPI","topN":10,"additionalSymbols":["005930"]}
When:  objectMapper.readValue(json, RuleDefinition.class)
Then:  universe.type()="volume_top_n", topN()=10, additionalSymbols()=["005930"]
```

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| KOSPI 200 종목 초기 데이터 삽입 완료 확인 | DATA-01 | DB 상태 확인 | `SELECT COUNT(*) FROM companies WHERE in_kospi200 = TRUE` → 약 200 |
| market_bars에 KOSPI 200 일봉 적재 확인 | DATA-02 | 실제 Yahoo API 호출 | `SELECT COUNT(DISTINCT symbol) FROM market_bars` → ≥ 200 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending

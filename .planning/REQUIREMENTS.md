# Requirements: Graphify 모의투자 고도화 & 토스증권 연동

**Defined:** 2026-06-20
**Core Value:** 룰 기반 전략을 백테스트로 검증 → 실시간 모의 실행으로 성과 확인 → 토스증권 실계좌로 승격하는 일관된 파이프라인

---

## v1 Requirements

### 데이터 인프라 & 동적 유니버스 (DATA)

- [x] **DATA-01**: 시스템은 KOSPI 200 종목 리스트를 관리하며, companies 테이블에 `in_kospi200` 플래그로 표시한다
- [ ] **DATA-02**: 시스템은 KOSPI 200 전체 종목의 2년치 일봉 OHLCV를 market_bars 테이블에 수집·유지한다 (기존 룰 명시 종목 한정 수집을 확장)
- [ ] **DATA-03**: `RuleDefinition.Universe`는 `"volume_top_n"` 타입과 선택적 `additionalSymbols`를 지원한다 (예: `{"type":"volume_top_n","market":"KOSPI","topN":10,"additionalSymbols":[]}`)
- [ ] **DATA-04**: 백테스트 시 `volume_top_n` 유니버스는 각 거래일별로 market_bars의 KOSPI 종목 중 거래량 상위 10종목을 동적으로 선정한다 (날짜마다 유니버스가 달라짐)
- [x] **DATA-05**: `BacktestService`의 volume null 전달 버그를 수정한다 — 이미 DB에 저장된 volume 값이 `RuleEvaluator`에 올바르게 전달되어야 한다

### 백테스트 시각화 (CHART)

- [ ] **CHART-01**: 사용자는 백테스트 결과 페이지에서 수익 곡선(equity curve) 라인 차트를 볼 수 있다
- [ ] **CHART-02**: 사용자는 백테스트 결과에서 드로우다운 구간을 수익 곡선 위에 음영으로 확인할 수 있다
- [ ] **CHART-03**: 사용자는 백테스트 결과에서 Sharpe Ratio, Sortino Ratio, Profit Factor를 확인할 수 있다 (서버사이드 계산)

### 실시간 데이터 수집 & 평가 엔진 (LIVE)

- [ ] **LIVE-01**: 시스템은 KRX 장 중(09:00–15:30 KST, 거래일)에 한해 5분마다 활성 종목의 인트라데이 봉을 수집한다
- [ ] **LIVE-02**: 시스템은 KRX 공휴일 목록을 유지하고, 장 외에는 평가를 건너뛴다
- [ ] **LIVE-03**: 시스템은 다중 인스턴스 환경에서 이중 평가가 발생하지 않도록 분산 잠금(ShedLock)을 적용한다
- [ ] **LIVE-04**: 시스템은 데이터 수집 후 최신 봉이 10분 이상 오래된 경우 평가를 건너뛰고 경고를 기록한다
- [ ] **LIVE-05**: 시스템은 PAPER_LIVE 상태인 룰을 매 평가 주기마다 자동으로 평가하고 가상 체결을 실행한다
- [ ] **LIVE-06**: 시스템은 각 평가 주기 종료 시 가상 계좌의 평가금액 스냅샷을 DB에 저장한다

### 모의 대시보드 (DASH)

- [ ] **DASH-01**: 사용자는 모의 대시보드에서 가상 현금 잔고와 총 평가금액을 확인할 수 있다
- [ ] **DASH-02**: 사용자는 모의 대시보드에서 현재 보유 포지션 목록(종목, 수량, 평균단가, 평가손익)을 확인할 수 있다
- [ ] **DASH-03**: 사용자는 모의 대시보드에서 오늘 실현된 손익 합계를 확인할 수 있다
- [ ] **DASH-04**: 사용자는 모의 대시보드에서 현재 PAPER_LIVE 상태인 룰 목록과 마지막 신호 발생 시각을 확인할 수 있다

### 룰 생애주기 관리 (RULE)

- [ ] **RULE-01**: 사용자는 백테스트를 최소 1회 실행한 룰을 PAPER_LIVE로 승격할 수 있다 (미실행 시 차단)
- [ ] **RULE-02**: 사용자는 PAPER_LIVE 룰을 일시정지(PAUSED)하거나 재개할 수 있다
- [ ] **RULE-03**: 사용자는 룰 목록에서 각 룰의 현재 상태(DRAFT / BACKTESTED / PAPER_LIVE / PAUSED / LIVE)를 배지로 확인할 수 있다
- [ ] **RULE-04**: 사용자는 PAPER_LIVE 룰을 LIVE로 승격할 수 있다 (토스증권 인증 완료 + 최소 5거래일 운영 확인 필수)
- [ ] **RULE-05**: LIVE 룰은 정의 편집이 불가하며, 수정 시 DRAFT 복사본으로만 가능하다

### 실시간 모니터 (MON)

- [ ] **MON-01**: 사용자는 모니터 페이지에서 각 룰의 신호 평가 로그(시각, 종목, 신호 종류, 결과)를 확인할 수 있다
- [ ] **MON-02**: 사용자는 모니터 페이지에서 스케줄러 마지막 실행 시각과 장중/장외 상태를 확인할 수 있다
- [ ] **MON-03**: 사용자는 모니터 페이지에서 오늘 체결된 가상 거래 피드를 실시간으로 확인할 수 있다
- [ ] **MON-04**: 신호 발생 로그에는 신호 평가 시점의 주요 지표값(RSI, SMA 등)이 함께 기록된다

### 성과 리포트 (REPORT)

- [ ] **REPORT-01**: 사용자는 성과 리포트 페이지에서 모의 실행 기간의 수익 곡선과 핵심 지표(수익률, MDD, 승률, 거래 횟수)를 확인할 수 있다
- [ ] **REPORT-02**: 사용자는 성과 리포트 페이지에서 모의 기간 동안의 Sharpe Ratio와 Sortino Ratio를 확인할 수 있다

### 토스증권 연동 (TOSS)

- [ ] **TOSS-01**: 사용자는 설정 페이지에서 토스증권 client_id와 client_secret을 등록할 수 있다 (AES-256-GCM 암호화 저장)
- [ ] **TOSS-02**: 시스템은 토스증권 OAuth 액세스 토큰을 자동 발급하고, 만료 10분 전 선제적으로 갱신한다
- [ ] **TOSS-03**: 사용자는 연동된 토스증권 실계좌 잔고를 대시보드에서 조회할 수 있다
- [ ] **TOSS-04**: 시스템은 LIVE 룰 평가 결과에 따라 토스증권 REST API로 실제 매수/매도 주문을 발행한다
- [ ] **TOSS-05**: 시스템은 토스증권 API에서 수신한 실시간 시세를 LIVE 룰 평가에 사용한다
- [ ] **TOSS-06**: 시스템은 토스증권 API 연속 5회 실패 시 서킷 브레이커를 열고 LIVE 룰 평가를 중단한다

---

## v2 Requirements

### 백테스트 고도화

- **CHART-04**: 월별 수익 히트맵 (캘린더 그리드)
- **CHART-05**: 벤치마크 비교선 (KOSPI 200 / KODEX 200)
- **CHART-06**: 매수/매도 마커를 수익 곡선 위에 표시

### 모의 고도화

- **LIVE-07**: 슬리피지 + 거래세 모델링 (FillSimulator 비용 모델)
- **LIVE-08**: 수익률 `evalTiming: EOD/INTRADAY` 선택 (look-ahead bias 방지)
- **DASH-05**: 수익 곡선 미니 스파크라인 (대시보드 내)

### 성과 리포트 고도화

- **REPORT-03**: 거래 내역 테이블 (페이지네이션)
- **REPORT-04**: 월별 수익률 요약
- **REPORT-05**: 룰별 성과 비교 (다중 룰 운영 시)

### 안전장치

- **RULE-06**: 룰 복제 (파라미터 실험용 DRAFT 복사)
- **RULE-07**: 쿨다운 UI 표시 ("다음 진입 가능: X분 후")
- **MON-05**: 상한가/하한가 종목 PRICE_LIMIT_PENDING 상태 처리

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| 수동 주문 UI | 자동매매 룰 기반으로 결정; 수동 주문은 별도 마일스톤 |
| WebSocket 실시간 푸시 | 5분 폴링으로 충분; 실시간 푸시 인프라는 과잉 |
| 이메일/푸시 알림 | 이 마일스톤 범위 초과; 신호 로그로 대체 |
| 모바일 앱 | 웹 우선 |
| 타 증권사 API | 토스증권 우선 |
| 암호화폐 | 국내 주식 한정 |
| Walk-forward / Monte Carlo | 과최적화 위험, 복잡도 과다 |
| PDF 리포트 내보내기 | 브라우저 인쇄 기능으로 충분 |
| 생존편향 완전 해결 | UI 경고 표시로 우선 대응 |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 0 | Complete |
| DATA-02 | Phase 0 | Pending |
| DATA-03 | Phase 0 | Pending |
| DATA-04 | Phase 0 | Pending |
| DATA-05 | Phase 0 | Complete |
| CHART-01 | Phase 1 | Pending |
| CHART-02 | Phase 1 | Pending |
| CHART-03 | Phase 1 | Pending |
| LIVE-01 | Phase 2 | Pending |
| LIVE-02 | Phase 2 | Pending |
| LIVE-03 | Phase 2 | Pending |
| LIVE-04 | Phase 2 | Pending |
| LIVE-05 | Phase 3 | Pending |
| LIVE-06 | Phase 3 | Pending |
| MON-04 | Phase 3 | Pending |
| DASH-01 | Phase 4 | Pending |
| DASH-02 | Phase 4 | Pending |
| DASH-03 | Phase 4 | Pending |
| DASH-04 | Phase 4 | Pending |
| RULE-01 | Phase 4 | Pending |
| RULE-02 | Phase 4 | Pending |
| RULE-03 | Phase 4 | Pending |
| RULE-05 | Phase 4 | Pending |
| MON-01 | Phase 4 | Pending |
| MON-02 | Phase 4 | Pending |
| MON-03 | Phase 4 | Pending |
| REPORT-01 | Phase 4 | Pending |
| REPORT-02 | Phase 4 | Pending |
| TOSS-01 | Phase 5 | Pending |
| TOSS-02 | Phase 5 | Pending |
| TOSS-03 | Phase 5 | Pending |
| TOSS-04 | Phase 6 | Pending |
| TOSS-05 | Phase 6 | Pending |
| TOSS-06 | Phase 6 | Pending |
| RULE-04 | Phase 6 | Pending |

**Coverage:**
- v1 requirements: 34 total (29 original + 5 DATA)
- Mapped to phases: 34
- Unmapped: 0 ✓

---
*Requirements defined: 2026-06-20*
*Last updated: 2026-06-20 — RULE-04 moved Phase 4 → Phase 6 (requires Toss auth completion, belongs with LIVE promotion gate)*

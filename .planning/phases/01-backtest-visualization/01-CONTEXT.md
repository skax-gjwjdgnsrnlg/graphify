# Phase 1: 백테스트 시각화 - Context

**Gathered:** 2026-06-20
**Status:** Ready for planning

<domain>
## Phase Boundary

5분봉 기반 인트라데이 백테스팅 엔진 + 결과 시각화를 완성한다.
사용자가 날짜 범위를 선택하면 해당 기간의 거래량 상위 종목(volume_top_n)을 자동 선정하고,
각 거래일의 09:00–12:00 KST 구간 5분봉 데이터로 백테스팅을 실행한다.
결과는 수익 곡선 차트(드로우다운 음영 포함) + 고급 통계 지표(Sharpe/Sortino/Profit Factor)로 표시한다.

**일봉 백테스팅 대체:** 기존 일봉 기반 BacktestService를 5분봉 인트라데이 모드로 전환.
일봉은 유니버스 선정(거래량 기준)에만 계속 사용한다.

**PAPER_LIVE 일관성:** 라이브 모의투자도 동일하게 5분봉 기준으로 운영 (Phase 3에서 적용).

</domain>

<decisions>
## Implementation Decisions

### 분봉 인터벌
- **5분봉 (5m) 단일 표준** — 백테스팅과 PAPER_LIVE 모두 동일 인터벌
- Yahoo Finance 제공 한계 60일을 감수하고 일관성 우선 (A-2 결정)
- 백테스팅 가능 기간: 최대 60일 (사용자 인지 완료)

### 백테스팅 시간대
- **09:00–12:00 KST** 구간만 평가
- BacktestRequest에 `timeFrom`(기본 "09:00"), `timeTo`(기본 "12:00") 파라미터 추가
- 사용자가 UI에서 조정 가능하도록 입력 필드 제공

### 유니버스 선정 방식
- 기존 `volume_top_n` 유지: 거래량은 **일봉** 기준으로 상위 N종목 선정
- 선정된 종목의 **5분봉**을 Yahoo Finance에서 수집하여 백테스팅
- 날짜별 동적 선정 로직(Phase 0에서 구현) 그대로 활용

### 수익 곡선 (Equity Curve)
- 날짜 범위 전체를 **하나의 연속 곡선**으로 표시
- x축: `datetime` (날짜+시간), 각 거래일 09:00–12:00 세션이 연속으로 이어짐
- `EquityPoint.date` → `EquityPoint.datetime` (LocalDateTime)으로 변경
- y축: 가상 계좌 평가금액 (원)

### 드로우다운 시각화
- 수익 곡선과 **같은 차트에 오버레이** (별도 패널 없음)
- 기준: 직전 고점 → 현재값 구간마다 recharts `ReferenceArea`로 표시
- 색상: `rgba(239, 68, 68, 0.15)` 연한 붉은 반투명 음영
- 서버에서 드로우다운 구간 목록(`DrawdownSegment`) 계산하여 내려줌

### 차트 라이브러리
- **recharts** (기존 로드맵 결정 유지)
- `LineChart` + `ReferenceArea` 조합으로 구현
- 100–500포인트 데이터에서 성능 문제 없음 (SVG 기반)

### 고급 통계 위치 및 계산
- 차트 **아래 별도 '고급 통계' 섹션** (기존 5개 메트릭 카드와 분리)
- 항목: Sharpe Ratio · Sortino Ratio · Profit Factor 3개 카드
- **서버사이드 계산** — `BacktestResult`에 `sharpeRatio`, `sortinoRatio`, `profitFactor` 필드 추가

### 차트 인터랙션
- **hover 툴팁**: datetime + 평가액 + 누적 수익률 (세 줄)
- 거래 테이블 연동 없음 (Phase 1 범위 초과)
- 줌/패닝/Brush 없음 (정적 차트) — v2에서 추가 검토

### 페이지 레이아웃 순서
```
폼 (룰 선택 + 날짜 범위 + 시간대 + 초기자본)
→ 요약 메트릭 5개 카드 (기존 유지)
→ 수익 곡선 차트 (드로우다운 음영 포함)
→ 고급 통계 3개 카드 (Sharpe / Sortino / Profit Factor)
→ 거래 내역 테이블
```

### Claude's Discretion
- 차트 높이 (350px 권장)
- x축 날짜+시간 레이블 밀도 (세션 경계마다 날짜 표시 권장)
- 툴팁 스타일링 (dark theme 기존 패턴 따름)
- Sharpe/Sortino 계산 시 무위험수익률 기본값 (0% 또는 연 3.5% — planner 결정)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `BacktestResult.EquityPoint(LocalDate date, double equity)` — datetime으로 마이그레이션 필요
- `BacktestService` — 동적 유니버스 선정 로직 재활용, 분봉 모드 추가
- `YahooFinanceChartClient` — 이미 수정됨, 5분봉 fetch 확장 필요
- `MarketBarIntraday.java` — 신규 파일 이미 존재 (git status 확인), 분봉 저장소로 활용
- `PaperBacktestPage.tsx` — 기존 폼+테이블 구조 유지, 차트 섹션 삽입
- `BacktestResult` TS 타입 — `equityCurve: BacktestEquityPoint[]` 이미 있음, datetime으로 변경

### Established Patterns
- UI: 다크 테마(gray-800/900), emerald-600 액센트, border-white/10, rounded-lg 카드
- API: React Query (`useMutation`) + `runBacktest()` 함수 기존 연결됨
- 서버: `ApiResponse<T>` 래퍼, Spring Boot 3.4.5 / Java 21

### Integration Points
- `BacktestRequest` — `interval`, `timeFrom`, `timeTo` 파라미터 추가
- `BacktestResult` — `sharpeRatio`, `sortinoRatio`, `profitFactor`, `drawdownSegments` 추가
- `EquityPoint.date: LocalDate` → `EquityPoint.datetime: LocalDateTime` 변경
- recharts 신규 설치 필요 (아직 package.json에 없음)

</code_context>

<specifics>
## Specific Ideas

- 9시부터 12시 단기 모멘텀 전략 검증이 핵심 사용 시나리오
- 일봉은 유니버스 선정(거래량 상위)에만 사용, 실제 신호 평가는 5분봉
- 백테스팅과 PAPER_LIVE가 동일 5분봉 인터벌 → 전략 이식성 보장
- Yahoo Finance 60일 제약은 인지된 상태로 진행

</specifics>

<deferred>
## Deferred Ideas

- 줌/Brush 범위 선택 — v2 (CHART-04 이후)
- 차트 hover 시 거래 테이블 행 하이라이트 연동 — v2
- 15분봉/1시간봉 인터벌 선택 — v2 (데이터 소스 확장 후)
- 세션별(일자별) 수익 분리 뷰 — v2
- 백테스팅 60일 이상 지원 (외부 유료 데이터 소스 연동) — 별도 마일스톤

</deferred>

---

*Phase: 01-backtest-visualization*
*Context gathered: 2026-06-20*

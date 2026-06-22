# Roadmap: Graphify 모의투자 고도화 & 토스증권 연동

## Overview

부분 구현된 모의투자(백테스트 엔진 + 룰 CRUD)를 완전한 서비스로 고도화하고, 검증된 룰을 토스증권 Open API를 통해 실투자로 승격시키는 엔드-투-엔드 파이프라인을 완성한다. 핵심 설계: **거래량 상위 KOSPI 10종목**을 자동으로 유니버스로 선정하여 백테스트·모의투자를 실행하며, TradingView webhook으로 외부 지표 신호를 받아 모의/실투자에 활용한다. 데이터 인프라 & 동적 유니버스(Phase 0) → 백테스트 시각화(Phase 1) → 실시간 데이터 수집 인프라(Phase 2) → PAPER_LIVE 평가 엔진(Phase 3) → 대시보드·룰 생애주기·모니터·리포트 UI(Phase 4) → 토스증권 OAuth 연동(Phase 5) → 룰 빌더 UI(Phase 6) → 룰 설정/라이프사이클 역할 분리(Phase 6.5) → 5분봉 캔들 차트 시각화(Phase 6.6) → TradingView webhook 연동(Phase 7) → 실투자 주문 실행(Phase 8) 순으로 의존성에 따라 빌드한다.

## Phases

**Phase Numbering:**
- Integer phases (0, 1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 0: 데이터 인프라 & 동적 유니버스** - KOSPI 200 일봉 OHLCV 전체 수집, volume_top_n 동적 유니버스 타입 추가, BacktestService volume 버그 수정을 완료해 이후 모든 phase의 데이터 기반을 확보한다 (completed 2026-06-20)
- [x] **Phase 1: 5분봉 인트라데이 백테스팅 & 시각화** - 일봉 백테스팅을 5분봉 인트라데이 모드로 전환하고(09:00–12:00 KST, Yahoo Finance 60일), 결과를 수익 곡선 차트 + 드로우다운 음영 + 고급 지표(Sharpe/Sortino/Profit Factor)로 시각화한다 (completed 2026-06-20)
- [x] **Phase 2: 실시간 데이터 수집 & 스케줄러 인프라** - KRX 장 중 5분 주기 분봉 수집, 공휴일 가드, ShedLock 분산 잠금을 구축한다 (completed 2026-06-21)
- [x] **Phase 3: PAPER_LIVE 평가 엔진** - 스케줄러 기반 실시간 룰 평가 + 가상 체결 + 스냅샷 저장 엔진을 완성한다 (completed 2026-06-21)
- [x] **Phase 4: 대시보드·룰 생애주기·모니터·리포트 UI** - 모의 대시보드, 룰 상태 전환 UI, 실시간 모니터, 성과 리포트 페이지를 완성한다 (completed 2026-06-21)
- [x] **Phase 5: 토스증권 OAuth & 자격증명 관리** - 토스증권 client_id/secret 암호화 저장, 토큰 자동 발급·갱신, 실계좌 잔고 조회를 구현한다 (completed 2026-06-21)
- [ ] **Phase 6: 룰 빌더 UI** - JSON 직접 입력 대신 드롭다운/폼으로 유니버스·진입/청산 조건·사이징을 시각적으로 구성할 수 있는 룰 빌더 UI를 구현한다
- [x] **Phase 6.5: 룰 설정/라이프사이클 역할 분리 & 매매 근거** (INSERTED) - 모의 룰 설정(DRAFT↔ACTIVE 관리)과 룰 라이프사이클(ACTIVE 중 PAPER_LIVE 적용 여부 + 시작/중지)의 역할을 분리하고, 백테스트·매매 이력에 매수/매도 근거(트리거 조건·지표값)를 표시한다 (completed 2026-06-22)
- [ ] **Phase 6.6: 5분봉 캔들 차트 시각화** (INSERTED) - lightweight-charts로 수익곡선 아래 5분봉 OHLCV 캔들 차트를 렌더링하고 진입/청산 시점을 마커로 표시한다
- [ ] **Phase 7: TradingView webhook 연동** - 새 룰 타입(TRADINGVIEW)을 추가해 TradingView alert webhook을 비동기로 수신하고, JSON/LLM 파서로 매수/매도 신호를 해석하여 PAPER_LIVE 가상 체결에 활용한다. 사전 등록된 KOSPI 시총 상위 종목 풀에서 종목을 선택한다
- [ ] **Phase 8: 실투자 주문 실행 & LIVE 승격** - LIVE 룰 평가에 따른 토스증권 실제 주문 발행, 시세 연동, 서킷 브레이커를 구현한다

## Phase Details

### Phase 0: 데이터 인프라 & 동적 유니버스
**Goal**: KOSPI 200 전체 종목의 2년치 OHLCV를 수집하고, `volume_top_n` 동적 유니버스를 BacktestService/LiveEvaluationService에서 지원하며, BacktestService의 volume null 버그를 수정해 이후 모든 phase에서 거래량 기반 룰이 정상 동작하게 한다
**Depends on**: Nothing (first phase — data foundation)
**Requirements**: DATA-01, DATA-02, DATA-03, DATA-04, DATA-05
**Success Criteria** (what must be TRUE):
  1. KOSPI 200 종목 리스트가 companies 테이블에 `in_kospi200=true` 플래그로 관리되며, MarketDataIngestionService가 이 종목들의 일봉을 수집한다
  2. market_bars 테이블에 KOSPI 200 전체 종목의 일봉 데이터(volume 포함)가 적재된다
  3. `RuleDefinition.Universe`에 `{"type":"volume_top_n","market":"KOSPI","topN":10}` 형식이 추가되며, BacktestService가 각 날짜별 거래량 상위 10종목을 동적으로 선정해 룰을 평가한다
  4. BacktestService가 `closes`와 함께 실제 `volumes` 배열을 RuleEvaluator에 전달하여 VOLUME 지표가 올바르게 계산된다
  5. `additionalSymbols`에 명시된 종목은 자동 선정 종목과 합산되어 유니버스를 구성한다
**Plans**: 2 plans

Plans:
- [ ] 00-01: BacktestService volume null 버그 수정 (closes 추출과 동일하게 volumes 배열 추출 후 전달)
- [ ] 00-02: companies 테이블에 `in_kospi200` 컬럼 추가 (Flyway V30) + KOSPI 200 종목 초기 데이터 삽입
- [ ] 00-03: MarketDataIngestionService에 `ingestDailyForKospi200()` 추가 — `in_kospi200=true` 종목 전체 OHLCV 수집, @Scheduled 매 거래일 장 마감 후 실행
- [ ] 00-04: `RuleDefinition.Universe`에 `volume_top_n` 타입 추가 + BacktestService/LiveEvaluationService에 날짜별 동적 유니버스 선정 로직 구현 (market_bars에서 KOSPI 거래량 상위 N 쿼리)

### Phase 1: 5분봉 인트라데이 백테스팅 & 시각화
**Goal**: 일봉 백테스팅을 5분봉 인트라데이 모드로 전환한다. 사용자가 날짜 범위를 선택하면 해당 기간 거래량 상위 종목(volume_top_n)을 자동 선정하고 각 거래일 09:00–12:00 KST 구간 5분봉으로 백테스팅을 실행한다. 결과는 datetime 축 수익 곡선 + 드로우다운 음영 + Sharpe/Sortino/Profit Factor로 시각화한다.
**Depends on**: Phase 0
**Requirements**: CHART-01, CHART-02, CHART-03
**Constraints**: Yahoo Finance 5분봉 제공 기간 최대 60일
**Success Criteria** (what must be TRUE):
  1. BacktestRequest에 interval(기본 5m), timeFrom(기본 09:00), timeTo(기본 12:00) 파라미터가 추가되고, BacktestService가 해당 구간 5분봉으로 평가를 실행한다
  2. 유니버스 선정은 일봉 거래량(volume_top_n) 기준을 유지하고, 선정된 종목의 5분봉 데이터를 Yahoo Finance에서 수집한다
  3. 수익 곡선이 datetime x축(각 세션 09:00–12:00 연속 연결)으로 렌더링되며, 드로우다운 구간에 연한 붉은 음영이 오버레이된다
  4. Sharpe Ratio, Sortino Ratio, Profit Factor가 서버에서 계산되어 차트 아래 별도 섹션에 표시된다
  5. hover 툴팁에 datetime + 평가액 + 누적 수익률이 표시된다
**Plans**: 2 plans

Plans:
- [x] 01-01: Yahoo Finance 5분봉 수집 + MarketBarIntraday 저장소 + BacktestRequest 인트라데이 파라미터
- [ ] 01-02: BacktestService 5분봉 모드 전환 + Sharpe/Sortino/Profit Factor 서버 계산 + BacktestResult 확장
- [ ] 01-03: recharts 설치 + PaperBacktestPage 차트 컴포넌트 (수익곡선 + 드로우다운 음영)
- [ ] 01-04: PaperBacktestPage UI 완성 (시간대 입력 필드 + 고급 통계 섹션 + 레이아웃 정렬)

### Phase 2: 실시간 데이터 수집 & 스케줄러 인프라
**Goal**: KRX 장 중(09:00-15:30 KST, 거래일)에만 5분마다 활성 종목 분봉을 수집하고, 다중 인스턴스 환경에서 이중 수집이 발생하지 않도록 ShedLock 분산 잠금을 적용한다
**Depends on**: Phase 1
**Requirements**: LIVE-01, LIVE-02, LIVE-03, LIVE-04
**Success Criteria** (what must be TRUE):
  1. 장 중 5분마다 활성 종목의 인트라데이 봉이 market_bars 테이블에 적재된다
  2. KRX 공휴일이 등록된 경우 해당 날짜에는 수집 및 평가가 실행되지 않는다
  3. 다중 인스턴스 환경에서 동일 틱에 분산 잠금이 적용되어 수집이 1회만 실행된다
  4. 최신 봉이 10분 이상 오래된 경우 WARNING 로그가 기록되고 이후 평가가 건너뛰어진다
**Plans**: 2 plans

Plans:
- [ ] 02-01: ShedLock 의존성 추가 + KrxMarketCalendar 구현 + market_holidays 마이그레이션
- [ ] 02-02: MarketDataPort.recentIntradayBars() 구현체(LiveIntradayAdapter) + @Scheduled 틱 + staleness 감지

### Phase 3: PAPER_LIVE 평가 엔진
**Goal**: PAPER_LIVE 상태인 룰을 매 평가 주기마다 자동으로 평가하고, 가상 체결을 실행하며, 평가금액 스냅샷과 신호 로그(지표값 포함)를 DB에 저장한다
**Depends on**: Phase 2
**Requirements**: LIVE-05, LIVE-06, MON-04
**Success Criteria** (what must be TRUE):
  1. PAPER_LIVE 룰이 존재할 때 스케줄러 틱마다 RuleEvaluator가 실행되고 신호 발생 시 PaperExecutor가 가상 체결을 기록한다
  2. 매 평가 주기 종료 시 paper_equity_snapshots 테이블에 가상 계좌 평가금액이 저장된다
  3. 신호 로그에 평가 시점의 RSI, SMA 등 주요 지표값이 함께 기록된다
  4. DB 쓰기 관통(write-through) 방식으로 틱마다 상태를 로드하고 플러시하여 인스턴스 재시작 후에도 포지션이 유지된다
**Plans**: 2 plans

Plans:
- [ ] 03-01: OrderExecutorPort 인터페이스 + PaperExecutor 구현 + paper_signal_log 마이그레이션
- [ ] 03-02: LiveEvaluationService (load → evaluate → flush) + 스케줄러 틱에 통합

### Phase 4: 대시보드·룰 생애주기·모니터·리포트 UI
**Goal**: 모의 대시보드(잔고·포지션·손익), 룰 상태 전환 UI(DRAFT→PAPER_LIVE→LIVE), 실시간 신호 모니터, 성과 리포트 페이지를 완성하여 사용자가 전략 운영 상황을 한눈에 파악하고 제어할 수 있다
**Depends on**: Phase 3
**Requirements**: DASH-01, DASH-02, DASH-03, DASH-04, RULE-01, RULE-02, RULE-03, RULE-04, RULE-05, MON-01, MON-02, MON-03, REPORT-01, REPORT-02
**Success Criteria** (what must be TRUE):
  1. 모의 대시보드에서 가상 현금 잔고·총 평가금액·보유 포지션(종목/수량/평균단가/평가손익)·오늘 실현손익·활성 PAPER_LIVE 룰 목록이 모두 표시된다
  2. 룰 목록에서 각 룰의 현재 상태(DRAFT/BACKTESTED/PAPER_LIVE/PAUSED/LIVE)가 배지로 표시되고, 백테스트 1회 이상 완료된 룰만 PAPER_LIVE 승격 버튼이 활성화된다
  3. 모니터 페이지에서 신호 평가 로그(시각·종목·신호·결과), 스케줄러 마지막 실행 시각, 장중/장외 상태, 오늘 체결 피드가 표시된다
  4. 성과 리포트 페이지에서 모의 실행 기간의 수익 곡선·수익률·MDD·승률·거래 횟수·Sharpe/Sortino Ratio가 표시된다
  5. LIVE 룰은 편집 버튼이 비활성화되고, 수정 시 DRAFT 복사본 생성 안내가 표시된다
**Plans**: 7 plans (04-01..04-04 base + 04-05..04-07 gap-closure)

Plans:
- [x] 04-01: PaperDashboardPage 완성 (잔고·포지션·손익 API + UI)
- [x] 04-02: 룰 생애주기 상태 전환 API + 룰 목록 배지 UI + PAPER_LIVE 승격 버튼
- [x] 04-03: TradingMonitorPage (신호 로그·스케줄러 상태·체결 피드)
- [x] 04-04: PaperReportPage (수익 곡선·지표·Sharpe/Sortino) + LIVE 룰 편집 차단
- [ ] 04-05: [gap] PAPER_LIVE 활성화 수정 — promote가 paper_live_symbols 채우고, 평가/수집 종목 소스를 paper_live_symbols로 일원화
- [ ] 04-06: [gap] PAPER 모드에서 승격 UI 도달 — TradingRulesPage를 PAPER 라우트/사이드바에 노출
- [ ] 04-07: [gap] 모의 거래 이력 — paper_trades 조회 엔드포인트 + paperApi + PaperHistoryPage 테이블

### Phase 5: 토스증권 OAuth & 자격증명 관리
**Goal**: 사용자가 토스증권 client_id와 client_secret을 안전하게 등록하고, 시스템이 액세스 토큰을 자동으로 발급·갱신하며, 연동된 실계좌 잔고를 대시보드에서 조회할 수 있다
**Depends on**: Phase 4
**Requirements**: TOSS-01, TOSS-02, TOSS-03
**Success Criteria** (what must be TRUE):
  1. 설정 페이지에서 client_id/client_secret을 등록하면 AES-256-GCM으로 암호화되어 toss_credentials 테이블에 저장된다 (DB에 평문 저장 없음)
  2. 시스템이 토스증권 OAuth 액세스 토큰을 자동 발급하고, 만료 10분 전에 선제적으로 갱신한다
  3. 대시보드에서 연동된 토스증권 실계좌 잔고를 조회할 수 있다
**Plans**: 2 plans

Plans:
- [ ] 05-01: toss_credentials 마이그레이션 + AesGcmAttributeConverter + TossCredentialService
- [ ] 05-02: TossTokenManager (자동 발급·갱신) + 실계좌 잔고 조회 API + 대시보드 연동

### Phase 6: 룰 빌더 UI
**Goal**: JSON 직접 입력 방식을 드롭다운·폼 기반 시각적 룰 빌더로 교체한다. 유니버스(거래량 상위 N / 직접 종목 지정), 진입 조건(PRICE·SMA·EMA·RSI·VOLUME + 비교 연산자 + crossAbove/Below), 청산 조건(익절%·손절%·지표 조건), 포지션 사이징(고정금액·전액), 쿨다운 봉 수를 모두 드롭다운/입력 필드로 구성할 수 있다
**Depends on**: Phase 5
**Requirements**: RULE-06, RULE-07
**Success Criteria** (what must be TRUE):
  1. 룰 생성/편집 페이지에서 JSON 에디터 없이 드롭다운과 입력 필드만으로 완전한 RuleDefinition JSON을 구성할 수 있다
  2. 유니버스 타입(거래량 상위 N / 직접 종목 지정), 진입 조건 지표(PRICE·SMA·EMA·RSI·VOLUME), 연산자(>·>=·<·<=·crossAbove·crossBelow), 청산(익절%·손절%·지표 조건), 사이징(고정금액·전액), 쿨다운을 모두 UI에서 설정 가능하다
  3. 빌더에서 구성한 룰을 저장하면 기존 백테스트·PAPER_LIVE 파이프라인이 그대로 동작한다
  4. 기존 JSON 룰도 빌더 폼에 로드(역직렬화)하여 편집할 수 있다
**Plans**: 2 plans

Plans:
- [x] 06-01-PLAN.md — RuleBuilderPage (유니버스·진입·청산·사이징·쿨다운 섹션) + trading.ts 타입 확장 + toDefinition/fromDefinition 직렬화 로직
- [x] 06-02-PLAN.md — router에 paper/rules/new·edit/:id 추가 + PaperRulesPage 모달 제거·navigate 전환 + 복제 버튼(RULE-06) + 쿨다운 컬럼(RULE-07)

### Phase 6.5: 룰 설정/라이프사이클 역할 분리 & 매매 근거 (INSERTED)
**Goal**: 모의 룰 설정 화면과 룰 라이프사이클 화면의 역할을 명확히 분리한다. 모의 룰 설정은 룰 CRUD와 DRAFT↔ACTIVE 상태 관리를 담당하고, 룰 라이프사이클은 ACTIVE 룰만 노출하되 각 룰이 현재 PAPER_LIVE로 적용 중인지(실행/중지) 여부를 구분해 보여주고 시작/중지를 제어한다. 추가로 백테스트·모의 매매 이력에 각 매수/매도가 어떤 조건(트리거 룰·지표값)으로 발생했는지 근거를 표시한다
**Depends on**: Phase 6
**Requirements**: RULE-08, RULE-09, MON-05
**Success Criteria** (what must be TRUE):
  1. 모의 룰 설정(PaperRulesPage)은 모든 상태의 룰을 나열하고 DRAFT↔ACTIVE 상태 전환을 관리하며, PAPER_LIVE 실행/중지 제어는 노출하지 않는다
  2. 룰 라이프사이클 화면은 ACTIVE 상태 룰만 표시하고, 각 룰이 PAPER_LIVE 적용 중인지(실행 중/중지됨) 배지로 구분하며 시작/중지 버튼을 제공한다
  3. 두 화면이 동일 상태를 중복 표시하지 않으며 역할 경계가 명확하다
  4. 백테스트 매매 이력의 각 거래에 매수/매도 트리거 근거(충족된 진입/청산 조건 + 그 시점 지표값)가 표시된다
  5. 모의(PAPER_LIVE) 매매 이력에도 동일하게 신호 근거가 표시된다
**Plans**: 5 plans (3 waves)

Plans:
- [x] 06.5-01-PLAN.md — 2축 상태 모델(V35) + PaperLifecycleService activate/start/stop 재구성 + RULE-08 전이 가드
- [x] 06.5-02-PLAN.md — RuleEvaluator EvalResult 리치 반환 타입 (조건별 값 + 청산 사유) [TDD]
- [x] 06.5-03-PLAN.md — 백테스트 엔진 근거 포착 + TradeDto rationaleJson 인라인 운반
- [x] 06.5-04-PLAN.md — 모의 근거 병합(LiveEvaluationService) + paper_trades↔signal_log JOIN + MON-05 PRICE_LIMIT_PENDING
- [x] 06.5-05-PLAN.md — 프론트 역할 분리(설정/운영) + TradeRationaleRow 아코디언(백테스트+모의)

### Phase 6.6: 5분봉 캔들 차트 시각화 (INSERTED)
**Goal**: 백테스트 결과 화면에서 기존 수익곡선 차트 아래에, 선택한 거래일의 5분봉 OHLCV를 캔들스틱 차트로 렌더링한다. x축은 세션 시작/종료 시간, y축은 가격이며, 룰이 진입/청산한 시점을 차트 위에 마커로 표시한다. 차트 라이브러리는 lightweight-charts(Apache 2.0)를 사용한다
**Depends on**: Phase 6.5
**Requirements**: CHART-04, CHART-05
**Success Criteria** (what must be TRUE):
  1. lightweight-charts가 프론트엔드에 도입되고 5분봉 캔들 차트 컴포넌트가 추가된다
  2. 백테스트 결과의 일자별 5분봉(09:00–12:00 등 세션 구간)이 캔들스틱으로 렌더링된다 (x=시간, y=가격)
  3. 백테스트에서 발생한 매수/매도 시점이 캔들 차트 위에 마커(매수=상향/매도=하향)로 표시된다
  4. 캔들 차트가 기존 수익곡선 차트 아래에 배치된다
  5. 거래일이 여러 날인 경우 날짜를 선택해 해당일 5분봉을 볼 수 있다
**Plans**: 3 plans (2 waves)

Plans:
- [ ] 06.6-01-PLAN.md — bars 백엔드 엔드포인트 (MarketBarIntraday getters + CandleBarDto + GET /bars + BarQueryTest)
- [ ] 06.6-02-PLAN.md — lightweight-charts 설치 + CandleChart 컴포넌트 + candleIndicators/fetchBars/CandleBar 타입
- [ ] 06.6-03-PLAN.md — CandleSection(4상태) + PaperBacktestPage/PaperHistoryPage 통합 + 행클릭 선택 + e2e

### Phase 7: TradingView webhook 연동
**Goal**: TradingView를 외부 신호 소스로 연동한다. 새 룰 타입(TRADINGVIEW)을 추가해, TradingView alert가 호출하는 webhook을 비동기로 수신(3초 타임아웃 회피)하고, 페이로드를 JSON 우선·실패 시 LLM(Claude API) fallback으로 파싱해 매수/매도 신호를 해석한 뒤 PAPER_LIVE 가상 체결에 반영한다. 종목은 시스템에 사전 등록된 KOSPI 시총 상위 종목 풀에서 선택하며, TradingView alert 등록은 사용자가 수동으로 수행한다(공식 API 없음). 백테스트는 기존 CUSTOM 룰 엔진을 그대로 사용한다(TradingView Strategy Tester 결과는 fetch 불가)
**Depends on**: Phase 6.6
**Requirements**: TV-01, TV-02, TV-03, TV-04, TV-05
**Constraints**:
  - TradingView 공식 API 없음 — webhook push만 가능 (alert 생성/조회/지표값 pull 전부 불가)
  - webhook은 TradingView Plus 이상에서만 동작, alert 개수 한도 15개 → multi-symbol Pine Script로 압축
  - webhook 응답 3초 초과 시 드랍·재시도 없음 → 수신 즉시 큐잉 후 비동기 처리 필수
**Success Criteria** (what must be TRUE):
  1. trading_rules에 rule_type(CUSTOM/TRADINGVIEW)과 룰별 고유 webhook_token이 추가되고, TRADINGVIEW 룰은 룰 목록에 TV 배지로 구분된다
  2. `POST /api/webhook/tv/{token}`이 페이로드를 즉시 큐에 넣고 100ms 내 HTTP 200을 반환하며, 파싱·체결은 비동기로 처리된다
  3. 신호 파서가 JSON(`{"symbol","action"}`)을 먼저 파싱하고 실패 시 LLM fallback으로 BUY/SELL/UNKNOWN을 판정하며, UNKNOWN이면 체결하지 않고 로그만 남긴다
  4. KOSPI 시총 상위 종목 풀(tv_supported_symbols)이 사전 등록되고, 룰 생성 시 이 풀에서 종목을 선택한다. webhook의 종목이 룰 종목 리스트에 없으면 무시된다
  5. TRADINGVIEW 룰의 PAPER_LIVE 가상 체결이 기존 PaperExecutor 파이프라인으로 처리되고, 룰 빌더에 TradingView 연동 메모·webhook URL 복사·파싱 방식 선택 UI가 제공된다
  6. TRADINGVIEW 룰은 자체 백테스트 대신 "TradingView에서 보기" 링크를 제공한다
**Plans**: TBD (plan-phase에서 분해)

### Phase 8: 실투자 주문 실행 & LIVE 승격
**Goal**: LIVE 룰 평가 결과에 따라 토스증권 REST API로 실제 매수/매도 주문을 발행하고, 실시간 시세를 LIVE 평가에 활용하며, API 연속 실패 시 서킷 브레이커로 주문을 안전하게 중단한다
**Depends on**: Phase 7
**Requirements**: TOSS-04, TOSS-05, TOSS-06, RULE-04
**Success Criteria** (what must be TRUE):
  1. LIVE 룰 평가에서 신호 발생 시 토스증권 REST API로 실제 매수/매도 주문이 발행된다
  2. LIVE 룰 평가에 토스증권 API 실시간 시세가 사용된다
  3. 토스증권 API 연속 5회 실패 시 서킷 브레이커가 열리고 LIVE 룰 평가가 중단되며 경고 로그가 기록된다
  4. PAPER_LIVE 룰을 LIVE로 승격하려면 토스증권 인증 완료 + 최소 5거래일 운영 조건을 충족해야 한다
**Plans**: 2 plans

Plans:
- [ ] 08-01: TossOrderExecutor 구현 + live_accounts/live_trades 마이그레이션 + 서킷 브레이커
- [ ] 08-02: LIVE 시세 어댑터(TossLiveIntradayAdapter) + PAPER_LIVE→LIVE 승격 게이트 UI/API

## Progress

**Execution Order:**
Phases execute in numeric order: 0 → 1 → 2 → 3 → 4 → 5 → 6 → 6.5 → 6.6 → 7 → 8

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. 데이터 인프라 & 동적 유니버스 | 4/4 | Complete   | 2026-06-20 |
| 1. 백테스트 시각화 | 4/4 | Complete   | 2026-06-20 |
| 2. 실시간 데이터 수집 & 스케줄러 인프라 | 2/2 | Complete   | 2026-06-21 |
| 3. PAPER_LIVE 평가 엔진 | 2/2 | Complete   | 2026-06-21 |
| 4. 대시보드·룰 생애주기·모니터·리포트 UI | 7/7 | Complete   | 2026-06-21 |
| 5. 토스증권 OAuth & 자격증명 관리 | 2/2 | Complete   | 2026-06-21 |
| 6. 룰 빌더 UI | 2/2 | Complete   | 2026-06-21 |
| 6.5. 룰 설정/라이프사이클 역할 분리 & 매매 근거 | 5/5 | Complete   | 2026-06-22 |
| 6.6. 5분봉 캔들 차트 시각화 | 0/3 | Planned | - |
| 7. TradingView webhook 연동 | 0/? | Not started | - |
| 8. 실투자 주문 실행 & LIVE 승격 | 0/2 | Not started | - |

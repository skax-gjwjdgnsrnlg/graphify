# Design Document

> 프로젝트의 모든 기능 설계를 **하나의 문서**로 통일 관리한다.
> 새 설계는 최신 항목이 위로 오도록 추가하고, 각 항목에 대상 릴리즈를 명시한다.
> 구현 완료 시 Status를 갱신하고 `RELEASE_NOTES.md`에 반영한다.

---

## [v1.5.3] 룰 중지 미반영 회귀 수정 + 백테스트 차트 근거 표시

> Status: 구현 완료 · 2026-06-24
> 계기: 사용자 UI 테스트에서 "중지 눌러도 실행 상태가 안 바뀜"(#2), 백테스트 차트 RSI pane 미표시(#3). Playwright로 재현·검증.

### 1. BUG — 룰 중지가 DB에 반영되지 않음 (v1.5.1 BUG-1 수정의 회귀)

- **증상:** `/stop`이 HTTP 200 + 응답 runStatus=STOPPED를 반환하지만 DB는 RUNNING 잔류 → UI 배지·버튼이 안 바뀜. 직접 API 호출도 자기 GET이 RUNNING.
- **근인:** v1.5.1에서 `PaperLiveSymbolRepository.deleteByRuleId`에 붙인 `@Modifying(clearAutomatically = true)`. `PaperLifecycleService.stop()`은 `rule.setRunStatus("STOPPED")` → `ruleRepo.save()`(아직 flush 안 됨) → `paperLiveSymbolService.deactivateRule()`(벌크 삭제) 순서로 한 트랜잭션에서 실행되는데, **`clearAutomatically=true`가 영속성 컨텍스트를 비우며 아직 flush되지 않은 run_status UPDATE를 폐기** → 커밋되지 않음.
- **수정:** `@Modifying(flushAutomatically = true, clearAutomatically = true)`. 벌크 삭제 직전에 dirty 변경(run_status)을 먼저 flush해 폐기되지 않게 함. assignSymbols(BUG-1)는 삭제가 인서트보다 먼저라 영향 없음.
- **검증:** Playwright로 rules-lifecycle 화면에서 중지 클릭 → 배지 "실행 중"→"중지됨", 버튼 "중지"→"시작", DB run_status=STOPPED 확인. VolumeRankRefresher/History 테스트 그린.

### 2. 백테스트 차트 — 매매 근거 지표 표시 보강 (#3/#4 후속)

- **RSI pane 미표시 근인:** 백테스트 `buildRationale`는 `conditions`를 최상위에, 라이브 이력 `mergeRationale`는 `rationale.conditions`로 감싼다. 프론트 `indicatorsFromRationale`가 후자만 파싱해 백테스트에선 RSI를 못 찾았다. → 두 구조 모두 파싱하도록 수정.
- **근거 가독성:** 거래 클릭 시 차트 위에 **근거 패널** 추가 — 저장된 실제 지표값을 명시(예: `RSI(14) < 30 (실제 RSI(14) = 18.3) ✓`). 라이브 이력의 RSI 선(프론트 재계산)이 체결 당시 값과 다를 수 있어, 저장값을 진실로 표시.

---

## [v1.5.2] 시장 전체 장중 거래대금 랭킹 소스 전환 (Naver 모바일 API)

> Status: 구현 완료 · 2026-06-23
> 계기: 기존 라이브 랭킹이 사전 적재 후보 풀(in_kospi200)에 묶여 "시장 전체에서 거래대금 상위를 그때그때 발굴"하지 못함. 시장 전체 장중 거래대금 소스로 전환.
> 검증: 단위테스트 6개 green + 라이브 13:50 틱에서 Naver 거래대금 top-10 정확 선정(보유 포지션 union 포함 12종목), DB 후보 풀 밖 종목(LS ELECTRIC 등) 포함 확인.

### 1. 문제

`YahooCumulativeVolumeAdapter`는 `market_bars_intraday`(우리 DB)를 집계하는데, 그 테이블은 사전 플래그된 `in_kospi200` 후보만 적재됨 → 랭킹 탐색 공간이 후보 풀로 고정(dev 6종목). 시장 전체 발굴 불가 + 부트스트랩 의존성(광역 ingest 시드 필요).

### 2. 소스: Naver 모바일 증권 JSON API

- 엔드포인트: `GET m.stock.naver.com/api/stocks/marketValue/{market}?page=N&pageSize=100` (무인증, UTF-8 JSON).
- 각 종목 응답에 **당일 누적 거래대금**(`accumulatedTradingValueRaw`, 원)·종목구분(`stockEndType` stock/etf)·`itemCode` 포함. **장중 갱신**(marketStatus=OPEN, localTradedAt 실시간).
- marketValue(시총) 정렬이라 거래대금 전용 경로 비공개 → 전 종목 페이징(KOSPI ~2500=25페이지, MAX_PAGES=30 하드캡) 후 거래대금 DESC 재정렬.

### 3. 구현

| 컴포넌트 | 역할 |
|---|---|
| `NaverStockRankingClient` | marketValue 엔드포인트 페이징 조회 → `RankingRow(itemCode, name, tradingValue, etf)` 목록. 실패는 빈 리스트 흡수. |
| `NaverTradingValueRankingAdapter` (`@Qualifier("naverTradingValueRankingAdapter")`) | ETF 제외 → 거래대금 DESC → topN. 시장별 1분 TTL 캐시, 빈 응답 시 stale 캐시 유지. |
| 배선 | `VolumeRankRefresher`·`LiveEvaluationService`의 `VolumeRankingProvider` 주입을 yahoo→naver 어댑터로 교체. |
| 설정 | `graphify.market.naver-mobile-api-base-url`(기본 `https://m.stock.naver.com`) + `naverMobileRestClient` 빈. |

### 4. 효과 및 잔여 과제

- **효과:** 랭킹이 후보 풀에서 독립 → in_kospi200 사전 적재 불필요, 부트스트랩 의존성(ISSUE-3) 해소. 선정된 top-N만 `paper_live_symbols`에 저장(= 활성 룰의 거래 대상 기억). 사용자 의도 부합.
- **잔여 과제:** ① 선정 종목이 `companies` 테이블에 없을 수 있음(예: LS ELECTRIC) → RSI 평가용 Yahoo 분봉 적재 시 종목마스터 커버리지 의존. 별도 종목마스터 동기화 필요. ② `stockEndType`은 우선주를 stock으로 분류(예: 삼성전자우 005935 미제외) → 보통주 한정 필요 시 호출측 필터 의존. ③ 기존 `YahooCumulativeVolumeAdapter` + in_kospi200 광역 ingest 경로는 미사용(fallback 빈으로 보존, 추후 정리 가능). ④ Naver 비공식 API — 약관/구조 변경 리스크, 모니터링 필요.

---

## [v1.5.1] 실시간 유니버스 라이브 테스트 후속 수정 (BUG-1 / 거래대금 / staleness)

> Status: 구현 완료 · 2026-06-23
> 계기: 2026-06-23 장중(KST) Phase 6.7 라이브 끝장 테스트에서 발견된 3개 이슈 수정.
> 검증: 단위테스트 17개 green + 라이브 재기동 2틱 연속 재선정 성공(duplicate-key 0회) + 가상체결 1건 확인.

### 1. BUG-1 — 매 틱 재선정 실패 (paper_live_symbols 유니버스 고정)

- **증상:** 첫 배정 이후 매 틱 `VolumeRankRefresher.refreshOneRule`이 `uq_paper_live_symbols(rule_id,symbol)` 중복키로 throw → per-rule catch가 삼켜 `paper_live_symbols`가 첫 틱 집합에 고정. 신규 진입 종목 미수집/이탈 종목 미제거 → SC3("매 틱 재선정")·SC4(포지션 union 갱신)가 조용히 무효화.
- **근인:** `PaperLiveSymbolService.assignSymbols`가 한 트랜잭션에서 `deleteByRuleId`(파생 삭제 → `em.remove` 큐잉) + `save`(insert)를 수행. Hibernate 액션 큐가 **insert를 delete보다 먼저 flush** → 기존 행과 충돌. 첫 배정(빈 테이블)만 우연히 성공.
- **수정:** `PaperLiveSymbolRepository.deleteByRuleId`를 `@Modifying(clearAutomatically=true) @Query("DELETE FROM PaperLiveSymbol p WHERE p.ruleId = :ruleId")` 벌크 DML로 전환 → 호출 시점 DB 즉시 반영, 이후 insert와 무충돌.
- **검증:** 재기동 후 비어있지 않은 테이블(6행)에 대해 10:50·10:55 두 틱 "Assigned 6 symbols" 성공, duplicate-key 0회.

### 2. ISSUE-4 — 랭킹 기준: 거래량(주식 수) → 거래대금(거래량×종가)

- **변경:** 유니버스 선정 정렬 기준을 순수 거래량에서 거래대금으로 통일(저가 대량거래주 왜곡 방지).
  - 라이브: `MarketBarIntradayRepository.findCumulativeVolumeByMarketAndDate` → `ORDER BY SUM(m.volume * m.close) DESC`
  - 백테스트: `MarketBarRepository.findTopVolumeByMarketOnDate`, `findTopVolumeSymbolsOnDate` → `ORDER BY (b.volume * b.close) DESC`
- `YahooCumulativeVolumeAdapter` Javadoc/`@VolumeRankingSemantics` 주석도 거래대금 기준으로 갱신. → **아래 v1.5.0 §2 "거래량 기준" 서술은 본 항목으로 대체됨(거래대금 기준).** 백테스트(일봉)·라이브(분봉)의 분봉 vs 일봉 차이만 잔존.

### 3. ISSUE-2 — staleness 임계 10분 → 25분

- **근거:** 라이브 시세 소스인 Yahoo 5분봉이 KRX 대비 ~15분 지연 → 10분 임계로는 장중 거의 전 종목이 stale로 평가 스킵(실측 확인).
- **수정:** `LiveEvaluationService.STALENESS_MINUTES`(평가 스킵 기준)·`LiveDataScheduler.STALENESS_MINUTES`(경고 기준) 둘 다 25L로 상향.
- **남은 한계(미수정, 기록만):** ① 부트스트랩 의존성 — 스케줄러 단독으론 첫 틱 분봉 0→선정 0 정체, 별도 광역 `/internal/market/ingest` 시드 필요(운영 Cloud Scheduler 역할). ② dev DB의 `in_kospi200` 후보 6종목뿐 → 실 KOSPI200 적재 필요. ③ 근본적으로 Yahoo 지연 시세 → 향후 KRX 인증 연동/실시간 체결가 소스로 대체 검토.

---

## [v1.5.0] 실시간 거래량 상위 유니버스 (VolumeRankingProvider)

> Status: 구현 완료 (Phase 6.7) · 2026-06-23
> 의존: v1.4.0(모의투자 실행 엔진 + 역할 분리) 완료 위에 구축

**Phase 6.7 전체 구현 요약:**

volume_top_n 룰이 "그날(현재 시점까지 누적) 거래량 상위 종목"을 실시간으로 동적 선정한다.
KRX MDC는 인증 장벽(HTTP 400 LOGOUT)으로 차단됨 → Yahoo 5분 분봉 누적 집계 fallback 채택.

### 1. 컴포넌트 구조

| 컴포넌트 | 역할 | Bean 이름 |
|---------|------|-----------|
| `VolumeRankingProvider` | 포트 인터페이스 — `topVolume(market, date, topN, excludeEtf)` | — |
| `DbVolumeRankingAdapter` | 백테스트 impl — 완결 일봉(market_bars) JPQL, instrument_type='COMMON_STOCK' 필터 | `dbVolumeRankingAdapter` |
| `YahooCumulativeVolumeAdapter` | 라이브 impl — Yahoo 5m 분봉 누적 GROUP BY SUM + COMMON_STOCK JOIN + 1분 TTL 캐시 | `yahooCumulativeVolumeAdapter` |
| `VolumeRankRefresher` | 매 틱 volume_top_n 룰 재선정 — top-N ∪ 보유 포지션 → assignSymbols | — |

### 2. 백테스트 ↔ 라이브 거래량 기준 차이 및 일관성 방침 (SC5)

| 구분 | 백테스트 | 라이브 |
|------|---------|-------|
| **기준** | 해당 거래일 완결 일봉 거래량 (market_bars) | 당일 누적 인트라데이 거래량 (Yahoo 5m, KST 당일) |
| **범위** | 전체 KOSPI 보통주 (instrument_type='COMMON_STOCK') | 전체 KOSPI 보통주 (instrument_type='COMMON_STOCK') |
| **의미** | 완결된 하루치 거래량으로 해당일 전략 검증 | 당일 현재 시점까지 누적 — 장 중 실시간 반영 |
| **ETF/ETN 제외** | instrument_type='COMMON_STOCK' DB 컬럼 (V36 마이그레이션) | instrument_type='COMMON_STOCK' DB 컬럼 (동일) |

**일관성 방침: 차이를 수용·문서화, 백테스트 재정렬 안 함 (Deferred)**

- 이유: 백테스트를 인트라데이 누적 스냅샷 기준으로 재정렬하면 V28 이전 데이터 부재 + 비용 과다. CONTEXT.md Deferred에 명시됨.
- VolumeRankingProvider 인터페이스 Javadoc에 `@VolumeRankingSemantics` 의미 차이 주석 기재됨.
- KRX 직접 연동(KRX_ID/PW 세션 로그인) 시 대체 어댑터를 동일 포트로 플러그인 가능.

### 3. 라이브 재선정 동작 (SC3/SC4)

- **매 틱 재선정 (SC3):** `LiveDataScheduler.collectLiveData()` → `VolumeRankRefresher.refreshIfVolumeTopN(today)` 호출 → `activeSymbolsUnion()` 직전에 paper_live_symbols 갱신.
- **volume_top_n 룰만 대상 (Pitfall 3):** `universe.type == "volume_top_n"`인 RUNNING 룰만 재선정. symbols/watchlist 타입 룰의 종목 목록은 건드리지 않음.
- **보유 포지션 종목 유지 (SC4, Pitfall 2):** 재선정 집합 = 새 top-N ∪ PaperPositionRepository 보유 종목. top-N에서 이탈했지만 보유 포지션 있는 종목은 paper_live_symbols에 계속 포함 → 수집 및 청산 평가 유지.
- **진입/청산 분리:** 진입(BUY)은 현재 top-N 멤버에만 허용 (`LiveEvaluationService.buildEntrySet()` + `evaluateSymbol()` 게이팅). 청산(SELL)은 보유 종목 전체에 항상 평가 — entrySet 체크 없음.

### 4. ETF/ETN 제외 구현 방식

| 경로 | 방식 |
|------|------|
| 백테스트 (DbVolumeRankingAdapter) | JPQL `JOIN Company c ON c.ticker = b.symbol WHERE c.instrumentType = 'COMMON_STOCK'` |
| 라이브 (YahooCumulativeVolumeAdapter) | JPQL `JOIN Company c ON c.ticker = m.symbol WHERE c.instrumentType = 'COMMON_STOCK'` |
| V36 마이그레이션 | `ALTER TABLE companies ADD COLUMN IF NOT EXISTS instrument_type VARCHAR(20) NOT NULL DEFAULT 'COMMON_STOCK'` |

### 5. 캐시 설계 (YahooCumulativeVolumeAdapter)

- 1분 TTL 수동 캐시 (`volatile List<String> + Instant`) — Spring Cache 인프라 불필요.
- 빈 fetch → 캐시 미갱신 (RESEARCH Pitfall 5 — stale-empty poisoning 방지). 다음 틱에 재시도.
- 예외 발생 → stale 캐시 반환 (또는 초기값 빈 리스트). 예외를 상위로 전파하지 않음.

### 6. 데이터 기준 차이 Deferred 항목

| 항목 | 이유 | 향후 처리 |
|------|------|----------|
| KRX 직접 인트라데이 연동 | HTTP 400 LOGOUT (인증 필요) | KRX_ID/PW 환경변수 로그인 세션 구현 시 동일 포트 플러그인 |
| 백테스트 거래량 기준 재정렬 | V28 이전 분봉 데이터 없음, 비용 과다 | 별도 Phase에서 분봉 히스토리 확보 후 검토 |
| 라이브 KRX 랭킹 정확도 검증 | 장중에만 수동 확인 가능 | verify-work 게이트 시 장중 smoke test |

**UI/UX: 해당 없음 — 백엔드 데이터 인프라 페이즈.**

---

## [v1.4.0] 룰 설정/운영 역할 분리 & 매매 근거 (Role Split & Trade Rationale)

> Status: 백엔드 완료 · 프론트엔드 완료 · 인수 육안 검증 대기 · 2026-06-22
> 의존: v1.3.0(모의투자 실행 엔진 + 룰 에디터) 완료 위에 구축

**Phase 6.5 전체 구현 요약:**

1. **2축 상태 모델 (Plan 01):** `trading_rules` 테이블에 `config_status(DRAFT|ACTIVE)` × `run_status(STOPPED|RUNNING)` 컬럼 추가(V35 Flyway). `activate/deactivate/start/stop` 4개 엔드포인트. RUNNING 중 편집 차단 가드.

2. **EvalResult 리치 반환 (Plan 02):** `RuleEvaluator.evalEntry/evalExit`가 조건별 지표값·통과여부·청산사유 포함 `EvalResult` 반환. 기존 boolean 메서드는 위임으로 하위 호환 유지.

3. **백테스트 근거 계측 (Plan 03):** `IntradayBacktestEngine`에서 `EvalResult` 포착 → `buildRationale()` → `BacktestResult.TradeDto.rationaleJson` 인라인 운반.

4. **모의 근거 계측 + MON-05 (Plan 04):** `LiveEvaluationService.mergeRationale()` → `paper_signal_log.indicator_snapshot`에 rationale 블록 병합. `PaperHistoryService` JOIN으로 per-trade rationale 조회. `PaperExecutor` ±29.5% 상하한가 PRICE_LIMIT_PENDING 감지.

5. **프론트엔드 역할 분리 + 아코디언 (Plan 05 — 현재):**
   - `전략 설정(/paper/rules)` = configStatus 배지 + activate/deactivate 토글만 노출. run 제어 없음.
   - `전략 운영(/paper/rules-lifecycle)` = ACTIVE 룰 필터 + runStatus 배지(실행 중/중지됨) + 시작/중지만.
   - `TradeRationaleRow` 공유 컴포넌트: 조건식 + 지표값 + ✓/✗ + 청산 사유.
   - `PaperBacktestPage` / `PaperHistoryPage`: 행 클릭 → 아코디언 근거 확장.

### RULE-08 성공 기준
- SC1: 전략 설정 화면 = config축(DRAFT/ACTIVE)만 제어 ✓
- SC2: 전략 운영 화면 = run축(STOPPED/RUNNING)만 제어, ACTIVE 룰만 표시 ✓
- SC3: 두 화면이 동일 상태를 중복 표시하지 않음 ✓

### RULE-09 성공 기준
- SC4: 백테스트 거래 행 클릭 → `RSI(14)=28.3 < 30.00 ✓` 형식 근거 아코디언 ✓
- SC5: 모의 거래 이력 행 클릭 → 동일 형식 근거 아코디언 (동일 TradeRationaleRow) ✓

---

## [v1.3.0] 모의투자 실행 엔진 (Paper Trading Engine)

> Status: 부분 구현 (룰 정의/저장/검증/CRUD + 룰 에디터 완료, 실행 엔진 미진행) · 2026-06-17
> 의존: v1.2.0(모드 토글/영속) 완료 위에 구축
>
> **구현 완료 슬라이스 1 (2026-06-17):** 8절 룰 definition 스키마 확정 후 저장/검증/편집 계층 — V27 `trading_rules` 테이블, `TradingRule` 엔티티/리포지토리, `RuleDefinition` 레코드, `RuleDefinitionValidator`, `PaperRuleService`/`PaperRuleController`(`/api/v1/trading/paper/rules` CRUD), 프론트 `types/trading.ts`·`lib/ruleApi.ts`·`PaperRulesPage` 룰 에디터.
> **구현 완료 슬라이스 2 (2026-06-17):** 엔진 코어 + 백테스트 경로 — V28 `paper_*` 테이블, `trading/engine`(`Bar`, `Indicators` SMA/EMA/RSI, `RuleEvaluator`, `FillSimulator`, `PaperLedger`, `MarketDataPort`+`YahooMarketDataAdapter`), `BacktestService`/`BacktestController`(`POST /api/v1/trading/paper/backtest`), 프론트 `PaperBacktestPage`(룰·기간·초기자본 → 수익률/MDD/승률/거래목록). 데이터 소스가 종가만 제공 → VOLUME 지표는 데이터 있을 때만 평가. (백엔드 컴파일·프론트 빌드 통과)
> **구현 완료 슬라이스 3 (2026-06-17):** 시세 적재 계층 — Yahoo 클라이언트 OHLCV/분봉 파싱 확장(`fetchDailyOhlcv`, `fetchIntraday`), V29 `market_bars`/`market_bars_intraday` 테이블, `MarketDataIngestionService`(룰 유니버스 종목만 수집·upsert), `DbMarketDataAdapter`(@Primary, DB 조회 + 비어있으면 즉석 적재 self-healing), 보안 내부 엔드포인트 `POST /internal/market/ingest?interval=EOD|MINUTE`(X-Internal-Token 공유 시크릿). 백테스트는 이제 DB 적재분을 사용. **운영 설정 필요:** 환경변수 `GRAPHIFY_INTERNAL_TOKEN`, Cloud Scheduler 2개(EOD 일1회 / MINUTE 장중 N분, 헤더 X-Internal-Token).
> **미구현:** 포워드 tick(`PaperTickService` + 내부 tick 엔드포인트), `paper_*` 엔티티/영속, 모의 대시보드/이력/리포트 페이지, 모의→실거래 승격 플로우, 슬리피지/REALTIME.

### 1. 목표 & 핵심 원칙
웹에서 룰을 편집하면서 가상 자금으로 매매를 시뮬레이션할 수 있는 엔진을 만든다.

- **룰 정의와 룰 실행을 분리** — 룰은 DB의 설정 데이터, 엔진은 매 평가 tick마다 최신 룰을 읽어 평가. 따라서 **웹에서 룰을 바꾸면 다음 tick부터 자동 반영**(실시간 편집은 구조적으로 지원).
- **단일 룰 엔진을 백테스트 + 포워드테스트가 공유** — 입력 데이터(과거/실시간)만 교체.
- **모의/실거래 룰 동일 테이블** — `mode` 컬럼 구분, 모의→실거래 승격 지원.
- **평가 주기는 옵션화** — EOD / 분(폴링) / 준실시간 셋 다 선택 가능.

### 2. 실행 모델
| 모델 | 입력 데이터 | 트리거 | 용도 |
|------|------------|--------|------|
| **백테스트** | 과거 봉(historical bars) | 사용자 on-demand 요청 | 룰 튜닝·검증, 즉시 결과 |
| **포워드테스트(실시간 모의)** | 장중 실시간 시세 | 스케줄러 tick | 실제 매매 감각 검증 |

두 모델 모두 동일한 **RuleEvaluator**(룰 조건 평가 → 주문 신호 생성)와 **FillSimulator**(현재가+슬리피지로 체결) + **PaperLedger**(가상 장부 갱신)를 호출. 차이는 "어떤 시점의 가격을 먹이느냐"뿐.

### 3. 평가 주기 옵션 (계정별 설정)
`paper_accounts.eval_interval` 컬럼으로 계정마다 선택:
| 값 | 의미 | 구현 |
|----|------|------|
| `EOD` | 장 마감 후 1회 | Cloud Scheduler 일 1회 (장마감 시각) |
| `MINUTE` | 1~5분 폴링 | Cloud Scheduler N분 간격 (장중) |
| `REALTIME` | 준실시간(초~수십초) | min-instances=1 + `@Scheduled`, 또는 스트림(후속) |

- 공통 진입점: 보안 내부 엔드포인트 `POST /internal/paper/tick?interval=MINUTE` → 해당 주기 계정만 평가.
- Cloud Scheduler가 주기별로 서로 다른 cron으로 이 엔드포인트를 호출 (OIDC 인증 헤더).
- REALTIME은 비용/운영 부담 큼 → MVP는 EOD·MINUTE 먼저, REALTIME은 옵션 노출 후 후속 구현.

### 4. Cloud Run 제약 대응
- Cloud Run은 요청 없으면 0으로 스케일다운 → 상주 루프 비신뢰. **Cloud Scheduler → 엔드포인트 폴링** 채택.
- 내부 엔드포인트는 Scheduler의 OIDC 토큰 또는 공유 시크릿 헤더로 보호 (외부 차단).
- REALTIME 선택 계정이 존재할 때만 min-instances=1 고려 (비용 트레이드오프).

### 5. 데이터 모델 (신규 테이블, 초안)
```sql
-- 룰 (모의/실거래 공용)
trading_rules(
  id, user_id, name, mode VARCHAR(8),        -- PAPER | LIVE
  definition JSONB,                          -- 조건/액션 룰 트리
  status VARCHAR(16),                        -- DRAFT|ACTIVE|PAUSED
  promoted_from BIGINT NULL,                 -- 승격 출처 룰 id
  created_at, updated_at
)

-- 가상 계좌
paper_accounts(
  id, user_id, base_cash NUMERIC, cash NUMERIC,
  eval_interval VARCHAR(8),                  -- EOD|MINUTE|REALTIME
  status VARCHAR(16), created_at, updated_at
)

paper_positions(id, account_id, symbol, qty, avg_price, updated_at)
paper_orders(id, account_id, rule_id, symbol, side, qty, price,
             status, simulated_at)          -- 가상 주문/체결
paper_trades(id, account_id, rule_id, symbol, side, qty, price,
             pnl, traded_at)                -- 체결 이력 → 모의 거래 이력 화면
paper_equity_snapshots(id, account_id, ts, equity, cash)  -- 성과 그래프
```

### 6. 엔진 컴포넌트 (백엔드)
- `RuleEvaluator` — 룰 definition + 시세 → 주문 신호 (백테스트/포워드 공용)
- `FillSimulator` — 신호 → 가상 체결 (현재가, 슬리피지/수수료 모델)
- `PaperLedger` — 체결 반영(현금/포지션/평가손익), 스냅샷 기록
- `PaperTickService` — 주기별 활성 계정 순회 → 위 3개 호출 (포워드)
- `BacktestService` — 과거 봉 재생으로 동일 엔진 호출 (on-demand)
- `MarketDataPort` — 기존 market service 어댑터(실시간/과거 조회 추상화)
- `PaperController` — 룰 CRUD, 백테스트 실행, 계좌/포지션/이력 조회 API
- `InternalPaperController` — `POST /internal/paper/tick` (스케줄러 전용, 보안)

### 7. 프론트 연동 (기존 플레이스홀더 대체)
- 모의 룰 설정: 룰 편집기 → `trading_rules`(mode=PAPER) CRUD
- 백테스트: 룰+기간 선택 → 결과(수익률/MDD/승률/거래목록)
- 모의 대시보드: `paper_accounts` 잔고/포지션 + equity 그래프
- 모의 거래 이력: `paper_trades`
- 모의 성과 리포트: 스냅샷 기반 지표

### 8. 룰 definition 스키마 (JSONB) — 확정

룰은 **유니버스 + 진입조건 + 청산조건 + 사이징 + 제약**으로 구성. `trading_rules.definition`에 JSONB로 저장.

```jsonc
{
  "version": 1,
  "universe": {
    "type": "symbols",            // symbols | watchlist
    "symbols": ["005930", "000660"]  // type=symbols일 때
  },
  "entry": {
    "logic": "AND",               // AND | OR
    "conditions": [               // Condition[]
      { "left": { "indicator": "RSI", "params": { "period": 14 } },
        "op": "<",
        "right": { "value": 30 } },
      { "left": { "indicator": "PRICE" },
        "op": "crossAbove",
        "right": { "indicator": "SMA", "params": { "period": 20 } } }
    ]
  },
  "exit": {
    "logic": "OR",
    "conditions": [
      { "left": { "indicator": "RSI", "params": { "period": 14 } },
        "op": ">", "right": { "value": 70 } }
    ],
    "takeProfitPct": 5.0,         // optional, 진입가 대비 +%
    "stopLossPct": -3.0           // optional, 진입가 대비 -%
  },
  "sizing": {
    "type": "cash",               // cash | percent | qty
    "value": 1000000              // cash=원, percent=현금비중%, qty=주수
  },
  "constraints": {
    "maxPositionsPerSymbol": 1,   // 동일 종목 중복 진입 제한
    "cooldownBars": 1             // 청산 후 재진입 대기 bar 수
  }
}
```

**Operand (피연산자)** — `left`/`right`에 사용
| 형태 | 의미 |
|------|------|
| `{ "value": number }` | 상수 |
| `{ "indicator": "PRICE" }` | 최근 종가 |
| `{ "indicator": "SMA", "params": { "period": n } }` | 단순이동평균 |
| `{ "indicator": "EMA", "params": { "period": n } }` | 지수이동평균 |
| `{ "indicator": "RSI", "params": { "period": n } }` | RSI |
| `{ "indicator": "VOLUME" }` | 최근 거래량 |

- **MVP 지표**: PRICE, SMA, EMA, RSI, VOLUME
- **후속 지표**: MACD, BBANDS, ATR, STOCH (스키마 동일 — `indicator`만 추가)

**Operator (연산자)**
| op | 의미 |
|----|------|
| `>`,`>=`,`<`,`<=`,`==` | 값 비교 (해당 bar 기준) |
| `crossAbove` | left가 직전 bar엔 right 이하 → 현재 bar에 초과 (상향 돌파) |
| `crossBelow` | 하향 돌파 |

> `crossAbove/Below`는 직전 bar 값이 필요 → 엔진이 직전/현재 두 시점을 평가.

**평가 규칙**
- `entry.logic`로 진입 조건들을 AND/OR 결합 → 참이고 미보유면 매수(sizing대로)
- 보유 중일 때 `exit.conditions`(logic 결합) 또는 `takeProfitPct`/`stopLossPct` 중 하나라도 충족 시 청산
- 조건 평가 시점 = 엔진 tick (백테스트는 각 과거 bar, 포워드는 주기 tick)

**검증(서버)**: `version=1`, 알 수 없는 indicator/op 거부, period>0, sizing.value>0, symbols 비어있지 않음(type=symbols), 중첩 깊이 제한.

### 9. 미결정 사항
- 슬리피지·수수료 모델 디테일 (MVP: 슬리피지 0, 수수료 0.015% 가정 등)
- 백테스트 과거 봉 데이터 소스/보관 범위
- REALTIME 스트리밍 도입 시점
- 다지표 조합 외 멀티-leg(피라미딩) 지원 여부 — 현재 스키마는 단일 진입/청산

### 10. 단계별 구현 순서 (제안)
1. 스키마: `trading_rules` + `paper_*` 테이블 (Flyway V27~)
2. 룰 엔진 코어(`RuleEvaluator`/`FillSimulator`/`PaperLedger`) + 단위 테스트
3. 백테스트 경로(`BacktestService` + API + 프론트) — 즉시 피드백 먼저
4. 포워드 tick(`PaperTickService` + 내부 엔드포인트) + Cloud Scheduler(EOD→MINUTE)
5. 모의 페이지 5종 실제 구현(플레이스홀더 대체)
6. 모의→실거래 승격(promote) 흐름
7. (후속) REALTIME 옵션

---

## [v1.2.0] 모의투자(Paper Trading) 모드

> Status: 구현 완료 (Phase 1 — UI/토글/모드 영속) · 2026-06-17
> 미구현: 모의 체결 엔진, 백테스트 연산, 모의 페이지는 플레이스홀더 상태

### 1. 목표 & 범위
자동매매 트레이딩 페이지에 **모의투자 모드**를 도입한다.
- 트레이딩 사이드바 하단에 **모의투자 ON/OFF 토글** 배치
- 토글 상태는 **유저별로 백엔드에 영속** (재접속·기기 변경 시 유지)
- 모드에 따라 사이드바 **메뉴 구성이 전환** — 모의투자 ON 시 **실거래 전용 메뉴는 숨기고 모의투자 전용 메뉴**를 노출
- 실거래(LIVE) 전환은 위험 동작이므로 **확인 절차** 포함

**비범위:** 실제 모의 체결 엔진 / 가상 잔고 정산, 백테스트 연산 엔진, 토스증권 실거래 API 연동

### 2. 핵심 개념: 모드 모델
기존 `trading_enabled`(접근 권한)와 **모드(mode)** 를 별개 개념으로 분리한다.

| 개념 | 의미 | 저장 |
|------|------|------|
| `trading_enabled` | 트레이딩 페이지 진입 권한 (관리자가 부여) | 기존 컬럼 |
| `trading_mode` | 진입 후 현재 동작 모드 (PAPER / LIVE) | **신규 컬럼** |

```
trading_enabled = false  → /gg 진입 불가
trading_enabled = true   → 진입 가능
   ├─ trading_mode = PAPER (토글 ON, 기본) → 모의투자 메뉴
   └─ trading_mode = LIVE  (토글 OFF)       → 실거래 메뉴
```
- 기본값 **PAPER** (안전 우선). 유저가 명시적으로 토글을 꺼야 LIVE 전환
- UI 표기: 토글 "모의투자 ON" = `PAPER`, "모의투자 OFF" = `LIVE`

### 3. 메뉴 구성
**공통 (모드 무관, 항상 노출)**
| 경로 | 라벨 | 비고 |
|------|------|------|
| `/trading` | DDS Agent | 채팅. 현재 모드를 컨텍스트로 인지 |

**실거래(LIVE) 전용**
| 경로 | 라벨 |
|------|------|
| `/trading/dashboard` | 대시보드 |
| `/trading/history` | 거래 이력 |
| `/trading/rules` | 현재 룰 |
| `/trading/rules/edit` | 룰 수정 |
| `/trading/monitor` | 동작 모니터링 |

**모의투자(PAPER) 전용**
| 경로 | 라벨 | 설명 |
|------|------|------|
| `/trading/paper/dashboard` | 모의 대시보드 | 가상 잔고·포지션 요약 |
| `/trading/paper/history` | 모의 거래 이력 | 모의 체결 내역 |
| `/trading/paper/rules` | 모의 룰 설정 | 모의 환경 룰 편집 (실거래 룰과 분리) |
| `/trading/paper/backtest` | 백테스트 | 과거 데이터 기반 룰 검증 |
| `/trading/paper/report` | 모의 성과 리포트 | 수익률·MDD·승률 등 |

**모드 전환 시 라우팅**
- 현재 경로가 반대 모드 전용이면 해당 모드 기본 페이지로 리다이렉트 (PAPER→`/trading/paper/dashboard`, LIVE→`/trading/dashboard`)
- 공통 경로(`/trading`)는 리다이렉트 안 함
- 반대 모드 경로 직접 접근(딥링크) 시 가드로 현재 모드 기본 페이지로 이동

### 4. 백엔드 설계
**DB 마이그레이션 (V26)** — `db/migration/V26__trading_mode.sql`
```sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS trading_mode VARCHAR(8) NOT NULL DEFAULT 'PAPER';
```
- 값 `'PAPER'` | `'LIVE'`, 기본 `'PAPER'` (기존 유저 전원 안전 모드 초기화)

**엔티티 / DTO**
- `User.java`: `tradingMode` 필드 (선택: `enum TradingMode {PAPER, LIVE}` + `@Enumerated(EnumType.STRING)`)
- `UserMeDto`: `tradingMode` 응답 추가 → `/api/v1/users/me`
- 신규 `TradingModeRequest(String mode)`

**API**
| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/users/me` | 응답에 `tradingMode` 추가 |
| PUT | `/api/v1/trading/mode` | `{ "mode": "PAPER"\|"LIVE" }`, 인증 필요 |

**검증:** `trading_enabled=false` → 403, 잘못된 mode → 400, 응답은 갱신된 `{ tradingEnabled, tradingMode }`. 신규 `TradingController` + `TradingSettingsService.setMode(userId, mode)`.

### 5. 프론트엔드 설계
**상태 (`tradingStore.ts` 확장)**
```ts
type TradingMode = "PAPER" | "LIVE";
interface TradingState {
  darkMode: boolean;
  enableDarkMode: () => void;
  disableDarkMode: () => void;
  mode: TradingMode;                 // 기본 "PAPER"
  setMode: (m: TradingMode) => void; // 낙관적 업데이트
}
```
- 진입 시 `fetchUserMe()`로 서버 `tradingMode` 동기화
- 토글 클릭 → `setMode` 낙관적 반영 → `PUT /trading/mode` → 실패 시 롤백

**API (`lib/tradingApi.ts` 신규)**
```ts
export async function updateTradingMode(mode: "PAPER" | "LIVE") {
  return apiPut<TradingSettings, { mode: string }>("/api/v1/trading/mode", { mode });
}
```

**사이드바 토글**
- 위치: 사이드바 하단 "메인으로" 버튼 위, 구분선으로 분리
- UI: "모의투자" 라벨 + 스위치(AdminUsersPage 토글 스타일 재사용), 모드 뱃지(PAPER=emerald/amber, LIVE=red "실거래 중")
- 동작: PAPER→LIVE(OFF)는 **확인 모달**("실거래 모드로 전환합니다. 실제 자금이 사용될 수 있습니다."), LIVE→PAPER(ON)는 즉시 적용
- 데스크탑+모바일 드로어 양쪽 적용 → **`SidebarFooter` 공통 컴포넌트 추출**

**메뉴 렌더링**
```ts
const navItems = [...commonItems, ...(mode === "PAPER" ? paperItems : liveItems)];
```

**라우터 / 타입**
- `/trading/paper/*` 경로 그룹 추가 + 모드 가드(`<Navigate>`)
- `types/user.ts` `UserMe.tradingMode` 추가

### 6. 신규/변경 파일 요약
**백엔드:** `V26__trading_mode.sql`(신규), `User.java`/`UserMeDto.java`/`UserProfileService.java`(수정), `trading/dto/TradingModeRequest.java`·`trading/TradingController.java`·`trading/TradingSettingsService.java`(신규)
**프론트:** `tradingStore.ts`(수정), `lib/tradingApi.ts`(신규), `TradingLayout.tsx`(수정), `components/trading/PaperTradingToggle.tsx`(신규), `pages/trading/paper/*.tsx`(신규 5종), `router/index.tsx`(수정), `types/user.ts`(수정)

### 7. 안전 / 엣지 케이스
1. **LIVE 전환 확인** — 토글 OFF 시 확인 모달 필수
2. **권한 회수 동기화** — 관리자가 `trading_enabled` 끄면 다음 진입 차단, 진행 세션은 다음 API에서 403
3. **딥링크 가드** — 반대 모드 경로 직접 접근 시 현재 모드 기본 페이지로 리다이렉트
4. **낙관적 롤백** — `PUT /trading/mode` 실패 시 토글 원복 + 토스트
5. **DDS Agent 컨텍스트** — 채팅 Agent가 현재 모드 인지(추후 API에 mode 전달)
6. **기본값 PAPER** — 실거래는 의식적 전환으로만 진입

### 8. 결정/미결정 사항
- **[결정] 룰 저장 방식**: 모의/실거래 룰을 **동일 테이블**에 두고 `mode` 컬럼(PAPER/LIVE)으로 구분.
  모의에서 검증한 룰을 실거래로 승격(promote)할 수 있도록 설계 (예: 룰 복제 + mode 변경, 또는 `promoted_from` 참조).
- **[설계됨] 모의투자 실행 방식**: 별도 항목 **[v1.3.0] 모의투자 실행 엔진** 참조.

### 9. 단계별 구현 순서
1. 백엔드: V26 + `User`/`UserMeDto`/`UserProfileService`
2. 백엔드: `TradingController` + `TradingSettingsService` + 검증
3. 프론트: `tradingStore` + `tradingApi` + 타입
4. 프론트: `PaperTradingToggle` + `SidebarFooter` 추출 + `TradingLayout` 메뉴 분기
5. 프론트: `/trading/paper/*` 라우트 + 플레이스홀더 + 모드 가드
6. 검증: 토글→DB→재접속 유지→메뉴 전환→딥링크 가드
7. 배포 (Cloud Run 자동, Flyway V26 적용 확인)

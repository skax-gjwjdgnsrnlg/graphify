# Phase 3 Context: PAPER_LIVE 평가 엔진

**Phase:** 03-paper-live-engine
**Depends on:** Phase 2 (LiveDataScheduler, PaperLiveSymbolService, MarketDataPort.recentIntradayBars)
**Requirements:** LIVE-05, LIVE-06, MON-04

---

## Goal

LiveDataScheduler가 5분마다 수집한 인트라데이 봉을 소비하여 PAPER_LIVE 룰을 실시간으로 평가하고, 가상 체결을 실행하며, 계좌 스냅샷과 신호 로그를 DB에 저장한다.

---

## What Phase 2 Provides

- `LiveDataScheduler.collectLiveData()` — 5분마다 실행, PAPER_LIVE 종목 5분봉 수집 완료
- `PaperLiveSymbolService.activeSymbolsUnion()` — 현재 PAPER_LIVE 룰의 종목 합집합
- `MarketDataPort.recentIntradayBars(symbol)` — 당일 최신 5분봉 목록 반환
- `MarketBarIntradayRepository.findMaxTsBySymbolAndInterval()` — staleness check
- `KrxMarketCalendar.isTradingDay()` — 거래일 판단

## What Already Exists (Engine Layer)

- `RuleEvaluator.entryTriggered() / exitTriggered()` — 조건 평가 (closes[], volumes[], index i)
- `PaperLedger` — 현금/포지션/거래 인메모리 장부 (buy/sell/equity)
- `FillSimulator` — 체결가 계산 (슬리피지 0, 수수료 0.015%)
- `Signal` enum — BUY / SELL / HOLD
- `TradingRule` entity + `TradingRuleRepository`
- `PaperRuleService` — 룰 CRUD (mode=PAPER)

## What Exists in DB (V28)

- `paper_accounts` (id, user_id, base_cash, cash, eval_interval, status)
- `paper_positions` (id, account_id, symbol, qty, avg_price, updated_at)
- `paper_orders` (id, account_id, rule_id, symbol, side, qty, price, status, simulated_at)
- `paper_trades` (id, account_id, rule_id, symbol, side, qty, price, pnl, traded_at)
- `paper_equity_snapshots` (id, account_id, ts, equity, cash)

## What Needs to Be Built (Phase 3)

### Plan 03-01: OrderExecutorPort + PaperExecutor + paper_signal_log migration

1. **V32 migration**: `paper_signal_log` 테이블 (rule_id, symbol, ts, signal, indicator_snapshot jsonb, executed BOOLEAN)
2. **JPA entities**: PaperAccount, PaperPosition, PaperTrade, PaperEquitySnapshot (V28 테이블 매핑)
3. **Repositories**: PaperAccountRepository, PaperPositionRepository, PaperTradeRepository, PaperEquitySnapshotRepository, PaperSignalLogRepository
4. **OrderExecutorPort**: 인터페이스 — `execute(signal, rule, symbol, price, ts) → TradeResult`
5. **PaperExecutor**: implements OrderExecutorPort — DB 기반 포지션 관리 (load → update → flush)

### Plan 03-02: LiveEvaluationService + 스케줄러 틱 통합

1. **LiveEvaluationService**: 틱마다 모든 PAPER_LIVE 룰 평가
   - `recentIntradayBars()` 로 최신 5분봉 조회
   - `RuleEvaluator`로 진입/청산 조건 평가
   - `PaperExecutor`로 가상 체결
   - `paper_signal_log`에 지표 스냅샷 기록
   - `paper_equity_snapshots`에 평가금액 저장
2. **LiveDataScheduler 확장**: `collectLiveData()` 마지막에 `LiveEvaluationService.evaluateTick()` 호출

---

## Design Decisions

### 계좌-룰 관계
- `paper_accounts`는 user당 1개 (1:1 단순화). 룰 여러 개가 같은 계좌 공유.
- 포지션은 symbol 기준 단일 포지션 (account_id + symbol UNIQUE).
- 룰별 분리 계좌는 Phase 4+ 확장 사항.

### 평가 순서 (per tick)
```
LiveDataScheduler.collectLiveData()
  → ingestIntraday() per symbol (Phase 2 기존)
  → LiveEvaluationService.evaluateTick()
      → for each PAPER_LIVE rule:
          → parse RuleDefinition
          → for each symbol in rule:
              → recentIntradayBars(symbol) → closes[], volumes[]
              → staleness check (skip if > 10m)
              → RuleEvaluator.entryTriggered() OR exitTriggered()
              → signal → PaperExecutor.execute()
              → PaperSignalLog 기록
          → PaperEquitySnapshot 기록
```

### 지표 스냅샷 (MON-04)
- `paper_signal_log.indicator_snapshot` JSONB: `{"rsi": 68.2, "sma20": 72500.0, "price": 73000.0}`
- `Indicators.rsi() / sma()` 호출해 마지막 봉 기준 계산 후 직렬화

### Write-through (LIVE-06 요구)
- 틱 시작 시 DB에서 포지션/현금 로드 → 평가 → 틱 종료 시 DB flush
- 인스턴스 재시작 후에도 포지션 유지

### Sizing
- `RuleDefinition.Sizing.type = "fixed_cash"` → `value` 원어치 매수
- `type = "full_cash"` → 사용 가능 현금 전액 (단순화)
- qty = floor(allocCash / price)

---

## Key Interfaces (Phase 3 will implement)

```java
// OrderExecutorPort.java
public interface OrderExecutorPort {
    TradeResult execute(Signal signal, TradingRule rule, String symbol, double price, Instant ts);
}

// PaperSignalLog entity fields:
// rule_id, symbol, ts, signal (BUY/SELL/HOLD), indicator_snapshot (jsonb), executed (boolean)
```

---

## Conventions (follow existing project patterns)

- `@Transactional` on service methods, NOT on scheduler
- `@DataJpaTest` with `spring.flyway.enabled=false`, `ddl-auto=create-drop`, `MODE=PostgreSQL` for JPA tests
- Mockito `@ExtendWith(MockitoExtension.class)` for unit tests
- Packages: entities → `com.graphify.trading.paper`, service → `com.graphify.trading.paper`
- Logger: `LoggerFactory.getLogger(ClassName.class)` pattern

# Architecture Research: Real-time Trading Engine

*Researched: 2026-06-20*
*Confidence: MEDIUM — Toss Securities API details from web search (official docs confirmed REST+Client Credentials, WebSocket status LOW confidence); Spring patterns HIGH confidence from official Spring docs + codebase evidence.*

---

## Component Overview

The existing system already defines the right abstraction boundary. The key insight is that `RuleEvaluator` is already data-source-agnostic — it operates on `double[] closes` and `Double[] volumes` arrays, not on any data source directly. The only thing that changes between backtest and live modes is what fills those arrays.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SHARED ENGINE LAYER                          │
│   RuleEvaluator (stateless @Component — zero changes needed)       │
│   FillSimulator · Indicators · Bar record                           │
└───────────────────────┬─────────────────────────────────────────────┘
                        │ double[] closes, Double[] volumes
          ┌─────────────┴──────────────┐
          ▼                            ▼
┌─────────────────────┐    ┌──────────────────────────┐
│   BacktestService   │    │  LiveEvaluationService   │  (NEW)
│   (batch, one-shot) │    │  (@Scheduled, per-tick)  │
│   PaperLedger       │    │  DbPaperAccountAdapter   │
│   (in-memory)       │    │  (DB-backed positions)   │
└─────────────────────┘    └──────────┬───────────────┘
                                      │
               ┌──────────────────────┴──────────────────┐
               ▼                                         ▼
   ┌─────────────────────────┐            ┌──────────────────────────┐
   │  OrderExecutorPort      │◄──────────►│  MarketDataPort          │
   │  (interface — NEW)      │            │  (interface — EXISTS)    │
   └────────┬────────────────┘            └────────┬─────────────────┘
            │                                      │
   ┌────────┴────────┐               ┌─────────────┴──────────────┐
   │ PaperExecutor   │               │ DbMarketDataAdapter        │
   │ (writes to DB   │               │ (EXISTS — historicalDaily) │
   │  paper_trades)  │               │                            │
   │  (NEW)          │               │ LiveIntradayAdapter        │
   ├─────────────────┤               │ (intraday from DB,         │
   │ TossOrderExec.  │               │  ingested by scheduler)    │
   │ (calls Toss API)│               │  (NEW)                     │
   │  (LIVE phase)   │               └────────────────────────────┘
   └─────────────────┘
```

### Data flow direction (PAPER_LIVE mode, per scheduler tick)

```
@Scheduled tick (every 5 min, market hours only)
  → MarketDataIngestionService.ingestIntraday()     // fetch latest bar from Yahoo
  → LiveEvaluationService.evaluateAll()
      → load active PAPER_LIVE rules from DB
      → for each rule: load last N daily + today's intraday bars → build closes[]
      → RuleEvaluator.entryTriggered / exitTriggered (UNCHANGED)
      → signal → PaperExecutor.execute()
          → write paper_orders + paper_trades + update paper_positions + paper_accounts
  → paper_equity_snapshots.insert()                 // end of each tick
```

---

## MarketDataPort Adapter Strategy

### Current state

`MarketDataPort` has one method: `historicalDailyBars(String symbol)`. `DbMarketDataAdapter` is `@Primary` and implements it by reading from `market_bars` (DB), with self-healing ingest from Yahoo Finance.

### What must be added

The interface needs a second method for intraday-last bars:

```java
public interface MarketDataPort {
    List<Bar> historicalDailyBars(String symbol);           // EXISTS
    List<Bar> recentIntradayBars(String symbol, String interval, int lookbackBars);  // ADD
}
```

`recentIntradayBars` reads from `market_bars_intraday` (already in DB via `MarketBarIntraday` entity and V29 migration). The scheduler pre-ingests before calling the evaluator, so the adapter just queries what is already in the DB — no blocking HTTP calls in the evaluation path.

### Adapter instantiation strategy

Do NOT create a second `@Primary` adapter. Add `recentIntradayBars` to `DbMarketDataAdapter` directly (it already has `MarketBarIntradayRepository` available through `MarketDataIngestionService`, or inject it directly). This keeps a single `MarketDataPort` bean and avoids conditional wiring complexity.

### Bar construction for live evaluation

`RuleEvaluator` needs a sufficient window of closes to compute SMA/EMA/RSI lookback. The live adapter must combine:
1. Historical daily closes (N days, where N >= max indicator period in the rule definition)
2. Today's intraday bars converted to a synthetic "current close" appended at the end

The resulting array is passed unchanged to `RuleEvaluator`. No changes to `RuleEvaluator` itself.

---

## Scheduler Design

### Approach: Spring @Scheduled with zone + guard method

Spring `@Scheduled` does not support conditional execution natively, so the pattern is a fixed-rate scheduler that guards internally:

```java
@Scheduled(cron = "0 */5 9-15 * * MON-FRI", zone = "Asia/Seoul")
public void tick() {
    if (!marketCalendar.isMarketOpen()) return;   // holiday guard
    ingestionService.ingestIntradayForActiveSymbols("5m", "1d");
    liveEvaluationService.evaluateAll();
}
```

The cron `0 */5 9-15 * * MON-FRI` fires every 5 minutes, on-the-hour through 15:xx KST weekdays. The `zone = "Asia/Seoul"` attribute on `@Scheduled` (HIGH confidence — standard Spring feature since 4.x) ensures the cron interprets times in KST regardless of JVM timezone.

**Note on 15:30 cutoff:** The cron fires at :00 and :05 intervals up to 15:55 if not bounded. Add a time check: `LocalTime.now(ZoneId.of("Asia/Seoul")).isBefore(LocalTime.of(15, 30))`. The cron expression `0 */5 9-15 * * MON-FRI` fires last at 15:55 without the check, so the guard is mandatory.

### Market holiday handling

Toss Securities Open API includes a Market Info category with holiday data (confirmed by web search, MEDIUM confidence). The recommended pattern:

```
MarketCalendarService (NEW)
  - caches holiday list from Toss API on startup + daily refresh
  - isMarketOpen(): returns false on public holidays
  - fallback: if Toss API unavailable, skip evaluation (fail-safe, never trade on unknown day)
```

This is preferable to hardcoding a holiday list, because KRX occasionally moves holidays and Toss API stays current. Store the fetched holiday list in memory (a `Set<LocalDate>`) refreshed once per day at 08:00 KST.

Alternative for early phases (before Toss API integration): embed a KRX holiday list for the current year as a configuration property. This unblocks development of the scheduler without API dependency.

### @EnableScheduling placement

Add `@EnableScheduling` to the main application class or a `@Configuration` class. Verify `spring.task.scheduling.pool.size` is set to at least 2 if multiple scheduled tasks exist (default is 1 thread, which can cause starvation if one task runs long).

---

## Paper Account State

### Decision: DB-backed with write-through, no periodic flush

**Recommendation: write every fill to DB immediately (write-through), keep no long-lived in-memory state between ticks.**

Rationale from examining the codebase:

1. `PaperLedger` (existing) is instantiated fresh per backtest run — it is a per-invocation object, not a singleton. The same design applies to live evaluation: instantiate it per tick by loading state from DB at the start of the tick.

2. The existing `paper_accounts`, `paper_positions`, `paper_trades`, `paper_orders`, `paper_equity_snapshots` tables (V28) are exactly the right schema for this. They were clearly designed for persistent live paper trading, not just backtest recording.

3. `paper_positions` has a unique index on `(account_id, symbol)` — designed for upsert on position change, not append-only. This is the right pattern for live state.

4. Spring `@Scheduled` tasks run in a thread pool. If state were in-memory between ticks, thread-safety would require synchronization or a ConcurrentHashMap. DB removes this concern entirely.

### Tick execution pattern

```
tick():
  1. Load paper_account by user_id → cash, status
  2. Load paper_positions for this account → Map<symbol, Position>
  3. Build PaperLedger(cash, positions) — a per-tick transient object
  4. Run rule evaluation → PaperLedger.buy() / .sell()
  5. Flush results:
     a. UPDATE paper_accounts SET cash = ?
     b. UPSERT paper_positions (delete on qty=0, upsert on qty>0)
     c. INSERT paper_trades (one row per fill)
     d. INSERT paper_equity_snapshots (equity at this tick)
  All in one @Transactional block.
```

This means `PaperLedger` is still used as a transient computation object exactly as in backtest — no structural change needed. A new `DbPaperAccountAdapter` (or service) handles steps 1-2 (load) and 5 (flush).

### Why not pure in-memory with periodic flush

- Server restart loses all live session state
- Multiple rules per user would need careful isolation
- The DB schema already exists and is designed for persistence
- Evaluation happens every 5 minutes — DB round trip cost is negligible at that frequency

---

## Toss Securities API Flow

### Confirmed facts (MEDIUM confidence — web search, official docs not directly accessible)

**Authentication:**
- OAuth 2.0 Client Credentials Grant
- Endpoint: `POST /oauth2/token` with `client_id` + `client_secret`
- Token passed as `Authorization: Bearer {access_token}`
- Token has a limited validity period (exact duration unclear — treat as short-lived, cache and refresh)
- Account operations require an additional header: `X-Tossinvest-Account: {accountSeq}`

**API scope (26 categories confirmed):**
- Auth, Market Data (시세), Stock Info, Market Info (환율/휴장일), Account (잔고), Order (주문/정정/취소/조회)

**Real-time data:**
- WebSocket: NOT yet available as of June 2026 (confirmed by multiple sources, MEDIUM confidence)
- Polling: REST at 1-second intervals is possible per community sources
- For this system, 5-minute evaluation cycles mean REST polling is entirely sufficient — WebSocket is not needed

**Order fill callbacks:**
- No webhook/callback mechanism found in available documentation
- Pattern: place order via REST, poll order status via REST to confirm fill
- For the evaluation cycle frequency (5 min), synchronous order-then-poll is acceptable

### Token storage architecture

```java
// DB table (new migration required)
CREATE TABLE toss_credentials (
    user_id      BIGINT PRIMARY KEY,
    client_id    TEXT NOT NULL,
    client_secret_enc TEXT NOT NULL,    -- AES-256 encrypted
    access_token_enc  TEXT,             -- AES-256 encrypted, nullable until first auth
    token_expires_at  TIMESTAMPTZ,
    account_seq  TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

`client_secret` and `access_token` must be encrypted at rest. Use a JPA `@Convert` with `AttributeConverter<String, String>` that encrypts/decrypts via AES-256-GCM with a key from `graphify.toss.encryption-key` property (sourced from environment, never committed).

### TossOrderExecutor (LIVE mode only)

```
TossOrderExecutor implements OrderExecutorPort
  - injects TossCredentialService (handles token refresh)
  - on execute(signal, symbol, qty, price):
      1. getValidToken(userId) — refresh if expired
      2. POST /orders with body {symbol, side, qty, orderType: "MARKET"}
      3. Poll GET /orders/{orderId} until status = FILLED (max 3 attempts, 1s apart)
      4. Record actual fill price to live_trades table (separate from paper_trades)
```

### OrderExecutorPort interface (new — enables PAPER/LIVE swap)

```java
public interface OrderExecutorPort {
    FillResult execute(Long ruleId, Signal signal, String symbol, double qty, double referencePrice);
}
```

`PaperExecutor` writes to `paper_trades`. `TossOrderExecutor` calls the Toss API. `LiveEvaluationService` depends on `OrderExecutorPort`, injected by mode.

---

## Rule Promotion Architecture

### State machine

```
DRAFT → BACKTESTED → PAPER_LIVE → LIVE
```

These map to `TradingRule.status` (existing field). The `mode` field (`PAPER` / `LIVE`) on `TradingRule` is separate — it indicates which executor type to use.

### What changes PAPER_LIVE → LIVE

| Aspect | PAPER_LIVE | LIVE |
|--------|-----------|------|
| `TradingRule.status` | `PAPER_LIVE` | `LIVE` |
| `TradingRule.mode` | `PAPER` | `LIVE` |
| `RuleEvaluator` | unchanged | unchanged |
| `MarketDataPort` adapter | unchanged | unchanged |
| `OrderExecutorPort` impl | `PaperExecutor` | `TossOrderExecutor` |
| State store | `paper_accounts` / `paper_trades` | `live_accounts` / `live_trades` (new tables) |
| Ledger class | `PaperLedger` (transient per tick) | same or subclass with real fill price |
| Scheduler | same tick loop | same tick loop — checks `rule.mode` to route executor |

**Key design:** `LiveEvaluationService.tick()` evaluates all active rules and routes each to the correct `OrderExecutorPort` implementation based on `rule.mode`. No separate scheduler for LIVE rules — the same scheduler handles both.

### Promotion flow (API)

```
PATCH /api/paper/rules/{id}/promote
  body: { targetStatus: "PAPER_LIVE" }   // or "LIVE"
  
  validations:
    DRAFT → PAPER_LIVE: requires at least one completed backtest
    PAPER_LIVE → LIVE: requires user to have valid Toss credentials in DB
                       creates a copy of the rule with mode=LIVE (promotedFrom=originalId)
                       original stays at PAPER_LIVE for comparison
```

The `promotedFrom` field on `TradingRule` already exists (V27 schema) — this enables the copy-on-promote pattern without losing the paper record.

---

## Build Order

Dependencies determine this order. Each phase delivers a working vertical slice.

### Phase 1: Live data ingestion + scheduler shell
**Delivers:** Intraday bars flowing into DB on market-hours schedule

- Add `recentIntradayBars()` to `MarketDataPort` and `DbMarketDataAdapter`
- Implement `MarketCalendarService` (hardcoded KRX holiday list for current year as stopgap)
- Add `@Scheduled` ticker in `LiveEvaluationService` — logs "tick" but does not evaluate yet
- Add `@EnableScheduling`, configure thread pool size

Prerequisite for: everything else. No other phase can work without live data arriving.

### Phase 2: Paper account persistence layer
**Delivers:** DB-backed paper positions that survive restart

- Implement `DbPaperAccountAdapter` (load + flush pattern described above)
- Add JPA entities for `paper_accounts`, `paper_positions` (V28 schema already exists, just needs entity classes)
- Wire into `LiveEvaluationService.tick()` as load-evaluate-flush cycle

Prerequisite for: Phase 3 (evaluation needs somewhere to write fills).

### Phase 3: Live rule evaluation engine
**Delivers:** PAPER_LIVE rules auto-evaluated every 5 min during market hours

- Implement `OrderExecutorPort` interface
- Implement `PaperExecutor` (writes to `paper_trades`, updates `paper_positions` via adapter)
- Complete `LiveEvaluationService.evaluateAll()`: load PAPER_LIVE rules, build closes[], call RuleEvaluator, route to PaperExecutor
- Add `paper_equity_snapshots` insert per tick

Prerequisite for: Phase 4 (dashboard needs evaluation running to show data), Phase 5 (promotion needs PAPER_LIVE working first).

### Phase 4: Dashboard + rule lifecycle UI
**Delivers:** Real-time dashboard, performance reports, status transition UI

- REST endpoints for paper account summary, positions, trade history, equity curve
- Frontend PaperDashboardPage (currently empty shell)
- Rule status transition API (`DRAFT → PAPER_LIVE`)

No hard dependency on Phases 5-6 (can build in parallel with Toss work).

### Phase 5: Toss Securities OAuth + credential storage
**Delivers:** Encrypted credential storage, token issuance/refresh

- New migration: `toss_credentials` table
- `TossCredentialService`: store/retrieve encrypted client_id/secret, request/cache access_token
- `AES256AttributeConverter`: encrypt secret fields
- OAuth flow UI: user enters client_id/secret in settings, system validates by requesting a token

Prerequisite for: Phase 6.

### Phase 6: Live order execution + rule promotion to LIVE
**Delivers:** Full PAPER_LIVE → LIVE promotion, real orders placed via Toss API

- Implement `TossOrderExecutor implements OrderExecutorPort`
- New DB tables: `live_accounts`, `live_trades` (mirror of paper schema)
- Rule promotion API (`PAPER_LIVE → LIVE` with Toss credential validation)
- Scheduler routes LIVE rules to `TossOrderExecutor`

---

## Critical Constraints and Risks

### RuleEvaluator window size
The evaluator's crossAbove/crossBelow checks require `i >= 1`. For live intraday evaluation, the closes array must include enough historical bars to satisfy all indicator lookback periods in the rule definition. The live adapter must load `max(entry.period, exit.period) + 2` daily bars minimum. If the rule definition is not parsed to extract max period, use a safe default of 200 bars.

### Yahoo Finance intraday limitations
The current `ingestIntraday` uses Yahoo Finance with `interval=5m, range=1d`. Yahoo imposes rate limits and the 5m data is only available for recent days. This is acceptable for PAPER_LIVE (evaluate last 5m close) but has no fallback if Yahoo throttles. Flag: the Toss Market Data API can serve as intraday data source once integrated in Phase 5, replacing Yahoo for live bars.

### Single-threaded scheduler risk
With `spring.task.scheduling.pool.size=1` (Spring default), if `ingestIntradayForActiveSymbols()` is slow (many symbols, Yahoo rate limit), the 5-minute tick fires late. Set pool size to 2 and measure ingestion time. For MVP, acceptable risk.

### Toss API token expiry during market session
If the access token expires mid-session and refresh fails, LIVE rule evaluation must fail-safe (log, alert, skip — never use stale token). Implement exponential backoff on token refresh with a maximum of 2 retries.

### Order fill price discrepancy (LIVE mode)
`FillSimulator.fillPrice()` currently returns `referencePrice` unchanged (zero slippage). For LIVE mode, the actual fill price from Toss API will differ. `TossOrderExecutor` must record the actual fill price, not the reference price, for accurate P&L tracking. The `live_trades` table should have a separate `fill_price` column distinct from `reference_price`.

---

Sources:
- [토스증권 Open API 가이드](https://developers.tossinvest.com/docs) — official documentation
- [토스증권 Open API 완벽 가이드 2026](https://www.pulse-know.com/toss-invest-open-api-guide-2026/) — community guide confirming OAuth Client Credentials flow and API categories
- [토스증권 Open API 공식 홈페이지](https://corp.tossinvest.com/ko/open-api) — product page confirming 26 API categories
- [토스증권 OpenAPI 오픈](https://braindetox.kr/posts/toss_securities_openapi_2026.html) — confirms REST-only, no WebSocket as of 2026
- [Spring Scheduling Cron Expressions](https://javatechonline.com/spring-scheduling-cron-expression/) — cron syntax and zone attribute
- [KRX Market Hours 2026](https://www.tradinghours.com/markets/krx) — confirms 09:00–15:30 KST, Mon–Fri

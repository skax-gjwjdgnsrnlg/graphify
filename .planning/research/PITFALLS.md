# Pitfalls Research: Korean Stock Auto-Trading

**Project:** Graphify — 모의투자 고도화 & 토스증권 실투자 연동
**Researched:** 2026-06-20
**Confidence:** MEDIUM-HIGH (KRX specifics HIGH from official sources; Toss API specifics MEDIUM from indirect sources)

---

## Korean Market Specifics

### CRITICAL: Trading Calendar Not Checked = Silent Misfires

**What goes wrong:** The scheduler fires at 09:00 on what is a KRX holiday. The market is closed, Yahoo Finance returns empty or stale data, `ingestIntradayForActiveSymbols()` silently succeeds with 0 rows, and the rule evaluator runs against yesterday's prices — possibly triggering buy/sell signals on a closed market.

**Why it happens:** Spring `@Scheduled` has no concept of market calendars. The scheduler will fire on Chuseok, New Year, election days, and any ad-hoc KRX closures with no distinction.

**KRX holidays are not predictable from public holidays alone.** KRX publishes its own calendar separately (e.g., Dec 31 is often a trading day closure not tied to any public holiday). The 2026 calendar includes closures like Mar 2 (Independence Movement Day observed), May 1 (Labour Day — KRX does close), Sep 24-25, Oct 5. These shift year to year.

**Consequences:**
- PAPER_LIVE rules evaluate on stale closes and generate phantom signals
- LIVE rules could emit real orders to Toss API on a day when the exchange is closed — Toss will reject with an error code, but that error must be handled gracefully or the order sits in a retry queue
- Ingestion gaps accumulate silently; historical data has holes that break indicator lookback periods

**Prevention:**
- Maintain a `market_holidays` table seeded via a Flyway migration updated annually from the official KRX schedule
- Add a `KrxMarketCalendar` service with `boolean isTradingDay(LocalDate)` and `boolean isWithinTradingHours(ZonedDateTime)`
- Gate every scheduler entry point: `if (!calendar.isTradingDay(today)) return;`
- Publish an admin endpoint to reload the calendar without redeployment

**Warning signs:** Ingestion logs showing `Daily ingestion done: 0 symbols` on weekdays. Rule evaluation producing orders with timestamps outside 09:00–15:30.

**Phase:** Address in the real-time data ingestion phase, before any scheduler-driven evaluation is wired.

---

### CRITICAL: Price Limit Lock (상한가/하한가) Breaks Exit Logic

**What goes wrong:** A stock hits its ±30% daily price limit and locks. The rule evaluator sees a sell signal (e.g., stop-loss threshold breached), emits a sell order, and either:
- For PAPER mode: the fill simulator fills at the limit price, understating actual loss
- For LIVE mode: Toss rejects the order because the book has no contra side; the position stays open with no acknowledgment of failure

**Why it happens:** The existing `FillSimulator` and `PaperLedger` have no concept of price-locked stocks. The backtest uses historical OHLCV where the price limit already resolved — it cannot simulate the experience of being unable to exit.

**Additional layer:** Volatility Interruptions (VI) cause 2–2.5 minute call auctions mid-session when a stock moves ±10% (static) or ±3% (dynamic) from the VI base. Orders submitted during a VI pause may be queued, re-priced, or silently cancelled depending on Toss API behavior.

**Consequences:** Live rule shows an open position it cannot close. P&L calculation diverges from reality. In worst case, a stop-loss rule loops: evaluates → signals sell → order rejected → re-evaluates next minute → signals sell again → duplicate rejected orders.

**Prevention:**
- In the rule evaluation loop, query current price band before emitting any order: if `abs(signal_price - prev_close) / prev_close >= 0.295`, flag the order as `PRICE_LIMIT_PENDING` rather than sending it
- Add deduplication guard on order emission: per (rule_id, symbol, direction, date), only one pending order allowed at a time (idempotency key pattern)
- For PAPER mode, add a `PriceLimitAwareSimulator` that caps fills at ±30% of prev_close

**Warning signs:** Toss API returning order rejection codes on signals that passed all rule conditions. Repeated identical order attempts for the same (rule, symbol) pair within one session.

**Phase:** Address before LIVE order execution. PAPER simulation can defer to a later improvement.

---

### MODERATE: Stock Splits and Corporate Actions Corrupt Historical Indicators

**What goes wrong:** A stock in a rule's universe undergoes a 1:5 split. Yahoo Finance retroactively adjusts historical prices (adj_close). The `market_bars` table, already populated with pre-split prices, now has a discontinuity: rows before the split date show pre-split prices, rows after show post-split prices. SMA/EMA calculated across this boundary is meaningless.

**Why it happens:** `MarketDataIngestionService.ingestDaily()` uses `upsert` semantics: if the row exists (`findBySymbolAndTradingDate`) it calls `existing.update()`. It does NOT re-fetch and overwrite historical rows that Yahoo has now adjusted. The database row was written once and is never corrected.

**Prevention:**
- Store both `close` (unadjusted) and `adj_close` (split/dividend adjusted) columns in `market_bars`
- Always compute indicators against `adj_close`
- On each ingestion run, detect split events by comparing the most recent stored close against Yahoo's adjusted close for the same date; if divergence exceeds 5%, trigger a full backfill for that symbol
- Alternatively, always fetch the last N+lookback days and overwrite (not just upsert new rows)

**Warning signs:** Indicator values (SMA, EMA) making discontinuous jumps for a symbol on a specific date. Backtest results diverging from live results for the same date range.

**Phase:** Address during the market data ingestion design phase, before indicator computation is finalized.

---

## Real-Time Data Reliability

### CRITICAL: Yahoo Finance as Real-Time Source is Structurally Unreliable

**What goes wrong:** Yahoo Finance has no official public API for real-time or intraday data. The existing `YahooFinanceChartClient` calls undocumented endpoints that have changed without notice, added authentication gates, imposed rate limits without documentation, and experienced regional outages. As of 2024-2025, several previously reliable Yahoo Finance endpoints were deprecated with no replacement.

The `MarketDataIngestionService` has a single point of failure: if Yahoo returns an error or empty body during the intraday ingestion window, `ingestIntraday()` returns 0 and the market_bars_intraday table has a hole for that minute. The rule evaluator then runs on a stale last-known bar — potentially from several minutes ago.

**Consequences:**
- Indicators computed on stale data trigger signals that would not fire on true current prices
- No alerting exists when ingestion silently produces 0 rows mid-session
- For LIVE rules, this means real orders are placed on wrong signals

**Prevention:**
- Add a staleness check to the rule evaluator: before evaluating any rule, assert that the most recent bar for every symbol in the universe is no older than `(current_time - 2 * interval_minutes)`. If stale, skip evaluation and emit a `DATA_STALE` warning event
- Add a `last_ingested_at` timestamp to `market_bars_intraday` and expose a health endpoint that reports staleness per symbol
- Plan for a secondary data source: KRX Data Marketplace (official, but paid), Naver Finance scraping (unofficial but often reliable for Korean markets), or a WebSocket-based provider
- For MVP, the fallback can simply be: if Yahoo fails 3 consecutive times, pause all LIVE rule evaluation and send an admin alert

**Warning signs:** `Intraday(5m) ingestion done: 0 symbols` during market hours. `market_bars_intraday` has no rows for the last 10 minutes during an active session.

**Phase:** Design the staleness detection circuit before wiring the rule evaluator to live data.

---

### MODERATE: No Heartbeat / Feed Disconnection Detection

**What goes wrong:** The ingestion service is called on a schedule (pull model). If the scheduled call itself fails (scheduler thread exception, JVM GC pause overruns, DB connection pool exhaustion), no bar is ingested and nothing raises an alarm. The rule evaluator runs on increasingly stale data for the rest of the session.

**Prevention:**
- After each ingestion cycle, write a heartbeat row to a `data_feed_heartbeats` table with `(source, symbol_count, latest_ts, status)`
- A separate health-check scheduler (every 5 minutes) queries this table and logs `WARN` if the latest heartbeat is older than 15 minutes during trading hours
- Expose this check in Spring Actuator `/actuator/health`

**Phase:** Implement alongside the ingestion service.

---

## Toss Securities API Limits

### CRITICAL: OAuth Token Expires Mid-Session with No Auto-Refresh

**What goes wrong:** Toss Securities issues access tokens with a ~1-hour TTL (3600 seconds, based on `client_credentials` grant type standard). A LIVE rule session starts at 09:05 with a fresh token. At 10:05 the token expires. The next order attempt receives a 401 response. If the system treats 401 as a permanent error (order rejected), it stops trying. If it treats 401 as transient and retries without refreshing the token, it loops forever.

**Why it happens:** OAuth token lifecycle management is orthogonal to trading logic. Without an explicit token refresh mechanism, expiry during market hours is inevitable in any session longer than 1 hour — which is every full trading day (09:00–15:30 = 6.5 hours).

**Consequences:** All LIVE order execution silently stops mid-session. Positions cannot be closed. Stop-losses cannot fire.

**Prevention:**
- Implement a `TossTokenManager` bean that stores the token with its `expires_at = issued_at + expires_in - 60s` (60-second safety margin)
- Before every API call, check `ZonedDateTime.now().isAfter(expires_at)`: if so, refresh synchronously before proceeding
- Use a background refresh job that proactively refreshes at the 50-minute mark regardless of pending calls
- Store `expires_at` in the database so a server restart does not lose token state

**Warning signs:** Toss API returning 401 errors on order calls after the first hour of a session. Token issuance timestamps in the database showing no refresh for > 55 minutes during an active session.

**Phase:** Implement in the Toss API integration phase, before any live order execution.

---

### CRITICAL: Toss API Tokens Must Never Be Stored in Plaintext

**What goes wrong:** The project constraint explicitly states "OAuth 토큰을 DB에 암호화 저장해야 함 (평문 저장 금지)." If the `toss_credentials` or equivalent table stores `access_token` and `refresh_token` as plaintext VARCHAR columns, a database dump, a SQL injection vulnerability, or inadvertent logging exposes credentials that can be used to execute real trades in the user's brokerage account.

**Why it happens:** The path of least resistance in JPA/Spring is to map a String field directly to a column. Without a JPA `AttributeConverter` applying AES encryption, the token lands in plaintext.

**Consequences:** Credential theft → unauthorized order execution in real accounts → financial loss. Regulatory exposure under Korean financial security requirements.

**Prevention:**
- Implement a `AesGcmAttributeConverter implements AttributeConverter<String, String>` that encrypts on write and decrypts on read using a 256-bit key loaded from environment variable (never hardcoded)
- Annotate token fields with `@Convert(converter = AesGcmAttributeConverter.class)`
- Add a Flyway migration that defines the token storage table with a `BYTEA` or encrypted `TEXT` column
- Ensure the encryption key is injected via `GRAPHIFY_TOKEN_ENCRYPTION_KEY` env var, with application startup failure if absent
- Never log token values — use `[REDACTED]` in any debug output that touches token fields

**Warning signs:** Token fields appearing in application logs. Plaintext JWT-shaped strings visible in `psql` queries against the tokens table.

**Phase:** Address in the Toss API integration phase, before any token storage is implemented.

---

### MODERATE: API Rate Limits Are Undocumented and Discovered at Runtime

**What goes wrong:** Toss Securities Open API rate limits are not publicly documented with exact numbers. Kiwoom Securities (the most mature Korean retail broker API) caps at 1 request per 3.6 seconds / 1000 per hour — Toss likely has similar constraints. Under load (many active LIVE rules, each polling price + submitting orders), the system may hit rate limits and receive HTTP 429 responses with no retry-after header guidance.

**Consequences:** Order submissions are throttled or dropped. Rule evaluation produces signals that cannot be acted on.

**Prevention:**
- Implement a `TossApiRateLimiter` using a token bucket (Guava `RateLimiter` or Resilience4j `RateLimiter`) initialized at a conservative 1 req/4s per the Kiwoom precedent
- Track all Toss API call counts in a rolling window; emit WARN log if approaching 800/hour
- On 429 response, back off exponentially with jitter, not immediate retry
- Consolidate API calls: rather than each rule independently querying the price for the same symbol, use a shared current-price cache refreshed once per evaluation cycle

**Phase:** Address in the Toss API integration phase.

---

### MODERATE: API Downtime During Market Hours Is a Real Scenario

**What goes wrong:** Broker APIs experience unplanned outages, including during trading hours. If `TossOrderClient` throws an exception or returns a 5xx, the current rule evaluator has no circuit breaker. It will either retry in a tight loop (DoS against Toss), propagate the exception up and crash the evaluation cycle, or silently swallow the error and lose the signal.

**Prevention:**
- Wrap all Toss API calls with Resilience4j `CircuitBreaker`: open circuit after 5 consecutive failures, half-open after 60 seconds
- On circuit open, set all LIVE rules to `SUSPENDED` state and log a structured event; do not leave them in a state where they generate unexecutable signals
- Add a health indicator for Toss API connectivity to Spring Actuator

**Phase:** Address in the Toss API integration phase.

---

## Backtest-to-Live Gap

### CRITICAL: Look-Ahead Bias in Close-Based Indicators

**What goes wrong:** The existing `RuleEvaluator` computes SMA/EMA/RSI against daily bars. In a backtest, the "current bar" is the final OHLCV for that day including the closing price. A crossover signal computed at 15:30:01 using today's close is fine in backtest. In live evaluation, however, the "current bar" at 14:00 is an incomplete intraday bar — the close field holds the last trade price, not the day's closing auction price. Using incomplete intraday bars as if they were daily bars makes the live strategy evaluate on different data than the backtest used.

**Consequences:** A strategy that looks profitable in backtest underperforms in live because the signal timing differs. A strategy may signal at 10:00 based on an SMA computed against intraday prices, when the backtest only ever signaled at EOD.

**Prevention:**
- Make the evaluation time horizon explicit in rule definitions: `"evalTiming": "EOD"` (evaluate after 15:30 daily bar is final) vs `"evalTiming": "INTRADAY"` (evaluate on intraday bars)
- For `EOD` rules in PAPER_LIVE mode, only evaluate after 15:35 KST when the daily bar has settled
- Document this distinction clearly in the rule creation UI so users understand their backtest used EOD bars

**Warning signs:** PAPER_LIVE performance diverging from backtest performance on the same date range when run retrospectively. Signals firing at random intraday times for rules designed around daily closes.

**Phase:** Address during the real-time rule evaluator design phase.

---

### CRITICAL: Execution Slippage Not Modeled

**What goes wrong:** The existing `FillSimulator` likely fills at signal price (close). In live markets, the actual fill price differs: market orders fill at the best ask (buy) or best bid (sell) at the moment of submission, which may be 0.1–1% away from the close that triggered the signal, especially for less liquid KOSDAQ names. Over many trades, this gap consumes strategy alpha.

**Consequences:** Backtest shows 15% annual return; live delivers 8% after realistic slippage and fees. User loses trust in the platform.

**Prevention:**
- Add a configurable `slippageBps` parameter to `FillSimulator` (e.g., 10 bps default for KOSPI large-cap, 30 bps for KOSDAQ small-cap)
- Add transaction cost model: Korean securities tax (0.18% for KOSPI, 0.18% for KOSDAQ as of 2025), brokerage commission (typically 0.015%–0.05%)
- Show "backtest with costs" vs "backtest gross" in the UI as separate series on the equity curve

**Phase:** Address during the backtest performance reporting phase.

---

### MODERATE: Survivorship Bias in Symbol Universe

**What goes wrong:** Rules are defined against currently-traded symbols. Historical backtests using only currently-traded symbols exclude companies that were delisted between the backtest start date and today. Delisted companies typically have large negative terminal returns. The backtest appears better than it would have been had you actually held those positions.

**Consequences:** Inflated backtest win rates and Sharpe ratios. Strategies that performed well on surviving companies may not generalize.

**Prevention:**
- For MVP, document this limitation explicitly in the backtest results UI: "Results may be subject to survivorship bias. Delisted securities are not included."
- Long-term: integrate DART delisting data to include historical symbols that no longer trade

**Phase:** Document the limitation before launching the backtest results page. Full fix is a later milestone.

---

## Spring Scheduler Pitfalls

### CRITICAL: Multiple Application Instances Both Fire the Scheduler

**What goes wrong:** If Graphify is ever deployed with more than one instance (horizontal scaling, blue-green deployment, rolling restart), every instance runs its own `@Scheduled` tasks. At 09:01, both instances call `ingestDailyForActiveSymbols()` simultaneously and both call the rule evaluator. Result: duplicate bar ingestion (benign due to upsert, but doubles Yahoo API calls), and — most dangerously — duplicate LIVE order execution: two instances each evaluate a BUY signal and each submit a buy order to Toss, doubling the intended position size.

**Why it happens:** Spring `@Scheduled` is instance-local. There is no coordination mechanism by default.

**Consequences:** For PAPER mode, duplicate fills inflate virtual P&L. For LIVE mode, real money is committed in doubled quantities.

**Prevention:**
- Add ShedLock before deploying any LIVE rule evaluation: `@SchedulerLock(name = "ruleEvaluationTask", lockAtMostFor = "PT55S", lockAtLeastFor = "PT10S")`
- ShedLock uses the existing PostgreSQL database as the lock store — no additional infrastructure needed
- The lock key must be the same string across all instances; use task name, not hostname
- Even in single-instance deployments, add ShedLock now as a correctness guarantee

**Warning signs:** Duplicate rows in the order execution log with identical (rule_id, symbol, direction, eval_timestamp). Yahoo API being called N times per cycle where N equals the instance count.

**Phase:** Address before the real-time rule evaluation scheduler is activated.

---

### CRITICAL: Cron Timezone Defaults to JVM/Container Timezone, Not KST

**What goes wrong:** A cron expression `@Scheduled(cron = "0 0 9 * * MON-FRI")` means "fire at 09:00 in whatever timezone the JVM is running." In a Docker container or cloud deployment, the default timezone is UTC. 09:00 UTC = 18:00 KST — the scheduler fires 9 hours late, entirely outside market hours. Conversely, the intended 09:01 KST fire time occurs at 00:01 UTC, which the container may interpret correctly or not depending on how the JVM timezone is set.

**Prevention:**
- Always specify `zone` explicitly: `@Scheduled(cron = "0 1 9 * * MON-FRI", zone = "Asia/Seoul")`
- Additionally set `TZ=Asia/Seoul` as a container environment variable and `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))` at application startup
- Verify with a log line at startup: `log.info("JVM timezone: {}", TimeZone.getDefault().getID())`
- Write an integration test that asserts scheduler fires within the expected KST window

**Warning signs:** Ingestion logs timestamped at unexpected wall-clock times. Scheduler fires happening at 00:01 or 18:01 KST rather than 09:01.

**Phase:** Address before implementing any scheduled task. This is a configuration issue that must be correct from the first scheduler.

---

### MODERATE: Missed Fires on Application Restart During Market Hours

**What goes wrong:** The application restarts at 10:30 KST (during market hours). `@Scheduled` with a fixed-rate or cron expression does not fire for the missed windows between the last fire before restart and the first fire after startup. If the scheduler was supposed to collect 5-minute bars at 10:00, 10:05, 10:10, 10:15, 10:20, 10:25, those bars are missing from `market_bars_intraday`. The rule evaluator at 10:31 computes indicators over a 30-minute gap.

**Prevention:**
- On application startup, run a "catch-up ingestion" that fetches the last 2 hours of intraday bars for all active symbols, regardless of what the scheduler has done
- This catch-up should run as a `@EventListener(ApplicationReadyEvent.class)` method, not as part of the regular scheduler cycle
- Quartz with a persistent job store (JDBC JobStore) supports misfire handling natively if the scheduling complexity warrants it — for MVP, the catch-up on startup is sufficient

**Phase:** Address during the ingestion service implementation.

---

## Security Pitfalls

### CRITICAL: Unauthorized Order Execution via Horizontal Rule Access

**What goes wrong:** LIVE rules belong to a specific user. If the rule evaluator fetches all rules with status `LIVE` using `ruleRepository.findAll()` (as `activeSymbols()` currently does), there is no user-scoping. A bug in the evaluation loop, or a future refactor that accidentally removes the user filter, could evaluate User A's rules and submit orders to User B's Toss account, or submit orders to User A's Toss account using User B's token.

**Prevention:**
- The evaluation path must always be: fetch user → fetch user's Toss token → fetch user's LIVE rules → evaluate → submit order using that user's token
- Never cross user-token-rule associations in a single evaluation pass
- Add a database constraint: `trading_rules.user_id` FK must match the `user_id` on the Toss credentials row used for order submission
- Add a server-side assertion before every Toss order call: `assert rule.getUserId().equals(tossCredentials.getUserId())`

**Warning signs:** Order execution logs showing a rule owned by user X using credentials belonging to user Y. Any code path that calls Toss order API without first verifying rule.userId == credentials.userId.

**Phase:** Design this constraint into the order execution service from the start. Do not retrofit.

---

### CRITICAL: API Key / Secret Stored in Environment But Logged at Startup

**What goes wrong:** Spring Boot auto-configuration logs all application properties at startup at DEBUG level when `logging.level.org.springframework=DEBUG` is set. If `toss.client-secret` or `graphify.token-encryption-key` is bound to a `@ConfigurationProperties` bean, its value will appear in startup logs. Additionally, Spring Boot Actuator's `/actuator/env` endpoint exposes environment variables — unless masked.

**Prevention:**
- Add `management.endpoint.env.keys-to-sanitize=.*secret.*,.*key.*,.*token.*,.*password.*` in `application.properties`
- Annotate sensitive `@ConfigurationProperties` fields so Spring's property masking covers them
- Never set `logging.level.org.springframework=DEBUG` in production profiles
- Ensure `GRAPHIFY_TOKEN_ENCRYPTION_KEY` is injected via Kubernetes secret or environment-specific secret manager, not hardcoded in `application.yml`

**Warning signs:** Startup logs containing strings matching the pattern of API keys (long alphanumeric strings). `/actuator/env` returning unmasked values for key-sounding properties.

**Phase:** Address before any production deployment of the Toss integration.

---

### MODERATE: Frontend Rule Execution Trigger Without Server-Side Rate Guard

**What goes wrong:** The rule lifecycle UI allows a user to transition a rule to `LIVE` status. Without server-side guards, a user could create 100 LIVE rules all targeting the same symbol, causing the rule evaluator to emit 100 buy orders for the same symbol in the same minute, exhausting both Toss rate limits and the user's available margin.

**Prevention:**
- Add a server-side cap: maximum N active LIVE rules per user (e.g., 10 for MVP)
- Add per-symbol deduplication in the order emission layer: within a single evaluation cycle, only one order per (user, symbol, direction) is permitted
- Return a clear validation error when the LIVE promotion exceeds the cap

**Phase:** Address during the rule lifecycle management API implementation.

---

### MODERATE: CSRF and Idempotency on Order Endpoints

**What goes wrong:** If order-triggering endpoints (rule status transitions, manual overrides) are not CSRF-protected and accept repeated requests, a replay attack or accidental double-click could double-promote a rule or emit duplicate state transitions.

**Prevention:**
- Ensure Spring Security CSRF protection is enabled for state-mutating endpoints (check that `SecurityConfig` does not globally disable CSRF for trading endpoints)
- Add idempotency keys to order submission: include a `X-Idempotency-Key` header on Toss API calls; store submitted keys in a `order_idempotency_keys` table with a 24-hour TTL to prevent duplicate submissions on network retry

**Phase:** Address during the Toss order execution implementation.

---

## Phase-Specific Warnings Summary

| Phase | Primary Pitfall | Mitigation |
|-------|----------------|------------|
| Market data ingestion | Yahoo Finance unreliability + no staleness detection | Staleness circuit + catch-up on startup |
| Market data ingestion | Stock split corrupts stored prices | Store adj_close, detect divergence and backfill |
| Real-time scheduler | Multiple instances double-firing + wrong timezone | ShedLock + explicit `zone = "Asia/Seoul"` |
| Real-time rule evaluator | Market holiday fires + look-ahead bias on intraday bars | KrxMarketCalendar guard + explicit evalTiming |
| Backtest results UI | Slippage not modeled → inflated returns | Add cost model to FillSimulator, show gross vs net |
| Toss API integration | Token expiry mid-session | TossTokenManager with proactive refresh |
| Toss API integration | Plaintext token storage | AesGcmAttributeConverter on all token fields |
| Toss API integration | Rate limits hit under multiple LIVE rules | Token bucket rate limiter + shared price cache |
| Order execution | Unauthorized cross-user order emission | Assert rule.userId == credentials.userId before every call |
| Order execution | Price-locked stock (상한가/하한가) loops | PRICE_LIMIT_PENDING state + order deduplication |
| Rule lifecycle management | Unlimited LIVE rules exhaust rate limits | Server-side cap + per-cycle deduplication |

---

*Researched: 2026-06-20*
*Sources: KRX official market guide (global.krx.co.kr), KRX Volatility Interruption research (MDPI), ShedLock documentation, Spring @Scheduled timezone analysis, Toss Securities Open API overview (corp.tossinvest.com), Kiwoom API rate limit precedent (1 req/3.6s), backtesting bias literature (QuantStart, Coriva), AES field-level encryption with JPA AttributeConverter (2024-2025 guides)*

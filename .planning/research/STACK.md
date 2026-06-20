# Stack Research: Real-time Auto-Trading

**Project:** Graphify — 모의투자 고도화 & 토스증권 실투자 연동
**Researched:** 2026-06-20
**Mode:** Ecosystem (subsequent milestone — existing Spring Boot 3.4.5 / Java 21 / React 18 / Zustand / Tailwind)

---

## Real-time Market Data

### Situation

The codebase already uses `YahooFinanceChartClient` (RestClient-based) for historical daily bars and intraday polling via the `/v8/finance/chart/{symbol}?interval=5m&range=1d` endpoint. `MarketDataIngestionService` upserts into `market_bars` and `market_bars_intraday` tables. `InternalMarketController` exposes HTTP trigger endpoints intended to be called by a scheduler. The `MarketDataPort` interface has no live implementation yet.

### Recommendation: Polling via Yahoo Finance (5-minute bars) during KRX hours + KIS WebSocket for live tick if required

**Primary path — Yahoo Finance polling (MEDIUM confidence):**

Yahoo Finance's unofficial `/v8/finance/chart` endpoint already works in production for this codebase (`YahooFinanceChartClient.fetchIntraday`). For per-minute rule evaluation, calling `interval=1m&range=1d` on a per-symbol basis every minute during 09:00–15:30 KST is the lowest-friction path. The existing `MarketDataIngestionService.ingestIntraday(symbol, "1m", "1d")` method only needs a scheduler trigger and a market-hours guard, not a new HTTP client.

Limitation: Yahoo Finance has no official SLA for Korean stocks. The `regularMarketPrice` in the meta block typically lags 15–20 minutes for KRX during market hours. For paper trading simulation (not real money), this delay is acceptable — the rule fires on the last confirmed candle.

**Secondary path — KIS (한국투자증권) WebSocket for real tick data (MEDIUM confidence):**

Korea Investment & Securities publishes an official Open Trading API with WebSocket real-time streams. Transaction IDs `H0STCNT0` (체결가) and `H0STASP0` (호가) deliver sub-second ticks. Spring Boot integration requires:
- `spring-boot-starter-websocket` (already in Spring Boot BOM, no extra version needed)
- `org.java-websocket:Java-WebSocket:1.5.7` or Spring's `WebSocketClient` (Tyrus or Jetty underneath)
- OAuth approval key flow (separate from trading account OAuth)

KIS WebSocket is rate-limited: up to 20 combined subscriptions per connection, 1 notification subscription. For multi-symbol PAPER_LIVE rules this hits limits quickly. A multiplexing layer is needed if > 20 symbols.

**Do NOT use for real-time Korean stock data:**
- `yfinance` Python library — wrong runtime
- Naver Finance unofficial API — HTML scraping, breaks without warning
- Cybos Plus — Windows COM-only, incompatible with server-side JVM
- Alpha Vantage free tier — 25 req/day, useless for per-minute ingestion

**Decision matrix:**

| Approach | Latency | Cost | Complexity | Suitable for paper trading |
|---|---|---|---|---|
| Yahoo Finance polling (1m) | ~15–20 min delay | Free | Low — already wired | YES — rule fires on closed candle |
| KIS WebSocket real-time | Sub-second | Free (KIS account required) | Medium — connection mgmt, heartbeat, reconnect | YES — and required for LIVE real orders |
| KIS REST polling | ~1–5 min | Free | Low | YES |

**Concrete recommendation:** Start with Yahoo Finance 1-minute polling for PAPER_LIVE. When implementing LIVE rule promotion (Toss Securities / KIS order execution), add KIS WebSocket for real-tick data. Do not rebuild `MarketDataIngestionService` — extend it with a `LiveMarketDataAdapter` implementing `MarketDataPort`.

**Confidence: MEDIUM** — Yahoo Finance intraday for KRX confirmed working in existing code; KIS WebSocket capability confirmed via official GitHub (github.com/koreainvestment/open-trading-api) and community Java implementations (github.com/youhogeon/finance.kis_api, github.com/devngho/kt_kisopenapi). Rate limit specifics from community blog posts, not official docs.

---

## Rule Evaluation Engine

### Situation

The backtest engine (`BacktestService`, `RuleEvaluator`) is complete. The requirement is a scheduled per-minute evaluation loop that fires only during KRX market hours (09:00–15:30 KST, weekdays), loads the latest 1-minute bars, runs `RuleEvaluator` for all `PAPER_LIVE` rules, and writes fills to `PaperLedger`. `InternalMarketController` already exists as an HTTP-triggered ingestion endpoint suggesting a scheduler is planned but not yet implemented.

### Recommendation: Spring `@Scheduled` with a market-hours guard (NOT Quartz, NOT Spring Batch)

**Use `@Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "Asia/Seoul")` (HIGH confidence):**

Spring's `@Scheduled` is already available — `spring-boot-starter-web` pulls in `spring-context` which includes the scheduler. No additional dependency. The cron expression `"0 * 9-15 * * MON-FRI"` fires at the top of every minute from 09:00 to 15:00 KST on weekdays. A final 15:30 evaluation requires a second expression: `"0 30 15 * * MON-FRI"`. Both can live in the same `@Component`.

A market-hours guard in code (`LocalTime.now(ZoneId.of("Asia/Seoul"))`) handles public holidays (KRX holidays are not in cron syntax — maintain a `Set<LocalDate>` of holiday dates, check before evaluating).

```java
@Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
public void evaluateLiveRules() {
    if (isKrxHoliday(LocalDate.now(ZoneId.of("Asia/Seoul")))) return;
    // ingest latest 1m bar, then evaluate PAPER_LIVE rules
}
```

**Why NOT Quartz:**
Quartz requires either in-memory or JDBC job store (new `QRTZ_*` tables in PostgreSQL). It adds ~10 dependencies, a `SchedulerFactory` bean, `JobDetail` and `Trigger` wiring, and significant boilerplate. The use case here — a single per-minute job during market hours — does not justify this. Quartz is warranted when you need: distributed job locking across multiple instances, job persistence across restarts, dynamic job creation at runtime, or retry/dead-letter semantics. None of these apply to Graphify's current scope.

**Why NOT Spring Batch:**
Spring Batch is a bulk ETL framework (chunks, readers, writers, job repository). It is the wrong abstraction for a per-minute real-time evaluation loop. Its overhead (job metadata tables, step context) would be pure noise here.

**Handling the 15:30 edge case:**
KRX closes at 15:30. Add `"0 30 15 * * MON-FRI"` as a second `@Scheduled` for the closing evaluation. After 15:30, the intraday ingestion job can also stop.

**Enabling scheduling:**
Add `@EnableScheduling` to a `@Configuration` class. This is the only setup step.

**Confidence: HIGH** — `@Scheduled` with timezone-aware cron is a well-documented Spring feature, no external dependency, confirmed current in Spring Boot 3.4.x docs.

---

## Chart Library

### Situation

The frontend has no charting library at all (package.json confirms: Recharts, Chart.js, Victory, Nivo — none present). Two chart types are needed: (1) equity curve (line/area chart for portfolio value over time — backtest and PAPER_LIVE results) and (2) candlestick OHLCV chart (per-symbol price chart in the dashboard). The stack is React 18, Tailwind CSS 3, Zustand 5, Vite 6, TypeScript 5.6.

### Recommendation: `lightweight-charts` v5 for candlestick, Recharts v2 for equity curve

**`lightweight-charts@5.2.0` for candlestick charts (HIGH confidence):**

TradingView's `lightweight-charts` is the standard for financial charting in React. Version 5.2.0 (latest as of mid-2026) is 45KB gzipped, renders via HTML5 Canvas (not SVG), and handles thousands of OHLCV data points without layout thrashing. It has full TypeScript support, a `createChart` + `addCandlestickSeries` API, and is integrated via `useRef`/`useEffect` (imperative, not declarative — fits because the DOM node is owned by the library).

Critically: it is NOT a React component library — it manages its own DOM. This means Tailwind utility classes do not style chart internals (axis colors, grid lines), but the chart's `layout` and `grid` config options handle theming. Wrap it in a `div` sized by Tailwind; the chart fills the container. Resize via `ResizeObserver`.

```
npm install lightweight-charts@5.2.0
```

**`recharts@2.15.0` for equity curve / performance charts (HIGH confidence):**

Recharts is built on React + SVG, declarative, and integrates naturally with Tailwind (className on wrapper divs) and Zustand (feed data from store directly into `<LineChart data={equityCurve}>`). It is correct for equity curve (100–500 daily data points), MDD visualization (area chart), and win-rate bar charts in the performance report page. SVG rendering at these data densities has no performance problem.

Do NOT use Recharts for candlestick OHLCV — it has no candlestick series type and building one manually in SVG performs poorly at 390 bars (09:00–15:30 at 1-minute intervals).

**Why NOT Victory:**
Victory's bundle is larger than Recharts and its financial chart support is no better. It has no candlestick series. Recharts has more weekly downloads and more active maintenance in 2025–2026.

**Why NOT Nivo:**
Nivo wraps D3 and is optimized for static/animated infographic charts (treemaps, chord diagrams). Overkill for a trading dashboard. No candlestick series. Heavy dependency tree.

**Why NOT a single library for both chart types:**
`lightweight-charts` does not have a React-native declarative API, making equity curve composition awkward. Recharts cannot do performant candlestick. Two focused libraries at ~45KB + ~90KB gzipped is the right tradeoff.

**Installation:**
```bash
npm install lightweight-charts@5.2.0 recharts@2.15.0
```

**Tailwind integration pattern:**
- `lightweight-charts`: `<div ref={containerRef} className="w-full h-64 rounded-lg overflow-hidden" />`
- Recharts: `<ResponsiveContainer width="100%" height={256}><LineChart ...>`

**Confidence: HIGH** — Both libraries confirmed current and actively maintained. `lightweight-charts` v5 release confirmed via GitHub (github.com/tradingview/lightweight-charts) and npm. Recharts 2.x confirmed maintained. Performance characteristics (Canvas vs SVG, data density limits) verified across multiple 2025–2026 comparison sources.

---

## API Token Security

### Situation

The project constraint requires: Toss Securities Open API OAuth tokens (access token + refresh token) stored encrypted at rest in PostgreSQL. Plain-text storage is explicitly forbidden. The existing stack has Spring Data JPA, Spring Security, PostgreSQL, and Flyway. There is no existing encryption utility in the codebase.

### Toss Securities API Status Note (LOW confidence)

As of 2026-06-20, the Toss Securities Open API (`corp.tossinvest.com/en/open-api`) appears to be in staged/pre-registered rollout. Official documentation is at `developers.tossinvest.com/docs` (REST-only endpoints confirmed: accounts, balances, orders, quotes, candles). The API uses OAuth 2.0 client credentials or authorization code flow (standard bearer token). The specific token TTL and refresh semantics were not confirmed from official docs — this must be verified directly from the developer portal before implementation.

### Recommendation: JPA `AttributeConverter` with AES-256-GCM, key from environment variable (HIGH confidence)

**Do NOT use Jasypt for this use case.** Jasypt (`jasypt-spring-boot`) is designed for encrypting Spring `application.properties` values (datasource passwords, API keys in config files). It uses password-based encryption (PBE) which derives a key from a passphrase on every encrypt/decrypt call — correct for config, wrong for database column encryption where you need a stable, reusable symmetric key and explicit IV management.

**Use `javax.crypto` (JDK built-in) via a JPA `AttributeConverter`:**

This is the standard pattern for column-level encryption in Spring Data JPA. No new dependency — `javax.crypto.Cipher` with `AES/GCM/NoPadding` is in the JDK. The converter encrypts on `convertToDatabaseColumn` and decrypts on `convertToEntityAttribute`.

```java
@Converter
public class AesGcmEncryptingConverter implements AttributeConverter<String, String> {
    // AES-256-GCM: 32-byte key from env, 12-byte random IV prepended to ciphertext
    // Store as Base64(iv + ciphertext) in a TEXT column
}
```

**Key management:**
- The AES-256 key (32 bytes) is loaded from an environment variable (`GRAPHIFY_ENCRYPTION_KEY`) at application startup — never hardcoded, never in `application.properties` checked into git.
- Encode the key as Base64 in the environment variable; decode to `byte[]` in a `@Bean`.
- Use a different key from the JWT signing secret.

**Schema change:**
The Toss Securities token entity needs a Flyway migration adding a `toss_credentials` table with `TEXT` columns for encrypted access token, encrypted refresh token, token expiry, and user FK.

```sql
-- V30__toss_credentials.sql
CREATE TABLE toss_credentials (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id),
    access_token  TEXT NOT NULL,   -- AES-256-GCM encrypted
    refresh_token TEXT,            -- AES-256-GCM encrypted
    expires_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id)
);
```

**Why NOT PostgreSQL `pgcrypto` `PGP_SYM_ENCRYPT`:**
`pgcrypto` encrypts at the database layer, which means the key must be passed in every SQL query (`SELECT PGP_SYM_DECRYPT(access_token, :key) ...`). This leaks the key in query logs and makes JPA repositories awkward. Application-layer encryption (JPA converter) keeps the key in the JVM process and never in SQL.

**Why NOT Vault / KMS now:**
HashiCorp Vault or AWS KMS adds significant operational complexity for a current single-instance deployment. `AttributeConverter` with an env-var key is the correct starting point; the converter interface can be swapped for a KMS-backed implementation later without changing entity code.

**Confidence: HIGH** — JPA `AttributeConverter` pattern is stable since JPA 2.1. AES-256-GCM with JDK `javax.crypto` has no external dependencies. Confirmed working with Spring Boot 3.x + PostgreSQL across multiple verified sources. Toss Securities OAuth token structure is LOW confidence (API in staged rollout, official docs not fully accessible).

---

## What NOT to Use

| Category | Avoid | Reason |
|---|---|---|
| Scheduling | Quartz Scheduler | Requires QRTZ_* DB tables, SchedulerFactory boilerplate, JobDetail/Trigger wiring — massively over-engineered for a single per-minute market-hours job |
| Scheduling | Spring Batch | ETL chunk-processing framework; wrong abstraction for a real-time evaluation loop |
| Scheduling | `fixedRate` / `fixedDelay` on `@Scheduled` | Does not respect market hours; always fires including nights/weekends/holidays. Use cron with timezone instead |
| Market data | Naver Finance API | Unofficial HTML scraping; breaks without warning; no intraday tick data |
| Market data | Cybos Plus (대신증권) | Windows COM object; incompatible with server-side JVM on Linux/Docker |
| Market data | Alpha Vantage free tier | 25 requests/day limit; unusable for per-minute polling of multiple symbols |
| Market data | KRX OpenAPI (정보데이터시스템) | Batch-only (EOD files, not real-time); requires institutional membership |
| Chart | Recharts for candlestick | No native candlestick series; SVG DOM thrashing at 390 bars (1m KRX session) |
| Chart | Victory | No candlestick series; larger bundle than Recharts; less community activity |
| Chart | Nivo | D3-backed infographic library; no financial chart types; heavy bundle |
| Chart | Apache ECharts (echarts-for-react) | Functional but the `lightweight-charts` + Recharts combo is already purpose-fit; adding a third charting dependency is unnecessary |
| Encryption | Jasypt for DB column encryption | PBE-based; designed for config file encryption, not database column encryption; wrong key derivation model |
| Encryption | `pgcrypto` PGP_SYM_ENCRYPT | Key appears in SQL query text → query logs → key leakage risk; JPA integration is awkward |
| Encryption | Storing OAuth tokens in plain text | Violates explicit project constraint; Toss Securities tokens give real-money trading access |
| Encryption | Storing tokens in JWT or session | Tokens must survive server restarts and be user-specific long-lived credentials; DB is correct store |

---

## Sources

- KIS Open Trading API official GitHub: https://github.com/koreainvestment/open-trading-api
- KIS Java library (community): https://github.com/youhogeon/finance.kis_api
- KIS Kotlin/Java library: https://github.com/devngho/kt_kisopenapi
- KIS WebSocket rate-limit handling: https://hky035.github.io/web/refact-kis-websocket/
- Toss Securities Open API page: https://corp.tossinvest.com/en/open-api
- lightweight-charts GitHub: https://github.com/tradingview/lightweight-charts
- lightweight-charts React tutorial: https://tradingview.github.io/lightweight-charts/tutorials/react/simple
- lightweight-charts v5 indicator community: https://github.com/tradingview/lightweight-charts/discussions/2027
- Spring Boot Quartz docs: https://docs.spring.io/spring-boot/reference/io/quartz.html
- JPA column encryption (AttributeConverter): https://sultanov.dev/blog/database-column-level-encryption-with-spring-data-jpa/
- Spring JPA encryption example: https://github.com/damienbeaufils/spring-data-jpa-encryption-example
- Recharts vs lightweight-charts comparison: https://stackshare.io/lightweight-charts/vs/recharts
- Top React stock chart libraries 2025: https://www.syncfusion.com/blogs/post/top-5-react-stock-charts-in-2025/amp

---

*Researched: 2026-06-20*

# Features Research: Paper Trading Platform

**Project:** Graphify — 모의투자 고도화 & 토스증권 실투자 연동
**Domain:** Rule-based algo trading — backtest → paper → live pipeline
**Researched:** 2026-06-20
**Overall confidence:** HIGH (stack is known, backtest domain is well-documented, codebase fully inspected)

---

## Paper Trading Dashboard

The dashboard is the primary operational view during PAPER_LIVE rule execution. Users need to answer: "Is my bot working right now, and is it making money?"

### Table Stakes

These are missing → users cannot use the paper trading feature at all.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Virtual balance display (가상 잔고) | Every paper trading system shows total equity and available cash | Low | DB has `paper_accounts.cash` and `paper_equity_snapshots`; just needs API + UI |
| Active positions list | Users must see what the bot currently holds | Low | `paper_positions` table exists. Display: symbol, qty, avg_price, current_price, unrealized P&L |
| Unrealized P&L per position | Core reason to watch the dashboard | Medium | Requires current price fetch at display time; color-coded green/red |
| Total portfolio value (평가금액) | Cash + unrealized position value | Low | `PaperLedger.equity()` logic already exists; needs SSE or polling endpoint |
| Today's realized P&L | Day-level summary for daily check-in | Low | Sum of `paper_trades.pnl` WHERE `traded_at >= today` |
| Active rules summary | Which rules are currently running (PAPER_LIVE) | Low | Filter `trading_rules` by status=ACTIVE and mode=PAPER |

### Differentiators

Present → platform feels professional vs. a toy.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Equity curve mini-chart on dashboard | Instant visual of account trajectory without visiting report page | Medium | Reuse `paper_equity_snapshots`; small sparkline, not full chart |
| Return-since-start percentage badge | Single number users want ("am I up or down overall?") | Low | `(current_equity - base_cash) / base_cash * 100` |
| Per-rule P&L breakdown | Shows which rule is contributing, which is dragging | Medium | Join `paper_trades.rule_id` → aggregate P&L per rule; display alongside rule list |
| Last signal timestamp | "When did this rule last fire?" — confirms the bot is alive | Low | Store last_evaluated_at on rule; display on dashboard card |
| Market hours indicator | Clear signal that real-time eval is only active 09:00–15:30 KST | Low | Simple status badge ("장 중" / "장 마감"); prevents user confusion about why nothing fired |

### Anti-features (do NOT build yet)

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Manual order entry from dashboard | Out of scope per PROJECT.md; adds complexity and regulatory surface area | Keep dashboard read-only; link to rules page for control |
| Multi-account view | Only one paper account per user for now | Single-account view only; multi-account is future milestone |
| Real-time WebSocket push | Significant infra overhead; paper trades fire at most once per minute (scheduler) | 10–30 second polling is sufficient for PAPER_LIVE |
| Mobile-optimized layout | Web-first per PROJECT.md | Responsive grid is fine; no dedicated mobile breakpoints needed yet |

**Dependencies:** Dashboard requires PAPER_LIVE engine (scheduler) to be running first. Without it, positions never change and the dashboard is static. Build the engine before polishing the dashboard UI.

---

## Backtest Visualization

Current state: numbers-only table with 5 metrics. The equity curve data is already returned by `BacktestResult.equityCurve` but not rendered. This is the highest-ROI gap to close.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Equity curve chart | Industry standard for backtests; without it users cannot see drawdown shape or recovery time | Medium | Data already in API response (`equityCurve: [{date, equity}]`). Add Recharts LineChart or TradingView Lightweight Charts. Chart lib decision is still open per PROJECT.md. |
| Drawdown chart (underwater chart) | Paired with equity curve; shows how long strategy was losing before recovering | Medium | Derived from equity curve: `drawdown[i] = (equity[i] - running_max) / running_max`. Can overlay on equity chart as shaded area. |
| Trade entry/exit markers on chart | Users want to see WHERE the bot bought and sold on the price/equity line | Medium | Overlay BUY/SELL markers from `trades[]` onto the equity curve timeline |
| Benchmark comparison (KOSPI 200) | Context without a benchmark is meaningless — "was my 12% return good?" | High | Requires fetching KODEX 200 (069500) daily closes for the same period; overlay as second line. **Needs separate data source decision.** |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Sharpe Ratio | Most recognized risk-adjusted metric; separates lucky from skilled | Low | Compute server-side from `equityCurve`: annualized_return / annualized_stddev. Add to `BacktestResult`. |
| Sortino Ratio | Better than Sharpe for non-symmetric return distributions (ignores upside volatility) | Low | Same as Sharpe but stddev uses only negative-return days. Add alongside Sharpe. |
| Profit Factor | Gross profit / gross loss; immediately interpretable (>1 = profitable overall) | Low | Computable from `trades[].pnl`; add to `BacktestResult`. |
| Monthly returns heatmap | Calendar grid of monthly P&L — immediately reveals seasonality and rough periods | Medium | Group `equityCurve` by month, compute monthly return %, render as color grid (red/green). High impact visual. |
| Max consecutive losses | Risk feel beyond just MDD; "how many losing trades in a row?" | Low | Scan `trades[].pnl` for runs of negative values. Add to `BacktestResult`. |

### Anti-features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Walk-forward / Monte Carlo simulation | Very complex to implement correctly; high over-engineering risk for this milestone | Flag as future research phase; simple out-of-sample split is sufficient |
| Parameter optimization sweep | Opens overfitting trap; requires compute cluster mentality | Users adjust params manually and re-run; keeps complexity low |
| Live benchmark feed (real-time KOSPI index) | Only relevant for paper/live, not backtest | Use static historical close data for backtest benchmark |

**Dependencies:** Chart library must be chosen before building any visualization. Recommendation: **Recharts** for consistency with React 18 / existing stack (lighter, no IFrame). TradingView Lightweight Charts is better for candlesticks but adds bundle weight and a more complex API surface. Backtest visualization does not need candlesticks — line charts suffice.

**Computed metrics (Sharpe, Sortino, Profit Factor) should be added to `BacktestResult` server-side**, not client-side, so they are consistent across backtest and performance report pages.

---

## Rule Lifecycle

Current state: frontend shows DRAFT / ACTIVE / PAUSED statuses. PROJECT.md defines the target flow as DRAFT → BACKTESTED → PAPER_LIVE → LIVE. These do not match.

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Explicit status display on rule list | Users must know at a glance whether a rule is running, paused, or draft | Low | Already partially implemented; needs status badge styling for each state |
| DRAFT → BACKTESTED gate | Prevent a rule from going PAPER_LIVE without at least one backtest run | Low | On "promote to paper" action, check if any backtest result exists for this rule_id. Block if not. |
| BACKTESTED → PAPER_LIVE promotion UI | One-click promotion with confirmation dialog | Low | API already exists (`setStatus`); needs dedicated promote button, not just a status dropdown edit |
| PAPER_LIVE → LIVE promotion gate | This is the real-money gate. Hard block without user acknowledgment | Medium | Require: (a) minimum paper run duration (e.g., 5 trading days), (b) explicit "I understand this uses real money" confirmation checkbox, (c) Toss Securities OAuth token present |
| Pause / resume PAPER_LIVE rule | Stop evaluation without deleting rule | Low | Set status=PAUSED; scheduler skips PAUSED rules |
| Rule mode separation (PAPER vs LIVE) | PAPER and LIVE rules are different entities; editing a LIVE rule is dangerous | Medium | LIVE rules should be read-only in the editor; promote creates a new copy, does not mutate original |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Backtest result summary on rule card | Show last backtest return% and MDD inline on the rule list card | Low | Store last backtest result reference on rule or as separate table; saves clicking into backtest page |
| Promotion history / audit log | "This LIVE rule was promoted from paper rule #42 on 2026-06-15" | Low | `promoted_from` column already exists on `TradingRule`; just needs UI display |
| Rule clone | Duplicate a rule to experiment with parameter changes without losing the original | Low | Server-side copy of definition with status=DRAFT |
| Cooldown enforcement UI | Show when a rule is in cooldown (cannot fire again yet) per `constraints.cooldownBars` | Medium | Requires tracking last_fired_at per rule; display "cooldown until X" on rule card |

### Anti-features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Free-text rule name editing on LIVE rules | Risk of user confusion about which version is which | Lock name on LIVE rules; allow description/notes field only |
| Auto-promote based on backtest score | Removes human decision from real-money transition; dangerous | Always require explicit human promote action with confirmation |
| Multi-rule conflict resolution | "What if two rules want to buy the same stock simultaneously?" is a real problem but complex | For this milestone: first-signal-wins; document this behavior |

**Critical dependency:** The status machine in `TradingRule` currently uses "DRAFT / ACTIVE / PAUSED" on both PAPER and LIVE modes. The lifecycle design needs a clear decision: either (a) use `status + mode` together as the gate machine (DRAFT+PAPER → ACTIVE+PAPER → ACTIVE+LIVE), or (b) introduce BACKTESTED and PAPER_LIVE as explicit status values. Option (b) matches PROJECT.md and is clearer for users. This decision must be made before building the lifecycle UI.

---

## Real-time Monitor

Current state: `TradingMonitorPage` is a "준비 중" stub. This page answers: "What is my bot doing right now?"

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Signal evaluation log | "At 10:32, RSI crossed below 30 for 005930 → BUY signal fired" — proof the bot is thinking | Medium | Each scheduler evaluation cycle should write a log record: timestamp, rule_id, symbol, indicator values, signal outcome |
| Order/trade execution feed | Chronological list of what the bot actually did today | Low | Query `paper_trades` or `paper_orders` WHERE `traded_at >= today` ordered by time; auto-refresh |
| Active positions panel | Real-time view of what's currently held (same data as dashboard but in context of monitoring) | Low | Shared component with dashboard; positions + unrealized P&L |
| Scheduler heartbeat indicator | "Last evaluated: 10:33:05 KST" — confirms the engine is running | Low | Store last_run_at in a scheduler state table or in-memory bean; expose via `/api/monitor/status` |
| Market hours gate display | Clear "장 마감 — 다음 평가: 내일 09:00" when outside trading hours | Low | Simple time-based computation; prevents panic when logs stop at 15:30 |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Per-symbol indicator values at last eval | "RSI(14) = 28.3, SMA(20) = 74,200" — transparency into why signal fired or did not | Medium | Requires `RuleEvaluator` to emit indicator snapshot alongside signal; store in signal log |
| Signal → no-action explanation | "Entry condition met BUT position already held (maxPositionsPerSymbol=1)" — reduces confusion | Medium | Add reason field to signal log: BOUGHT / SKIPPED_HELD / SKIPPED_CASH / NO_SIGNAL |
| Filter log by rule | When multiple rules are running, isolate one rule's activity | Low | Client-side filter on rule_id in the log feed |
| Export today's log to CSV | Useful for debugging and personal records | Low | Server endpoint; low priority but quick to add |

### Anti-features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| WebSocket push for every signal | Scheduler fires at most once per minute; overkill infra | 10-second polling on monitor page is sufficient |
| Full tick-level data stream | Not relevant for minute-bar scheduler; misleads users into thinking bot is faster than it is | Show per-evaluation-cycle granularity only |
| Alert notifications (email/push) | Significant infra (email service, FCM); not in scope for this milestone | Add as future enhancement; log feed is sufficient now |
| Real-time P&L auto-trade suggestions | This becomes advisory product territory | Keep monitor as read-only observation, not suggestion engine |

**Key design decision for signal log:** The scheduler (`@Scheduled`) must write evaluation results to a persistent table (not just log to console). Suggested table: `paper_signal_log (id, rule_id, symbol, evaluated_at, indicator_snapshot jsonb, signal, reason, trade_id)`. Without this, the monitor page has nothing to display.

---

## Performance Report

Current state: `PaperReportPage` is a "준비 중" stub. This is the post-hoc analysis view: "How has my paper trading strategy performed over time?"

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Full equity curve chart (time-series) | Same as backtest but for live paper period; essential for any performance view | Medium | Source: `paper_equity_snapshots`; already being written by the ledger. Needs API + chart. |
| Summary metrics card set: return%, MDD, win rate, trade count | Minimum viable performance summary; every trading journal shows these | Low | Compute from `paper_trades` and `paper_equity_snapshots`; same logic as `BacktestService` |
| Trade history table with P&L | Filterable, sortable list of all paper trades with realized P&L | Low | `paper_trades` table is ready; query by account_id, paginate |
| Per-period return summary | "How did each month perform?" — essential for spotting seasonal weakness | Medium | Group `paper_equity_snapshots` by month; compute month-over-month return |

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Sharpe / Sortino / Profit Factor (paper period) | Bridges backtest and live performance; users compare these numbers to see if live degrades vs backtest | Low | Same computation as backtest; use `paper_equity_snapshots` as equity curve input |
| Backtest vs. paper comparison panel | Side-by-side: backtest predicted 18% return, paper achieved 14% — realistic expectation calibration | Medium | Requires storing which backtest result corresponds to this paper run; add `backtest_result_id` reference |
| Per-rule performance breakdown | If multiple rules are running, which one is winning? | Medium | Group `paper_trades.rule_id` → P&L, win rate per rule; display as ranked table |
| Monthly returns heatmap (paper period) | Same as backtest heatmap but for live paper data; shows real performance calendar | Medium | Same component as backtest heatmap; different data source |

### Anti-features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| PDF export of report | Disproportionate complexity (headless browser or PDF lib); low usage | Browser print-to-PDF is sufficient |
| Tax lot tracking / realized gain/loss for tax | Korean tax reporting for stock gains is complex and out of scope | Paper is virtual; no tax implications |
| Benchmark-adjusted alpha / beta calculations | Requires reliable real-time benchmark data pipeline not yet built | Add benchmark line to equity chart as a visual; defer alpha/beta to future milestone |
| Comparison across multiple rules' historical performance | Requires careful date-range alignment and shared timeline; complex UX | Single-rule performance report first; multi-rule comparison is v2 |

**Critical dependency for report accuracy:** `paper_equity_snapshots` must be written consistently by the scheduler — once per evaluation cycle at minimum, once per day at close. If the scheduler skips a day, there is a gap in the equity curve. The scheduler must write an EOD snapshot even on days with no trades.

---

## Feature Dependencies Map

```
Real-time scheduler (PAPER_LIVE engine)
  └─ paper_signal_log table
       └─ Real-time Monitor (signal feed, heartbeat)
  └─ paper_positions updates
       └─ Paper Dashboard (positions, unrealized P&L)
  └─ paper_trades writes
       └─ Performance Report (trade history, per-rule P&L)
  └─ paper_equity_snapshots writes
       └─ Paper Dashboard (equity mini-chart)
       └─ Performance Report (equity curve, Sharpe, MDD)

Backtest engine (exists)
  └─ equityCurve data (exists)
       └─ Equity curve chart (missing chart lib)
  └─ BacktestResult metrics
       └─ Need: Sharpe, Sortino, Profit Factor added server-side

Rule lifecycle state machine
  └─ BACKTESTED gate → enables PAPER_LIVE promotion
  └─ PAPER_LIVE gate → enables LIVE promotion (Toss OAuth required)
  └─ LIVE mode → Toss Securities API orders
```

**Build order implied by dependencies:**
1. Chart library decision + equity curve chart (backtest page — highest ROI, engine exists)
2. Add Sharpe/Sortino/Profit Factor to `BacktestResult` server-side
3. Real-time scheduler (PAPER_LIVE engine) + signal log table
4. Paper Dashboard (unblocked once scheduler runs)
5. Rule lifecycle promotion UI (unblocked once scheduler exists)
6. Real-time Monitor (unblocked once signal log is populated)
7. Performance Report (unblocked once equity snapshots accumulate)
8. Toss Securities OAuth + LIVE order execution (last — depends on all above validated)

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Paper dashboard features | HIGH | Standard across all paper trading products; DB schema confirms what data is available |
| Backtest visualization | HIGH | Industry standard metrics (Sharpe, Sortino, MDD, equity curve) are well-documented; equity curve data already in API |
| Rule lifecycle | HIGH | Pattern is standard in algo platforms; current codebase gaps are clearly visible |
| Real-time monitor | MEDIUM | Signal log design is inferred from industry practice; specific table schema needs validation during implementation |
| Performance report | HIGH | Metrics well-documented; data sources confirmed in DB schema |
| Benchmark data (KOSPI 200) | LOW | No current data pipeline for benchmark; Yahoo Finance historical may suffice for backtest but needs validation |

---

*Researched: 2026-06-20*
*Sources: uTrade Algos backtest metrics, QuantStart Sharpe ratio guide, TradingStation performance report docs, LuxAlgo backtest metrics, QuantifiedStrategies performance metrics, ETNA paper trading platform analysis, TradeStation portfolio reports*

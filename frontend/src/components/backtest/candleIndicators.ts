/**
 * candleIndicators.ts — pure helpers (no React) for CandleChart overlays and markers.
 *
 * Hex → Tailwind token mapping (standard palette, NOT custom cream/charcoal):
 *   #10b981 = emerald-500   (BUY marker)
 *   #ef4444 = red-500       (SELL marker)
 *   #f59e0b = amber-500     (indicator lines)
 *
 * lightweight-charts requires literal color strings; these are standard-palette equivalents
 * documented here per CLAUDE.md (no hardcoded hex intent).
 */

import type { SeriesMarker, Time } from "lightweight-charts";
import type { BacktestTrade, CandleBar, RuleDefinition } from "@/types/trading";

// ---------------------------------------------------------------------------
// extractIndicators
// ---------------------------------------------------------------------------

export interface IndicatorSpec {
  indicator: "SMA" | "EMA";
  period: number;
}

/**
 * Scan a RuleDefinition for SMA/EMA operands that carry a period.
 * Returns a deduplicated list suitable for computing overlay line series.
 * PRICE/RSI/VOLUME are excluded — non-price-scale lines confuse the chart (RESEARCH Pattern 6).
 */
export function extractIndicators(def: RuleDefinition): IndicatorSpec[] {
  const seen = new Set<string>();
  const result: IndicatorSpec[] = [];

  const tryAdd = (indicator: string | undefined, period: number | undefined) => {
    if (
      (indicator === "SMA" || indicator === "EMA") &&
      typeof period === "number" &&
      Number.isFinite(period) &&
      period > 0
    ) {
      const key = `${indicator}-${period}`;
      if (!seen.has(key)) {
        seen.add(key);
        result.push({ indicator: indicator as "SMA" | "EMA", period });
      }
    }
  };

  const scanConditions = (conditions: typeof def.entry.conditions) => {
    for (const cond of conditions ?? []) {
      tryAdd(cond.left?.indicator, cond.left?.params?.period);
      tryAdd(cond.right?.indicator, cond.right?.params?.period);
    }
  };

  scanConditions(def.entry?.conditions ?? []);
  scanConditions(def.exit?.conditions ?? []);

  return result;
}

// ---------------------------------------------------------------------------
// computeSMA
// ---------------------------------------------------------------------------

/**
 * Sliding-window simple moving average of close prices.
 * Mirrors Indicators.java SMA logic. Output starts at index (period - 1).
 */
export function computeSMA(
  bars: CandleBar[],
  period: number
): Array<{ time: number; value: number }> {
  if (bars.length < period) return [];

  const result: Array<{ time: number; value: number }> = [];
  let windowSum = 0;

  for (let i = 0; i < bars.length; i++) {
    // noUncheckedIndexedAccess: bounds already guaranteed by loop condition
    const bar = bars[i]!;
    windowSum += bar.close;
    if (i >= period) {
      windowSum -= bars[i - period]!.close;
    }
    if (i >= period - 1) {
      result.push({ time: bar.time, value: windowSum / period });
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// computeEMA
// ---------------------------------------------------------------------------

/**
 * Standard EMA seeded with SMA of first `period` closes (k = 2 / (period + 1)).
 * Mirrors Indicators.java EMA logic.
 */
export function computeEMA(
  bars: CandleBar[],
  period: number
): Array<{ time: number; value: number }> {
  if (bars.length < period) return [];

  const k = 2 / (period + 1);
  const result: Array<{ time: number; value: number }> = [];

  // Seed: SMA of first `period` bars
  // noUncheckedIndexedAccess: bounds guaranteed by loop condition + early return above
  let seed = 0;
  for (let i = 0; i < period; i++) {
    seed += bars[i]!.close;
  }
  let ema = seed / period;
  result.push({ time: bars[period - 1]!.time, value: ema });

  for (let i = period; i < bars.length; i++) {
    const bar = bars[i]!;
    ema = bar.close * k + ema * (1 - k);
    result.push({ time: bar.time, value: ema });
  }

  return result;
}

// ---------------------------------------------------------------------------
// computeRSI
// ---------------------------------------------------------------------------

/**
 * Simple (non-Wilder) RSI of close prices — mirrors Indicators.java rsi().
 * RSI at index i uses the `period` diffs ending at i, so output starts at i=period.
 */
export function computeRSI(
  bars: CandleBar[],
  period: number
): Array<{ time: number; value: number }> {
  if (period <= 0 || bars.length <= period) return [];

  const result: Array<{ time: number; value: number }> = [];
  for (let i = period; i < bars.length; i++) {
    let gain = 0;
    let loss = 0;
    for (let t = i - period + 1; t <= i; t++) {
      const diff = bars[t]!.close - bars[t - 1]!.close;
      if (diff >= 0) gain += diff;
      else loss -= diff;
    }
    const avgGain = gain / period;
    const avgLoss = loss / period;
    let rsi: number;
    if (avgLoss === 0) rsi = avgGain === 0 ? 50 : 100;
    else rsi = 100 - 100 / (1 + avgGain / avgLoss);
    result.push({ time: bars[i]!.time, value: rsi });
  }
  return result;
}

// ---------------------------------------------------------------------------
// indicatorsFromRationale — derive indicators from a trade's rationale JSON
// ---------------------------------------------------------------------------

export interface RationaleIndicators {
  overlays: IndicatorSpec[]; // SMA/EMA → main-chart line overlays
  rsiPeriods: number[]; // RSI → bottom subchart pane
}

/**
 * Parse a trade's rationaleJson and extract the indicators that drove the
 * BUY/SELL decision. Condition labels look like "RSI(14)", "SMA(20)", "EMA(12)".
 * SMA/EMA become overlays; RSI becomes a subchart. Unknown/PRICE/VOLUME ignored.
 */
export function indicatorsFromRationale(
  rationaleJson: string | null | undefined
): RationaleIndicators {
  const overlays: IndicatorSpec[] = [];
  const rsiPeriods: number[] = [];
  const seen = new Set<string>();
  if (!rationaleJson) return { overlays, rsiPeriods };

  try {
    const obj = JSON.parse(rationaleJson) as {
      conditions?: Array<{ leftLabel?: string; rightLabel?: string }>;
      rationale?: { conditions?: Array<{ leftLabel?: string; rightLabel?: string }> };
    };
    // 백테스트(buildRationale)는 conditions를 최상위에, 라이브 이력(mergeRationale)은
    // rationale.conditions에 넣는다 — 두 구조 모두 지원.
    const conditions = obj?.rationale?.conditions ?? obj?.conditions ?? [];
    for (const cond of conditions) {
      for (const label of [cond.leftLabel, cond.rightLabel]) {
        const m = /^(RSI|SMA|EMA)\((\d+)\)/.exec(label ?? "");
        if (!m) continue;
        const indicator = m[1] as "RSI" | "SMA" | "EMA";
        const period = parseInt(m[2]!, 10);
        const key = `${indicator}-${period}`;
        if (seen.has(key)) continue;
        seen.add(key);
        if (indicator === "RSI") rsiPeriods.push(period);
        else overlays.push({ indicator, period });
      }
    }
  } catch {
    // malformed rationale — return whatever parsed
  }
  return { overlays, rsiPeriods };
}

// ---------------------------------------------------------------------------
// Time helpers (KST-aware)
// ---------------------------------------------------------------------------

const KST_TZ = "Asia/Seoul";

/**
 * Convert a trade datetime string to Unix epoch seconds, aligned with the bar
 * `time` field (which is the bar instant's epoch seconds from the backend).
 *
 * Backtest trades carry a zone-less KST wall-clock LocalDateTime
 * (e.g. "2026-06-11T09:00:00"); paper-history trades carry a UTC instant with a
 * zone ("...Z"). We trust an explicit zone when present and otherwise assume KST,
 * so the resulting instant matches the bar's epoch regardless of browser timezone.
 */
export function toEpochSec(datetime: string): number {
  const hasZone = /([zZ]|[+-]\d{2}:?\d{2})$/.test(datetime);
  const iso = hasZone ? datetime : `${datetime}+09:00`;
  return Math.floor(new Date(iso).getTime() / 1000);
}

/** Format epoch-seconds as KST "HH:mm" (axis ticks within a single session). */
export function fmtKstTime(epochSec: number): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TZ,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(epochSec * 1000);
}

/** Format epoch-seconds as KST "MM-DD HH:mm" (crosshair label). */
export function fmtKstDateTime(epochSec: number): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TZ,
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(epochSec * 1000);
}

/** Format a trade datetime string as a readable KST "YYYY-MM-DD HH:mm" for tables. */
export function fmtTradeKst(datetime: string): string {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: KST_TZ,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
    .format(toEpochSec(datetime) * 1000)
    .replace(/\. /g, "-")
    .replace(/\.$/, "");
}

// ---------------------------------------------------------------------------
// tradesToMarkers
// ---------------------------------------------------------------------------

/** Build a single lightweight-charts marker from one trade. */
export function tradeToMarker(t: BacktestTrade): SeriesMarker<Time> {
  const time = toEpochSec(t.datetime) as Time;
  if (t.side === "BUY") {
    return {
      time,
      position: "belowBar" as const,
      shape: "arrowUp" as const,
      color: "#10b981", // emerald-500
      text: "B",
      size: 1,
    };
  }
  return {
    time,
    position: "aboveBar" as const,
    shape: "arrowDown" as const,
    color: "#ef4444", // red-500
    text: "S",
    size: 1,
  };
}

/**
 * Convert BacktestTrade[] to lightweight-charts SeriesMarker objects.
 * Only trades whose datetime starts with filterDate (YYYY-MM-DD) are included.
 *
 * BUY  → position "belowBar" / shape "arrowUp"  / color #10b981 (emerald-500) / text "B"
 * SELL → position "aboveBar" / shape "arrowDown" / color #ef4444 (red-500)     / text "S"
 */
export function tradesToMarkers(
  trades: BacktestTrade[],
  filterDate: string
): SeriesMarker<Time>[] {
  return trades
    .filter((t) => t.datetime.startsWith(filterDate))
    .map(tradeToMarker);
}

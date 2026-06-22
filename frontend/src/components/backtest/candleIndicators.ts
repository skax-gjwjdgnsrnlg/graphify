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
// tradesToMarkers
// ---------------------------------------------------------------------------

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
    .map((t) => {
      const time = Math.floor(new Date(t.datetime).getTime() / 1000) as Time;
      if (t.side === "BUY") {
        return {
          time,
          position: "belowBar" as const,
          shape: "arrowUp" as const,
          color: "#10b981", // emerald-500
          text: "B",
          size: 1,
        };
      } else {
        return {
          time,
          position: "aboveBar" as const,
          shape: "arrowDown" as const,
          color: "#ef4444", // red-500
          text: "S",
          size: 1,
        };
      }
    });
}

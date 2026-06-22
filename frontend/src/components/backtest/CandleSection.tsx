/**
 * CandleSection.tsx — self-contained candle chart section (reused by both pages).
 *
 * Wraps bars useQuery + 4-state shell + indicator computation + CandleChart mount.
 * 4 dark-themed inline states (NOT shared/ EmptyState/ErrorBanner — light-theme cream/charcoal
 * tokens would clash with dark gray-900 page backgrounds per RESEARCH Pitfall 7).
 *
 * Token mapping (standard Tailwind palette only, no custom hex):
 *   bg-gray-900/50, border-white/10, text-gray-500 — card/empty state
 *   border-red-500/30 bg-red-500/10 text-red-300    — error state
 *   border-white/20 border-t-emerald-500             — loading spinner
 */

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchBars } from "@/lib/ruleApi";
import type { BacktestTrade } from "@/types/trading";
import {
  computeEMA,
  computeSMA,
} from "./candleIndicators";
import CandleChart from "./CandleChart";

interface IndicatorSpec {
  indicator: "SMA" | "EMA";
  period: number;
}

interface CandleSectionProps {
  symbol: string | null;
  date: string | null;
  trades: BacktestTrade[];
  indicators: IndicatorSpec[];
  highlightTime?: number;
}

export function CandleSection({
  symbol,
  date,
  trades,
  indicators,
  highlightTime,
}: CandleSectionProps) {
  const {
    data: bars = [],
    isLoading,
    isError,
    refetch,
  } = useQuery({
    queryKey: ["backtest", "bars", symbol, date],
    queryFn: async () => (await fetchBars(symbol!, date!)).data ?? [],
    enabled: !!symbol && !!date,
  });

  const indicatorLines = useMemo(() => {
    if (!bars.length) return [];
    return indicators.map(({ indicator, period }) => ({
      label: `${indicator}(${period})`,
      data:
        indicator === "SMA"
          ? computeSMA(bars, period)
          : computeEMA(bars, period),
    }));
  }, [bars, indicators]);

  return (
    <div
      data-testid="candle-section"
      className="rounded-lg border border-white/10 bg-gray-900/50 p-4"
    >
      <h3 className="mb-4 text-sm font-medium text-gray-300">5분봉 캔들 차트</h3>

      {/* State: no selection */}
      {!symbol || !date ? (
        <div className="flex h-[400px] items-center justify-center rounded-lg border border-white/10 bg-gray-900/50 text-sm text-gray-500">
          표시할 거래가 없습니다.
        </div>
      ) : isLoading ? (
        /* State: loading */
        <div className="flex h-[400px] items-center justify-center">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-white/20 border-t-emerald-500" />
        </div>
      ) : isError ? (
        /* State: error */
        <div className="flex h-[400px] items-center justify-center rounded-lg border border-red-500/30 bg-red-500/10 px-6 text-center text-sm text-red-300">
          캔들 데이터를 불러오지 못했습니다.{" "}
          <button onClick={() => void refetch()} className="ml-1 underline">
            재시도
          </button>
        </div>
      ) : bars.length === 0 ? (
        /* State: success but empty */
        <div className="flex h-[400px] items-center justify-center rounded-lg border border-white/10 bg-gray-900/50 text-sm text-gray-500">
          해당 일자의 5분봉 데이터가 없습니다.
        </div>
      ) : (
        /* State: success */
        <div data-testid="candle-chart">
          <CandleChart
            bars={bars}
            trades={trades}
            filterDate={date}
            indicatorLines={indicatorLines}
            highlightTime={highlightTime}
          />
        </div>
      )}
    </div>
  );
}

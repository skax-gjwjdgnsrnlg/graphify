/**
 * CandleSection.tsx — self-contained candle chart section (reused by both pages).
 *
 * Wraps bars useQuery + 4-state shell + indicator computation + CandleChart mount.
 * The 4 states use shared/ primitives in their dark tone (EmptyState / ErrorBanner /
 * SkeletonBlock with tone="dark"), so the section honors the shared-first rule while
 * fitting the dark gray-900 page backgrounds. Colors come from the standard Tailwind
 * palette via those primitives — no custom hex here.
 */

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchBars } from "@/lib/ruleApi";
import type { BacktestTrade } from "@/types/trading";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorBanner } from "@/components/shared/ErrorBanner";
import { SkeletonBlock } from "@/components/shared/SkeletonBlock";
import { computeEMA, computeSMA } from "./candleIndicators";
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

      {!symbol || !date ? (
        /* State: no selection */
        <EmptyState tone="dark" showHomeLink={false} title="표시할 거래가 없습니다." />
      ) : isLoading ? (
        /* State: loading */
        <SkeletonBlock tone="dark" className="h-[400px] w-full" />
      ) : isError ? (
        /* State: error */
        <ErrorBanner
          tone="dark"
          message="캔들 데이터를 불러오지 못했습니다."
          retryLabel="재시도"
          onRetry={() => void refetch()}
        />
      ) : bars.length === 0 ? (
        /* State: success but empty */
        <EmptyState
          tone="dark"
          showHomeLink={false}
          title="해당 일자의 5분봉 데이터가 없습니다."
        />
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

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
import { parseRationale } from "@/components/trading/TradeRationaleRow";
import {
  computeEMA,
  computeRSI,
  computeSMA,
  fmtTradeKst,
  indicatorsFromRationale,
  toEpochSec,
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
  highlightSide?: "BUY" | "SELL";
}

export function CandleSection({
  symbol,
  date,
  trades,
  indicators,
  highlightTime,
  highlightSide,
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

  // 클릭된 거래를 찾는다 (마커/근거/지표 공통 기준).
  const selectedTrade = useMemo(() => {
    if (highlightTime == null) return null;
    return (
      trades.find(
        (t) =>
          (!date || t.datetime.startsWith(date)) &&
          toEpochSec(t.datetime) === highlightTime &&
          (highlightSide == null || t.side === highlightSide)
      ) ?? null
    );
  }, [trades, highlightTime, highlightSide, date]);

  // 클릭된 거래의 매매 근거(rationale)에서 지표를 도출한다 (#4).
  // 거래 선택 시: 그 거래 근거의 SMA/EMA는 오버레이, RSI는 하단 서브차트.
  // 선택 없음: 룰 정의 기반 indicators(SMA/EMA 오버레이)로 폴백.
  const { overlaySpecs, rsiPeriod } = useMemo(() => {
    if (selectedTrade) {
      const { overlays, rsiPeriods } = indicatorsFromRationale(selectedTrade.rationaleJson);
      if (overlays.length || rsiPeriods.length) {
        return { overlaySpecs: overlays, rsiPeriod: rsiPeriods[0] ?? null };
      }
    }
    return { overlaySpecs: indicators, rsiPeriod: null as number | null };
  }, [selectedTrade, indicators]);

  // 선택된 거래의 매매 근거(저장된 실제 지표값) — 차트 위에 명시.
  const selectedRationale = useMemo(
    () => (selectedTrade ? parseRationale(selectedTrade.rationaleJson ?? null) : null),
    [selectedTrade]
  );

  const indicatorLines = useMemo(() => {
    if (!bars.length) return [];
    return overlaySpecs.map(({ indicator, period }) => ({
      label: `${indicator}(${period})`,
      data:
        indicator === "SMA"
          ? computeSMA(bars, period)
          : computeEMA(bars, period),
    }));
  }, [bars, overlaySpecs]);

  const rsiLine = useMemo(() => {
    if (!bars.length || rsiPeriod == null) return null;
    return { label: `RSI(${rsiPeriod})`, data: computeRSI(bars, rsiPeriod) };
  }, [bars, rsiPeriod]);

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
          {selectedRationale && selectedTrade ? (
            <div className="mb-3 rounded-md border border-white/10 bg-gray-800/40 p-3 text-xs">
              <div className="mb-1.5 font-medium">
                <span
                  className={
                    selectedTrade.side === "BUY" ? "text-emerald-400" : "text-red-400"
                  }
                >
                  {selectedTrade.side === "BUY" ? "매수" : "매도"} 근거
                </span>
                <span className="ml-2 text-gray-400">
                  {fmtTradeKst(selectedTrade.datetime)}
                </span>
              </div>
              <ul className="space-y-0.5">
                {selectedRationale.conditions.map((c, i) => (
                  <li key={i} className="text-gray-300">
                    {c.expr}
                    {Number.isFinite(c.leftValue) ? (
                      <span className="text-gray-400">
                        {" "}
                        (실제 {c.leftLabel} = {c.leftValue.toFixed(2)})
                      </span>
                    ) : null}
                    <span className={c.passed ? "text-emerald-400" : "text-gray-500"}>
                      {" "}
                      {c.passed ? "✓" : "✗"}
                    </span>
                  </li>
                ))}
                {selectedRationale.exitReason ? (
                  <li className="text-gray-400">
                    청산 사유: {selectedRationale.exitReason}
                    {selectedRationale.exitPct != null
                      ? ` (${selectedRationale.exitPct.toFixed(1)}%)`
                      : ""}
                  </li>
                ) : null}
              </ul>
            </div>
          ) : null}
          <CandleChart
            bars={bars}
            trades={trades}
            filterDate={date}
            indicatorLines={indicatorLines}
            rsiLine={rsiLine}
            highlightTime={highlightTime}
            highlightSide={highlightSide}
          />
        </div>
      )}
    </div>
  );
}

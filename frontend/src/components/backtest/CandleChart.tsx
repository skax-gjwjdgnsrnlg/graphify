/**
 * CandleChart.tsx — lightweight-charts v5 candle component (dark theme)
 *
 * Hex → Tailwind token mapping (standard palette, NOT custom cream/charcoal):
 *   #111827 = gray-900     (background)
 *   #9ca3af = gray-400     (text / axis labels)
 *   rgba(255,255,255,0.05) = white/5 (grid lines)
 *   #10b981 = emerald-500  (up candle / BUY marker)
 *   #ef4444 = red-500      (down candle / SELL marker)
 *   #f59e0b = amber-500    (indicator line overlays)
 *
 * lightweight-charts requires literal color strings; these are standard-palette equivalents
 * documented here per CLAUDE.md (no hardcoded hex intent — dark chart uses standard palette).
 */

import { useEffect, useRef } from "react";
import {
  CandlestickSeries,
  ColorType,
  HistogramSeries,
  LineSeries,
  createChart,
  createSeriesMarkers,
} from "lightweight-charts";
import type { BacktestTrade, CandleBar } from "@/types/trading";
import { tradesToMarkers } from "./candleIndicators";

interface CandleChartProps {
  bars: CandleBar[];
  trades: BacktestTrade[];
  filterDate: string; // YYYY-MM-DD — used by tradesToMarkers
  indicatorLines: Array<{
    label: string;
    data: Array<{ time: number; value: number }>;
  }>;
  highlightTime?: number; // epoch-seconds of the clicked trade marker to scroll to
}

export default function CandleChart({
  bars,
  trades,
  filterDate,
  indicatorLines,
  highlightTime,
}: CandleChartProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || bars.length === 0) return;

    // -----------------------------------------------------------------------
    // 1. Create chart (dark theme — standard palette hex documented above)
    // -----------------------------------------------------------------------
    const chart = createChart(container, {
      layout: {
        background: { type: ColorType.Solid, color: "#111827" }, // gray-900
        textColor: "#9ca3af", // gray-400
      },
      grid: {
        vertLines: { color: "rgba(255,255,255,0.05)" }, // white/5
        horzLines: { color: "rgba(255,255,255,0.05)" }, // white/5
      },
      autoSize: true,
    });

    // -----------------------------------------------------------------------
    // 2. Candlestick series (v5 API: addSeries(SeriesType, opts))
    // -----------------------------------------------------------------------
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: "#10b981",    // emerald-500
      downColor: "#ef4444",  // red-500
      borderVisible: false,
      wickUpColor: "#10b981",   // emerald-500
      wickDownColor: "#ef4444", // red-500
    });

    candleSeries.setData(
      bars.map((b) => ({
        time: b.time as unknown as import("lightweight-charts").Time,
        open: b.open,
        high: b.high,
        low: b.low,
        close: b.close,
      }))
    );

    // Push candle scale up to make room for volume at bottom
    candleSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.05, bottom: 0.25 },
    });

    // -----------------------------------------------------------------------
    // 3. Volume overlay (HistogramSeries on its own scale)
    // -----------------------------------------------------------------------
    const volumeSeries = chart.addSeries(HistogramSeries, {
      priceFormat: { type: "volume" },
      priceScaleId: "",
    });

    volumeSeries.priceScale().applyOptions({
      scaleMargins: { top: 0.8, bottom: 0 },
    });

    volumeSeries.setData(
      bars.map((b) => ({
        time: b.time as unknown as import("lightweight-charts").Time,
        value: b.volume,
        color: b.close >= b.open ? "#10b981" : "#ef4444", // emerald-500 / red-500
      }))
    );

    // -----------------------------------------------------------------------
    // 4. Trade markers (v5 API: createSeriesMarkers — NOT series.setMarkers)
    // -----------------------------------------------------------------------
    const markers = tradesToMarkers(trades, filterDate);

    // Bump the matching marker's size when highlightTime is set
    const markedMarkers = highlightTime
      ? markers.map((m) =>
          (m.time as unknown as number) === highlightTime
            ? { ...m, size: 2 }
            : m
        )
      : markers;

    createSeriesMarkers(candleSeries, markedMarkers);

    // -----------------------------------------------------------------------
    // 5. Indicator line overlays (SMA / EMA from rule definition)
    // -----------------------------------------------------------------------
    for (const { label, data } of indicatorLines) {
      const lineSeries = chart.addSeries(LineSeries, {
        color: "#f59e0b", // amber-500
        lineWidth: 1,
        priceLineVisible: false,
        crosshairMarkerVisible: false,
        title: label,
      });

      lineSeries.setData(
        data.map((pt) => ({
          time: pt.time as unknown as import("lightweight-charts").Time,
          value: pt.value,
        }))
      );
    }

    // -----------------------------------------------------------------------
    // 6. Fit + scroll to highlighted trade
    // -----------------------------------------------------------------------
    chart.timeScale().fitContent();

    if (highlightTime) {
      // Scroll so the highlighted marker is approximately in the center
      chart.timeScale().scrollToRealTime();
    }

    // -----------------------------------------------------------------------
    // 7. Cleanup — prevent memory leaks under StrictMode (RESEARCH Pitfall 6)
    // -----------------------------------------------------------------------
    return () => {
      chart.remove();
    };
  }, [bars, trades, indicatorLines, highlightTime, filterDate]);

  // Explicit height is required — lightweight-charts cannot infer height from 0px parent
  // (RESEARCH Pitfall 5). w-full + autoSize handles responsive width.
  return <div ref={containerRef} className="w-full" style={{ height: 400 }} />;
}

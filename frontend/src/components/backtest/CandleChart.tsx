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
  LineStyle,
  createChart,
  createSeriesMarkers,
} from "lightweight-charts";
import type { BacktestTrade, CandleBar } from "@/types/trading";
import {
  fmtKstDateTime,
  fmtKstTime,
  toEpochSec,
  tradeToMarker,
} from "./candleIndicators";

interface CandleChartProps {
  bars: CandleBar[];
  trades: BacktestTrade[];
  filterDate: string; // YYYY-MM-DD — limits markers to the selected session
  indicatorLines: Array<{
    label: string;
    data: Array<{ time: number; value: number }>;
  }>;
  // RSI series for the bottom subchart pane (#4 — shown when the clicked trade's
  // rationale is RSI-based). null/undefined → no RSI pane.
  rsiLine?: { label: string; data: Array<{ time: number; value: number }> } | null;
  highlightTime?: number; // epoch-seconds of the clicked trade marker
  highlightSide?: "BUY" | "SELL"; // side of the clicked trade (disambiguates same-time BUY/SELL)
}

export default function CandleChart({
  bars,
  trades,
  filterDate,
  indicatorLines,
  rsiLine,
  highlightTime,
  highlightSide,
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
      // Bar `time` is the bar instant's UTC epoch seconds; lightweight-charts
      // renders numeric time in UTC, so format ticks/crosshair explicitly in KST.
      timeScale: {
        timeVisible: true,
        secondsVisible: false,
        tickMarkFormatter: (time: unknown) => fmtKstTime(time as number),
      },
      localization: {
        timeFormatter: (time: unknown) => fmtKstDateTime(time as number),
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
    // 4. Trade marker — show ONLY the selected trade (v5 createSeriesMarkers)
    //    Matched by epoch-seconds AND side so a BUY and SELL at the same bar
    //    don't both render when only one was clicked.
    // -----------------------------------------------------------------------
    const selectedTrade =
      highlightTime != null
        ? trades.find(
            (t) =>
              t.datetime.startsWith(filterDate) &&
              toEpochSec(t.datetime) === highlightTime &&
              (highlightSide == null || t.side === highlightSide)
          )
        : undefined;

    if (selectedTrade) {
      createSeriesMarkers(candleSeries, [
        { ...tradeToMarker(selectedTrade), size: 2 },
      ]);
    }

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
    // 5.5 RSI subchart (bottom pane) — shown when the clicked trade is RSI-based (#4)
    //     v5: addSeries(..., paneIndex) creates a separate pane. 30/70 guide lines.
    // -----------------------------------------------------------------------
    if (rsiLine && rsiLine.data.length > 0) {
      const rsiSeries = chart.addSeries(
        LineSeries,
        {
          color: "#8b5cf6", // violet-500 (distinct from amber SMA/EMA overlays)
          lineWidth: 1,
          priceLineVisible: false,
          crosshairMarkerVisible: false,
          title: rsiLine.label,
        },
        1 // paneIndex → bottom subchart
      );
      rsiSeries.setData(
        rsiLine.data.map((pt) => ({
          time: pt.time as unknown as import("lightweight-charts").Time,
          value: pt.value,
        }))
      );
      // Overbought/oversold reference lines
      rsiSeries.createPriceLine({
        price: 70,
        color: "#ef4444", // red-500
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: "70",
      });
      rsiSeries.createPriceLine({
        price: 30,
        color: "#10b981", // emerald-500
        lineWidth: 1,
        lineStyle: LineStyle.Dashed,
        axisLabelVisible: true,
        title: "30",
      });
      const rsiPane = chart.panes()[1];
      if (rsiPane) rsiPane.setHeight(120);
    }

    // -----------------------------------------------------------------------
    // 6. Viewport — center the clicked trade with surrounding context (#3)
    // -----------------------------------------------------------------------
    if (highlightTime != null && bars.length > 0) {
      // nearest bar index to the clicked trade's time
      let idx = 0;
      let best = Infinity;
      for (let i = 0; i < bars.length; i++) {
        const d = Math.abs(bars[i]!.time - highlightTime);
        if (d < best) {
          best = d;
          idx = i;
        }
      }
      const span = 20; // bars shown on each side of the trade
      chart.timeScale().setVisibleLogicalRange({
        from: idx - span,
        to: idx + span,
      });
    } else {
      chart.timeScale().fitContent();
    }

    // -----------------------------------------------------------------------
    // 7. Cleanup — prevent memory leaks under StrictMode (RESEARCH Pitfall 6)
    // -----------------------------------------------------------------------
    return () => {
      chart.remove();
    };
  }, [bars, trades, indicatorLines, rsiLine, highlightTime, highlightSide, filterDate]);

  // Explicit height is required — lightweight-charts cannot infer height from 0px parent
  // (RESEARCH Pitfall 5). w-full + autoSize handles responsive width.
  // Taller when an RSI subchart pane is present so candles stay readable.
  return (
    <div
      ref={containerRef}
      className="w-full"
      style={{ height: rsiLine && rsiLine.data.length > 0 ? 520 : 400 }}
    />
  );
}

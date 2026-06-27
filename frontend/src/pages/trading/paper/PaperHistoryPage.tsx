import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchPaperHistory } from "@/lib/paperApi";
import type { PaperTradeHistoryItem } from "@/types/paper";
import { TradeRationaleRow, parseRationale } from "@/components/trading/TradeRationaleRow";
import { CandleSection } from "@/components/backtest/CandleSection";
import { toEpochSec } from "@/components/backtest/candleIndicators";
import type { BacktestTrade } from "@/types/trading";
import { TradePageState } from "@/components/trading/ui";

export function PaperHistoryPage() {
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [selected, setSelected] = useState<{
    symbol: string;
    date: string;
    time: number;
    side: "BUY" | "SELL";
  } | null>(null);

  const { data, isLoading, isError, dataUpdatedAt } = useQuery<PaperTradeHistoryItem[]>({
    queryKey: ["trading", "paper", "history"],
    queryFn: async () => {
      const res = await fetchPaperHistory();
      return res.data ?? [];
    },
    refetchInterval: 30000,
  });

  // Auto-select first trade when data loads (locked §1)
  useEffect(() => {
    if (data && data.length > 0 && !selected) {
      const t: PaperTradeHistoryItem = data[0]!;
      setSelected({
        symbol: t.symbol,
        date: t.tradedAt.slice(0, 10),
        time: toEpochSec(t.tradedAt),
        side: t.side,
      });
    }
    // Only run when data changes (not selected — intentional single-trigger on load)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  const lastUpdated =
    dataUpdatedAt > 0
      ? new Date(dataUpdatedAt).toLocaleTimeString("ko-KR", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
        })
      : null;

  if (isLoading) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
            모의 거래 이력
          </h2>
        </div>
        <TradePageState variant="loading" className="h-[300px]" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 거래 이력
        </h2>
        <TradePageState
          variant="error"
          message="거래 이력을 불러오지 못했습니다."
        />
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="space-y-4">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 거래 이력
        </h2>
        <TradePageState variant="empty" message="체결된 모의 거래가 없습니다." />
      </div>
    );
  }

  // Map PaperTradeHistoryItem[] → BacktestTrade[] shape for CandleSection markers
  const candleTrades: BacktestTrade[] = data.map((t) => ({
    datetime: t.tradedAt,
    symbol: t.symbol,
    companyName: t.companyName ?? null,
    side: t.side,
    qty: t.qty,
    price: t.price,
    pnl: t.pnl,
    rationaleJson: t.rationaleJson ?? null,
  }));

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 거래 이력
        </h2>
        {lastUpdated && (
          <p className="text-xs font-trade-mono text-trade-muted">
            30초 자동 갱신 · {lastUpdated}
          </p>
        )}
      </div>

      {/* Candle chart — reuses CandleSection (Task 1 reskin) */}
      <CandleSection
        symbol={selected?.symbol ?? null}
        date={selected?.date ?? null}
        trades={candleTrades}
        indicators={[]}
        highlightTime={selected?.time}
        highlightSide={selected?.side}
      />

      <p className="text-xs text-trade-muted font-trade-sans">
        행을 클릭하면 매매 근거를 확인할 수 있습니다.
      </p>

      {/* Trade history table */}
      <div className="rounded-lg border border-trade-hairline bg-trade-surface overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-trade-hairline text-trade-muted text-left">
                <th className="px-4 py-3 font-medium font-trade-sans">체결시각</th>
                <th className="px-4 py-3 font-medium font-trade-sans">종목</th>
                <th className="px-4 py-3 font-medium font-trade-sans">구분</th>
                <th className="px-4 py-3 font-medium font-trade-sans text-right">수량</th>
                <th className="px-4 py-3 font-medium font-trade-sans text-right">가격</th>
                <th className="px-4 py-3 font-medium font-trade-sans text-right">손익</th>
                <th className="px-4 py-3 font-medium text-right w-8"></th>
              </tr>
            </thead>
            <tbody>
              {data.map((t) => {
                const isExpanded = expandedId === t.id;
                const isSelected =
                  selected != null &&
                  t.symbol === selected.symbol &&
                  toEpochSec(t.tradedAt) === selected.time &&
                  t.side === selected.side;
                const rationale = parseRationale(t.rationaleJson ?? null);
                const hasRationale = rationale !== null;
                const handleRowClick = () => {
                  // Always load chart for this trade (RESEARCH Pattern 7)
                  setSelected({
                    symbol: t.symbol,
                    date: t.tradedAt.slice(0, 10),
                    time: toEpochSec(t.tradedAt),
                    side: t.side,
                  });
                  // Toggle accordion only when rationale is available
                  if (hasRationale) {
                    setExpandedId(isExpanded ? null : t.id);
                  }
                };
                return (
                  <>
                    <tr
                      key={`row-${t.id}`}
                      onClick={handleRowClick}
                      className={`cursor-pointer border-b border-trade-hairline/50 hover:bg-trade-elevated transition-colors ${
                        isSelected
                          ? "border-l-2 border-l-trade-primary bg-trade-elevated"
                          : ""
                      }`}
                    >
                      <td className="px-4 py-3 text-trade-muted font-trade-mono">
                        {new Date(t.tradedAt).toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 text-trade-body font-medium font-trade-sans">
                        {t.companyName
                          ? `${t.companyName} (${t.symbol})`
                          : t.symbol}
                      </td>
                      <td className="px-4 py-3">
                        {t.side === "BUY" ? (
                          <span className="text-trade-up font-trade-sans">매수</span>
                        ) : (
                          <span className="text-trade-down font-trade-sans">매도</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right text-trade-muted font-trade-mono">
                        {t.qty.toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 text-right text-trade-muted font-trade-mono">
                        {t.price.toLocaleString("ko-KR")}
                      </td>
                      <td className="px-4 py-3 text-right">
                        {t.pnl != null ? (
                          <span
                            className={`font-trade-mono ${
                              t.pnl > 0
                                ? "text-trade-up"
                                : t.pnl < 0
                                ? "text-trade-down"
                                : "text-trade-muted"
                            }`}
                          >
                            {t.pnl.toLocaleString("ko-KR", {
                              minimumFractionDigits: 0,
                              maximumFractionDigits: 0,
                            })}
                          </span>
                        ) : (
                          <span className="text-trade-muted font-trade-mono">-</span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-right text-trade-muted text-xs">
                        {hasRationale ? (isExpanded ? "▲" : "▼") : ""}
                      </td>
                    </tr>
                    {isExpanded && (
                      <tr
                        key={`rationale-${t.id}`}
                        className="bg-trade-elevated/40"
                      >
                        <td colSpan={7} className="px-6 py-3">
                          <TradeRationaleRow rationale={rationale} />
                        </td>
                      </tr>
                    )}
                  </>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

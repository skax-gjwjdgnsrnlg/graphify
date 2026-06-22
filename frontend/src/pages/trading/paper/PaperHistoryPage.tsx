import { useState, useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchPaperHistory } from "@/lib/paperApi";
import type { PaperTradeHistoryItem } from "@/types/paper";
import { TradeRationaleRow, parseRationale } from "@/components/trading/TradeRationaleRow";
import { CandleSection } from "@/components/backtest/CandleSection";
import type { BacktestTrade } from "@/types/trading";

export function PaperHistoryPage() {
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [selected, setSelected] = useState<{ symbol: string; date: string; time: number } | null>(null);

  const { data, isLoading, isError } = useQuery<PaperTradeHistoryItem[]>({
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
        time: Math.floor(new Date(t.tradedAt).getTime() / 1000),
      });
    }
    // Only run when data changes (not selected — intentional single-trigger on load)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data]);

  if (isLoading) {
    return (
      <div>
        <h2 className="text-xl font-semibold text-white">모의 거래 이력</h2>
        <p className="mt-2 text-sm text-gray-400">불러오는 중...</p>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div>
        <h2 className="text-xl font-semibold text-white">모의 거래 이력</h2>
        <p className="mt-2 text-sm text-red-400">거래 이력을 불러오지 못했습니다.</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h2 className="text-xl font-semibold text-white">모의 거래 이력</h2>

      {data.length === 0 ? (
        <div className="rounded-lg border border-white/10 bg-gray-900/50 p-8 text-center">
          <p className="text-sm text-gray-400">체결된 모의 거래가 없습니다.</p>
        </div>
      ) : (
        <>
          {/* Map PaperTradeHistoryItem[] → BacktestTrade[] shape for CandleSection markers */}
          {(() => {
            const candleTrades: BacktestTrade[] = data.map((t) => ({
              datetime: t.tradedAt,
              symbol: t.symbol,
              companyName: null,
              side: t.side,
              qty: t.qty,
              price: t.price,
              pnl: t.pnl,
              rationaleJson: t.rationaleJson ?? null,
            }));
            return (
              <CandleSection
                symbol={selected?.symbol ?? null}
                date={selected?.date ?? null}
                trades={candleTrades}
                indicators={[]}
                highlightTime={selected?.time}
              />
            );
          })()}

          <p className="text-xs text-gray-500">행을 클릭하면 매매 근거를 확인할 수 있습니다.</p>
          <div className="rounded-lg border border-white/10 bg-gray-900/50 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-white/10 text-gray-400 text-left">
                  <th className="px-4 py-3 font-medium">체결시각</th>
                  <th className="px-4 py-3 font-medium">종목</th>
                  <th className="px-4 py-3 font-medium">구분</th>
                  <th className="px-4 py-3 font-medium text-right">수량</th>
                  <th className="px-4 py-3 font-medium text-right">가격</th>
                  <th className="px-4 py-3 font-medium text-right">손익</th>
                  <th className="px-4 py-3 font-medium text-right w-8"></th>
                </tr>
              </thead>
              <tbody>
                {data.map((t) => {
                  const isExpanded = expandedId === t.id;
                  const rationale = parseRationale(t.rationaleJson ?? null);
                  const hasRationale = rationale !== null;
                  const handleRowClick = () => {
                    // Always load chart for this trade (RESEARCH Pattern 7)
                    setSelected({
                      symbol: t.symbol,
                      date: t.tradedAt.slice(0, 10),
                      time: Math.floor(new Date(t.tradedAt).getTime() / 1000),
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
                        className="cursor-pointer border-b border-white/5 hover:bg-white/5"
                      >
                        <td className="px-4 py-3 text-gray-300">
                          {new Date(t.tradedAt).toLocaleString("ko-KR")}
                        </td>
                        <td className="px-4 py-3 text-white font-medium">{t.symbol}</td>
                        <td className="px-4 py-3">
                          {t.side === "BUY" ? (
                            <span className="text-emerald-400">매수</span>
                          ) : (
                            <span className="text-red-400">매도</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right text-gray-300">
                          {t.qty.toLocaleString("ko-KR")}
                        </td>
                        <td className="px-4 py-3 text-right text-gray-300">
                          {t.price.toLocaleString("ko-KR")}
                        </td>
                        <td className="px-4 py-3 text-right">
                          {t.pnl != null ? (
                            <span className={t.pnl >= 0 ? "text-emerald-400" : "text-red-400"}>
                              {t.pnl.toLocaleString("ko-KR", {
                                minimumFractionDigits: 0,
                                maximumFractionDigits: 0,
                              })}
                            </span>
                          ) : (
                            <span className="text-gray-500">-</span>
                          )}
                        </td>
                        <td className="px-4 py-3 text-right text-gray-400 text-xs">
                          {hasRationale ? (isExpanded ? "▲" : "▼") : ""}
                        </td>
                      </tr>
                      {isExpanded && (
                        <tr key={`rationale-${t.id}`} className="bg-gray-800/40">
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
        </>
      )}
    </div>
  );
}

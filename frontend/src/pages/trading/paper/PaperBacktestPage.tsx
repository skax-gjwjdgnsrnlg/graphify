import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { fetchPaperRules, runBacktest } from "@/lib/ruleApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { BacktestResult } from "@/types/trading";
import { EquityCurveChart } from "@/components/backtest/EquityCurveChart";
import { TradeRationaleRow, parseRationale } from "@/components/trading/TradeRationaleRow";

const fmtMoney = (n: number) =>
  n.toLocaleString("ko-KR", { maximumFractionDigits: 0 }) + "원";
const fmtPct = (n: number) => `${n >= 0 ? "+" : ""}${n.toFixed(2)}%`;
const fmtStat = (n: number) =>
  Number.isFinite(n) ? n.toFixed(2) : "—";

export function PaperBacktestPage() {
  const [ruleId, setRuleId] = useState<number | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [initialCash, setInitialCash] = useState("10000000");
  const [timeFrom, setTimeFrom] = useState("09:00");
  const [timeTo, setTimeTo] = useState("12:00");
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<BacktestResult | null>(null);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  const { data: rules } = useQuery({
    queryKey: ["trading", "paper", "rules"],
    queryFn: async () => (await fetchPaperRules()).data ?? [],
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (ruleId === "") {
        throw new ApiRequestError("ERR_FORM", "룰을 선택하세요.");
      }
      return runBacktest({
        ruleId: ruleId as number,
        from: from || undefined,
        to: to || undefined,
        initialCash: initialCash ? Number(initialCash) : undefined,
        timeFrom,
        timeTo,
      });
    },
    onSuccess: (res) => {
      setResult(res.data ?? null);
      setExpandedId(null);
      setError(null);
    },
    onError: (err) => {
      setResult(null);
      setError(
        err instanceof ApiRequestError ? err.message : "백테스트에 실패했습니다."
      );
    },
  });

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-white">백테스트</h2>
        <p className="mt-1 text-sm text-gray-400">
          저장된 모의 룰을 5분봉 인트라데이 데이터(09:00–12:00 KST, 최대 60일)로 검증합니다.
        </p>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-6">
        <div className="lg:col-span-1">
          <label className="mb-1 block text-xs font-medium text-gray-300">룰</label>
          <select
            value={ruleId}
            onChange={(e) =>
              setRuleId(e.target.value === "" ? "" : Number(e.target.value))
            }
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          >
            <option value="">룰 선택…</option>
            {(rules ?? []).map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-300">시작일</label>
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-300">종료일</label>
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-300">시작 시각</label>
          <input
            type="time"
            value={timeFrom}
            onChange={(e) => setTimeFrom(e.target.value)}
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-300">종료 시각</label>
          <input
            type="time"
            value={timeTo}
            onChange={(e) => setTimeTo(e.target.value)}
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>
        <div>
          <label className="mb-1 block text-xs font-medium text-gray-300">초기 자본</label>
          <input
            type="number"
            value={initialCash}
            onChange={(e) => setInitialCash(e.target.value)}
            className="w-full rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>
      </div>

      <div className="mt-4 flex items-center gap-3">
        <button
          type="button"
          disabled={mutation.isPending || ruleId === ""}
          onClick={() => mutation.mutate()}
          className="rounded-md bg-emerald-600 px-4 py-2 text-sm text-white transition-opacity hover:opacity-90 disabled:opacity-50"
        >
          {mutation.isPending ? "실행 중..." : "백테스트 실행"}
        </button>
        {error ? <span className="text-xs text-red-400">{error}</span> : null}
      </div>

      {result ? (
        <div className="mt-6 space-y-6">
          {/* Summary metrics — 5 cards */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <Metric label="최종 평가액" value={fmtMoney(result.finalEquity)} />
            <Metric
              label="수익률"
              value={fmtPct(result.returnPct)}
              positive={result.returnPct >= 0}
            />
            <Metric label="최대 낙폭(MDD)" value={`-${result.maxDrawdownPct.toFixed(2)}%`} />
            <Metric label="승률" value={`${result.winRate.toFixed(1)}%`} />
            <Metric label="거래 횟수" value={`${result.tradeCount}회`} />
          </div>

          {/* Equity curve chart with drawdown overlays */}
          <div className="rounded-lg border border-white/10 bg-gray-900/50 p-4">
            <h3 className="mb-4 text-sm font-medium text-gray-300">수익 곡선</h3>
            <EquityCurveChart
              data={result.equityCurve}
              drawdownSegments={result.drawdownSegments ?? []}
              initialCash={result.initialCash}
            />
          </div>

          {/* Advanced statistics — 3 cards */}
          <div>
            <h3 className="mb-3 text-sm font-medium text-gray-300">고급 통계</h3>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <StatCard
                label="Sharpe Ratio"
                value={fmtStat(result.sharpeRatio)}
                description="연환산 샤프 비율 (무위험수익률 0%, √9000 연환산)"
              />
              <StatCard
                label="Sortino Ratio"
                value={fmtStat(result.sortinoRatio)}
                description="하방 변동성만 반영한 위험 조정 수익률"
              />
              <StatCard
                label="Profit Factor"
                value={fmtStat(result.profitFactor)}
                description="총 이익 / 총 손실 (1.0 초과 시 수익 우위)"
              />
            </div>
          </div>

          {/* Trade history table with rationale accordion */}
          <div>
            <p className="mb-2 text-xs text-gray-500">행을 클릭하면 매매 근거를 확인할 수 있습니다.</p>
            <div className="overflow-hidden rounded-lg border border-white/10 bg-gray-900/50">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-white/10 text-left text-gray-400">
                    <th className="px-4 py-3 font-medium">일시</th>
                    <th className="px-4 py-3 font-medium">종목</th>
                    <th className="px-4 py-3 font-medium">구분</th>
                    <th className="px-4 py-3 text-right font-medium">수량</th>
                    <th className="px-4 py-3 text-right font-medium">가격</th>
                    <th className="px-4 py-3 text-right font-medium">손익</th>
                    <th className="px-4 py-3 text-right font-medium w-8"></th>
                  </tr>
                </thead>
                <tbody>
                  {result.trades.length === 0 ? (
                    <tr>
                      <td colSpan={7} className="px-4 py-6 text-center text-gray-400">
                        체결된 거래가 없습니다.
                      </td>
                    </tr>
                  ) : (
                    result.trades.map((t, i) => {
                      const isExpanded = expandedId === i;
                      const rationale = parseRationale(t.rationaleJson ?? null);
                      const hasRationale = rationale !== null;
                      return (
                        <>
                          <tr
                            key={`row-${i}`}
                            onClick={() =>
                              hasRationale
                                ? setExpandedId(isExpanded ? null : i)
                                : undefined
                            }
                            className={`border-b border-white/5 last:border-0 ${
                              hasRationale ? "cursor-pointer hover:bg-white/5" : ""
                            }`}
                          >
                            <td className="px-4 py-2 text-gray-300">{t.datetime}</td>
                            <td className="px-4 py-2 text-white">
                              {t.companyName ? `${t.companyName} (${t.symbol})` : t.symbol}
                            </td>
                            <td className="px-4 py-2">
                              <span
                                className={
                                  t.side === "BUY" ? "text-emerald-400" : "text-red-400"
                                }
                              >
                                {t.side === "BUY" ? "매수" : "매도"}
                              </span>
                            </td>
                            <td className="px-4 py-2 text-right text-gray-300">{t.qty}</td>
                            <td className="px-4 py-2 text-right text-gray-300">
                              {t.price.toLocaleString("ko-KR")}
                            </td>
                            <td
                              className={`px-4 py-2 text-right ${
                                t.pnl == null
                                  ? "text-gray-500"
                                  : t.pnl >= 0
                                  ? "text-emerald-400"
                                  : "text-red-400"
                              }`}
                            >
                              {t.pnl == null ? "—" : fmtMoney(t.pnl)}
                            </td>
                            <td className="px-4 py-2 text-right text-gray-400 text-xs">
                              {hasRationale ? (isExpanded ? "▲" : "▼") : ""}
                            </td>
                          </tr>
                          {isExpanded && (
                            <tr key={`rationale-${i}`} className="bg-gray-800/40">
                              <td colSpan={7} className="px-6 py-3">
                                <TradeRationaleRow rationale={rationale} />
                              </td>
                            </tr>
                          )}
                        </>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function Metric({
  label,
  value,
  positive,
}: {
  label: string;
  value: string;
  positive?: boolean;
}) {
  return (
    <div className="rounded-lg border border-white/10 bg-gray-900/50 p-4">
      <p className="text-xs text-gray-400">{label}</p>
      <p
        className={`mt-1 text-lg font-semibold ${
          positive === undefined
            ? "text-white"
            : positive
            ? "text-emerald-400"
            : "text-red-400"
        }`}
      >
        {value}
      </p>
    </div>
  );
}

function StatCard({
  label,
  value,
  description,
}: {
  label: string;
  value: string;
  description: string;
}) {
  return (
    <div className="rounded-lg border border-white/10 bg-gray-800 p-4">
      <p className="text-sm text-gray-400">{label}</p>
      <p className="mt-1 text-2xl font-bold text-white">{value}</p>
      <p className="mt-1 text-xs text-gray-500">{description}</p>
    </div>
  );
}

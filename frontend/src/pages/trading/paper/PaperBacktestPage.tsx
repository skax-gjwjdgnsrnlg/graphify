import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { fetchPaperRules, runBacktest } from "@/lib/ruleApi";
import { ApiRequestError } from "@/lib/apiClient";
import type { BacktestResult, BacktestTrade } from "@/types/trading";
import { EquityCurveChart } from "@/components/backtest/EquityCurveChart";
import { CandleSection } from "@/components/backtest/CandleSection";
import { extractIndicators, fmtTradeKst, toEpochSec } from "@/components/backtest/candleIndicators";
import { TradeRationaleRow, parseRationale } from "@/components/trading/TradeRationaleRow";
import { TradingCompanyPickerModal } from "@/components/trading/TradingCompanyPickerModal";
import {
  TradeButton,
  TradeCard,
  TradeInput,
  TradeStatCard,
} from "@/components/trading/ui";

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
  const [selected, setSelected] = useState<{
    symbol: string;
    date: string;
    time: number;
    side: "BUY" | "SELL";
  } | null>(null);
  // v1.6.0: 빈 유니버스 폴백 — 종목 직접 선택 모달
  const [pickerOpen, setPickerOpen] = useState(false);

  const { data: rules } = useQuery({
    queryKey: ["trading", "paper", "rules"],
    queryFn: async () => (await fetchPaperRules()).data ?? [],
  });

  const mutation = useMutation({
    mutationFn: async (overrideSymbols?: string[]) => {
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
        overrideSymbols:
          overrideSymbols && overrideSymbols.length > 0 ? overrideSymbols : undefined,
      });
    },
    onSuccess: (res) => {
      const nextResult = res.data ?? null;
      setResult(nextResult);
      setExpandedId(null);
      setError(null);
      // Auto-select first trade (locked §1)
      if (nextResult && nextResult.trades.length > 0) {
        const t: BacktestTrade = nextResult.trades[0]!;
        setSelected({
          symbol: t.symbol,
          date: t.datetime.slice(0, 10),
          time: toEpochSec(t.datetime),
          side: t.side,
        });
      } else {
        setSelected(null);
      }
    },
    onError: (err) => {
      setResult(null);
      // 빈 거래대금 유니버스 → 종목 직접 선택 모달로 폴백 (v1.6.0)
      if (
        err instanceof ApiRequestError &&
        err.code === "ERR_BACKTEST_UNIVERSE_EMPTY"
      ) {
        setError(err.message);
        setPickerOpen(true);
        return;
      }
      setError(
        err instanceof ApiRequestError
          ? err.message
          : "백테스트에 실패했습니다."
      );
    },
  });

  return (
    <div>
      {/* Page header */}
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-trade-on-dark">백테스트</h2>
        <p className="mt-1 text-sm text-trade-muted">
          5분봉 인트라데이 (09:00–12:00 KST · 최대 60일) 검증
        </p>
      </div>

      {/* Input card */}
      <div className="rounded-lg border border-trade-hairline bg-trade-surface p-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div>
            <label className="mb-1 block text-xs font-medium text-trade-muted">
              룰
            </label>
            <select
              value={ruleId}
              onChange={(e) =>
                setRuleId(e.target.value === "" ? "" : Number(e.target.value))
              }
              className="h-10 w-full rounded-md border border-trade-hairline bg-trade-surface px-3 text-trade-body font-trade-sans transition-colors focus:outline-none focus:ring-2 focus:ring-trade-info"
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
            <label className="mb-1 block text-xs font-medium text-trade-muted">
              시작일
            </label>
            <TradeInput
              type="date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-trade-muted">
              종료일
            </label>
            <TradeInput
              type="date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-trade-muted">
              시간대
            </label>
            <div className="flex items-center gap-2">
              <TradeInput
                type="time"
                value={timeFrom}
                onChange={(e) => setTimeFrom(e.target.value)}
              />
              <span className="shrink-0 text-sm text-trade-muted">–</span>
              <TradeInput
                type="time"
                value={timeTo}
                onChange={(e) => setTimeTo(e.target.value)}
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-trade-muted">
              초기 자본
            </label>
            <TradeInput
              mono
              type="number"
              value={initialCash}
              onChange={(e) => setInitialCash(e.target.value)}
            />
          </div>
          <div className="flex items-end">
            <TradeButton
              variant="primary"
              disabled={mutation.isPending || ruleId === ""}
              onClick={() => mutation.mutate(undefined)}
              className="w-full"
            >
              {mutation.isPending ? "실행 중..." : "백테스트 실행"}
            </TradeButton>
          </div>
        </div>
        {error ? (
          <p className="mt-3 text-xs text-trade-down">{error}</p>
        ) : null}
      </div>

      {mutation.isPending ? (
        /* 실행 중 로딩 화면 — trade 토큰 + 기존 border-spinner 컨벤션 */
        <div
          className="mt-6 flex min-h-[280px] flex-col items-center justify-center rounded-lg border border-trade-hairline bg-trade-surface px-6 py-12 text-center"
          role="status"
          aria-live="polite"
        >
          <span className="inline-block h-10 w-10 animate-spin rounded-full border-[3px] border-trade-hairline border-t-trade-primary" />
          <p className="mt-4 text-sm font-medium text-trade-on-dark font-trade-sans">
            백테스트 실행 중…
          </p>
          <p className="mt-1 text-xs text-trade-muted font-trade-sans">
            5분봉 데이터를 불러와 매매를 시뮬레이션하고 있습니다. 잠시만 기다려 주세요.
          </p>
        </div>
      ) : result ? (
        <div className="mt-6 space-y-4">
          {/* Summary metrics — 5 cards */}
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            <TradeStatCard
              label="최종 평가액"
              value={fmtMoney(result.finalEquity)}
            />
            <TradeStatCard
              label="수익률"
              value={fmtPct(result.returnPct)}
              valueColor={result.returnPct >= 0 ? "up" : "down"}
            />
            <TradeStatCard
              label="MDD"
              value={`-${result.maxDrawdownPct.toFixed(2)}%`}
              valueColor="down"
            />
            <TradeStatCard
              label="승률"
              value={`${result.winRate.toFixed(1)}%`}
            />
            <TradeStatCard
              label="청산 횟수"
              value={`${result.tradeCount}회`}
            />
          </div>

          {/* Equity curve chart with drawdown overlays */}
          <TradeCard>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm font-semibold text-trade-on-dark font-trade-sans">
                수익 곡선
              </span>
              <span className="text-xs text-trade-muted">
                드로우다운 음영 오버레이
              </span>
            </div>
            <EquityCurveChart
              data={result.equityCurve}
              drawdownSegments={result.drawdownSegments ?? []}
              initialCash={result.initialCash}
            />
          </TradeCard>

          {/* Candle chart section — reused D2, wrapped in TradeCard (SC-4) */}
          {(() => {
            const ruleDef = (rules ?? []).find((r) => r.id === ruleId)
              ?.definition;
            const indicators = ruleDef ? extractIndicators(ruleDef) : [];
            return (
              <TradeCard>
                <CandleSection
                  symbol={selected?.symbol ?? null}
                  date={selected?.date ?? null}
                  trades={result.trades}
                  indicators={indicators}
                  highlightTime={selected?.time}
                  highlightSide={selected?.side}
                />
              </TradeCard>
            );
          })()}

          {/* Advanced statistics — 3 cards */}
          <div>
            <p className="mb-3 text-sm font-semibold text-trade-on-dark font-trade-sans">
              고급 통계
            </p>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
              <TradeStatCard
                label="Sharpe Ratio"
                value={fmtStat(result.sharpeRatio)}
                sub="연환산 샤프 비율 (무위험수익률 0%, √9000 연환산)"
              />
              <TradeStatCard
                label="Sortino Ratio"
                value={fmtStat(result.sortinoRatio)}
                sub="하방 변동성만 반영한 위험 조정 수익률"
              />
              <TradeStatCard
                label="Profit Factor"
                value={fmtStat(result.profitFactor)}
                sub="총 이익 / 총 손실 (1.0 초과 시 수익 우위)"
              />
            </div>
          </div>

          {/* Trade history table with rationale accordion — Task 2 theming */}
          <div>
            <p className="mb-2 text-xs text-trade-muted">
              행을 클릭하면 매매 근거를 확인할 수 있습니다.
            </p>
            <div className="overflow-hidden rounded-lg border border-trade-hairline bg-trade-surface">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-trade-hairline text-left text-trade-muted">
                      <th className="px-4 py-3 font-medium font-trade-sans">
                        일시 (KST)
                      </th>
                      <th className="px-4 py-3 font-medium font-trade-sans">
                        종목
                      </th>
                      <th className="px-4 py-3 font-medium font-trade-sans">
                        구분
                      </th>
                      <th className="px-4 py-3 text-right font-medium font-trade-sans">
                        수량
                      </th>
                      <th className="px-4 py-3 text-right font-medium font-trade-sans">
                        가격
                      </th>
                      <th className="px-4 py-3 text-right font-medium font-trade-sans">
                        손익
                      </th>
                      <th className="w-8 px-4 py-3 text-right font-medium font-trade-sans"></th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.trades.length === 0 ? (
                      <tr>
                        <td
                          colSpan={7}
                          className="px-4 py-6 text-center text-trade-muted"
                        >
                          체결된 거래가 없습니다.
                        </td>
                      </tr>
                    ) : (
                      result.trades.map((t, i) => {
                        const isExpanded = expandedId === i;
                        const rationale = parseRationale(
                          t.rationaleJson ?? null
                        );
                        const hasRationale = rationale !== null;
                        const handleRowClick = () => {
                          // Always load chart for this trade (locked §1 + RESEARCH Pattern 7)
                          setSelected({
                            symbol: t.symbol,
                            date: t.datetime.slice(0, 10),
                            time: toEpochSec(t.datetime),
                            side: t.side,
                          });
                          // Toggle accordion only when rationale is available
                          if (hasRationale) {
                            setExpandedId(isExpanded ? null : i);
                          }
                        };
                        return (
                          <>
                            <tr
                              key={`row-${i}`}
                              onClick={handleRowClick}
                              className="cursor-pointer border-b border-trade-hairline/50 last:border-0 hover:bg-trade-elevated transition-colors"
                            >
                              <td className="px-4 py-2 text-trade-muted font-trade-mono whitespace-nowrap">
                                {fmtTradeKst(t.datetime)}
                              </td>
                              <td className="px-4 py-2 text-trade-on-dark">
                                {t.companyName
                                  ? `${t.companyName} (${t.symbol})`
                                  : t.symbol}
                              </td>
                              <td className="px-4 py-2">
                                <span
                                  className={
                                    t.side === "BUY"
                                      ? "text-trade-up"
                                      : "text-trade-down"
                                  }
                                >
                                  {t.side === "BUY" ? "매수" : "매도"}
                                </span>
                              </td>
                              <td className="px-4 py-2 text-right text-trade-muted font-trade-mono">
                                {t.qty}
                              </td>
                              <td className="px-4 py-2 text-right text-trade-muted font-trade-mono">
                                {t.price.toLocaleString("ko-KR")}
                              </td>
                              <td
                                className={`px-4 py-2 text-right font-trade-mono ${
                                  t.pnl == null
                                    ? "text-trade-muted"
                                    : t.pnl >= 0
                                    ? "text-trade-up"
                                    : "text-trade-down"
                                }`}
                              >
                                {t.pnl == null ? "—" : fmtMoney(t.pnl)}
                              </td>
                              <td className="px-4 py-2 text-right text-trade-muted text-xs">
                                {hasRationale ? (isExpanded ? "▲" : "▼") : ""}
                              </td>
                            </tr>
                            {isExpanded && (
                              <tr
                                key={`rationale-${i}`}
                                className="bg-trade-elevated/40"
                              >
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
        </div>
      ) : null}

      <TradingCompanyPickerModal
        open={pickerOpen}
        title="백테스트 종목 직접 선택"
        description="해당 기간 거래대금 데이터가 없습니다. 종목을 직접 선택하세요."
        confirmLabel="이 종목으로 재실행"
        onClose={() => setPickerOpen(false)}
        onConfirm={(symbols) => {
          setPickerOpen(false);
          mutation.mutate(symbols);
        }}
      />
    </div>
  );
}

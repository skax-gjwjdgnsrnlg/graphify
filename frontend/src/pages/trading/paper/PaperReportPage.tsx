import { useQuery } from "@tanstack/react-query";
import { fetchPaperReport } from "@/lib/paperApi";
import { EquityCurveChart } from "@/components/backtest/EquityCurveChart";
import type { ReportData } from "@/types/paper";
import { TradeCard, TradePageState, TradeStatCard } from "@/components/trading/ui";

function fmtKst(iso: string | null): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleDateString("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

function fmtPct(v: number, sign = false): string {
  const s = v.toFixed(2) + "%";
  return sign && v > 0 ? "+" + s : s;
}

function fmtNum(v: number, decimals = 2): string {
  return v.toLocaleString("ko-KR", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function PaperReportPage() {
  const { data, isLoading, isError } = useQuery<ReportData>({
    queryKey: ["trading", "paper", "report"],
    queryFn: async () => {
      const res = await fetchPaperReport();
      return res.data as ReportData;
    },
  });

  if (isLoading) {
    return (
      <div className="space-y-6">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 성과 리포트
        </h2>
        <TradePageState variant="loading" className="h-[300px]" />
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div className="space-y-6">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 성과 리포트
        </h2>
        <TradePageState
          variant="error"
          message="데이터를 불러오지 못했습니다."
        />
      </div>
    );
  }

  const hasData = data.equityCurve.length > 0;
  const initialCash =
    hasData ? (data.equityCurve[0]?.equity ?? 10_000_000) : 10_000_000;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 성과 리포트
        </h2>
        {hasData && data.periodFrom && data.periodTo ? (
          <p className="mt-1 text-sm text-trade-muted font-trade-mono">
            {fmtKst(data.periodFrom)} ~ {fmtKst(data.periodTo)}
          </p>
        ) : (
          <p className="mt-1 text-sm text-trade-muted font-trade-sans">최근 30일 기준</p>
        )}
      </div>

      {!hasData ? (
        <TradePageState
          variant="empty"
          message="모의 실행 데이터가 없습니다. PAPER_LIVE 룰을 활성화하면 자동으로 기록됩니다."
        />
      ) : (
        <>
          {/* Equity curve — reused D2 chart on trade surface */}
          <TradeCard title="수익 곡선">
            <EquityCurveChart
              data={data.equityCurve}
              drawdownSegments={[]}
              initialCash={initialCash}
            />
          </TradeCard>

          {/* 6 metric stat cards */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
            <TradeStatCard
              label="총 수익률"
              value={fmtPct(data.totalReturn, true)}
              valueColor={data.totalReturn >= 0 ? "up" : "down"}
            />
            <TradeStatCard
              label="최대 낙폭 (MDD)"
              value={fmtPct(data.maxDrawdownPct)}
              valueColor="down"
            />
            <TradeStatCard
              label="승률"
              value={fmtPct(data.winRate)}
            />
            <TradeStatCard
              label="총 거래"
              value={`${data.totalTrades}건`}
            />
            <TradeStatCard
              label="Sharpe Ratio"
              value={fmtNum(data.sharpeRatio)}
              valueColor={data.sharpeRatio >= 1 ? "up" : "neutral"}
            />
            <TradeStatCard
              label="Sortino Ratio"
              value={fmtNum(data.sortinoRatio)}
              valueColor={data.sortinoRatio >= 1 ? "up" : "neutral"}
            />
          </div>
        </>
      )}
    </div>
  );
}

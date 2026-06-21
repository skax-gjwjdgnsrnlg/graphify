import { useQuery } from "@tanstack/react-query";
import { fetchPaperReport } from "@/lib/paperApi";
import { EquityCurveChart } from "@/components/backtest/EquityCurveChart";
import type { ReportData } from "@/types/paper";

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

interface StatCardProps {
  label: string;
  value: string;
  valueClass?: string;
}

function StatCard({ label, value, valueClass = "text-white" }: StatCardProps) {
  return (
    <div className="rounded-lg border border-white/10 bg-gray-900/50 p-4">
      <p className="text-xs text-gray-400">{label}</p>
      <p className={`mt-2 text-xl font-bold ${valueClass}`}>{value}</p>
    </div>
  );
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
      <div>
        <h2 className="text-xl font-semibold text-white">모의 성과 리포트</h2>
        <p className="mt-2 text-sm text-gray-400">불러오는 중...</p>
      </div>
    );
  }

  if (isError || !data) {
    return (
      <div>
        <h2 className="text-xl font-semibold text-white">모의 성과 리포트</h2>
        <p className="mt-2 text-sm text-red-400">데이터를 불러오지 못했습니다.</p>
      </div>
    );
  }

  const hasData = data.equityCurve.length > 0;
  const initialCash =
    hasData ? (data.equityCurve[0]?.equity ?? 10_000_000) : 10_000_000;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-white">모의 성과 리포트</h2>
        {hasData && data.periodFrom && data.periodTo ? (
          <p className="mt-1 text-sm text-gray-400">
            {fmtKst(data.periodFrom)} ~ {fmtKst(data.periodTo)}
          </p>
        ) : (
          <p className="mt-1 text-sm text-gray-400">최근 30일 기준</p>
        )}
      </div>

      {!hasData ? (
        <div className="rounded-lg border border-white/10 bg-gray-900/50 p-8 text-center">
          <p className="text-sm text-gray-400">
            모의 실행 데이터가 없습니다. PAPER_LIVE 룰을 활성화하면 자동으로 기록됩니다.
          </p>
        </div>
      ) : (
        <>
          {/* Equity curve */}
          <div className="rounded-lg border border-white/10 bg-gray-900/50 p-4">
            <h3 className="mb-3 text-sm font-semibold text-white">수익 곡선</h3>
            <EquityCurveChart
              data={data.equityCurve}
              drawdownSegments={[]}
              initialCash={initialCash}
            />
          </div>

          {/* Stat cards */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
            <StatCard
              label="총 수익률"
              value={fmtPct(data.totalReturn, true)}
              valueClass={data.totalReturn >= 0 ? "text-emerald-400" : "text-red-400"}
            />
            <StatCard
              label="최대 낙폭 (MDD)"
              value={fmtPct(data.maxDrawdownPct)}
              valueClass="text-red-400"
            />
            <StatCard
              label="승률"
              value={fmtPct(data.winRate)}
            />
            <StatCard
              label="총 거래"
              value={`${data.totalTrades}건`}
            />
            <StatCard
              label="Sharpe Ratio"
              value={fmtNum(data.sharpeRatio)}
              valueClass={data.sharpeRatio >= 1 ? "text-emerald-400" : "text-white"}
            />
            <StatCard
              label="Sortino Ratio"
              value={fmtNum(data.sortinoRatio)}
              valueClass={data.sortinoRatio >= 1 ? "text-emerald-400" : "text-white"}
            />
          </div>
        </>
      )}
    </div>
  );
}

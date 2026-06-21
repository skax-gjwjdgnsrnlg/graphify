import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { fetchPaperDashboard } from "@/lib/paperApi";
import { fetchTossAccounts, type TossAccount } from "@/lib/tossApi";
import type { PaperPositionItem } from "@/types/paper";

const fmtMoney = (n: number) =>
  n.toLocaleString("ko-KR", { maximumFractionDigits: 0 }) + "원";

const fmtPct = (n: number) =>
  `${n >= 0 ? "+" : ""}${n.toFixed(2)}%`;

function PnlText({ value }: { value: number }) {
  const color =
    value > 0 ? "text-green-400" : value < 0 ? "text-red-400" : "text-gray-400";
  return <span className={color}>{fmtMoney(value)}</span>;
}

function PnlPctText({ value }: { value: number }) {
  const color =
    value > 0 ? "text-green-400" : value < 0 ? "text-red-400" : "text-gray-400";
  return <span className={color}>{fmtPct(value)}</span>;
}

function StatCard({
  label,
  value,
  sub,
}: {
  label: string;
  value: React.ReactNode;
  sub?: React.ReactNode;
}) {
  return (
    <div className="rounded-lg bg-gray-800 p-4">
      <p className="mb-1 text-xs text-gray-400">{label}</p>
      <p className="text-xl font-bold text-white">{value}</p>
      {sub && <p className="mt-1 text-xs text-gray-400">{sub}</p>}
    </div>
  );
}

function PositionsTable({ positions }: { positions: PaperPositionItem[] }) {
  if (positions.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-gray-500">
        보유 포지션 없음
      </p>
    );
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-700 text-left text-xs text-gray-400">
            <th className="pb-2 pr-4">종목</th>
            <th className="pb-2 pr-4 text-right">수량</th>
            <th className="pb-2 pr-4 text-right">평균단가</th>
            <th className="pb-2 pr-4 text-right">현재가</th>
            <th className="pb-2 pr-4 text-right">평가금액</th>
            <th className="pb-2 pr-4 text-right">평가손익</th>
            <th className="pb-2 text-right">손익률</th>
          </tr>
        </thead>
        <tbody>
          {positions.map((pos) => (
            <tr
              key={pos.symbol}
              className="border-b border-gray-700/50 last:border-0"
            >
              <td className="py-2 pr-4 font-medium text-white">{pos.symbol}</td>
              <td className="py-2 pr-4 text-right text-gray-300">
                {pos.qty.toLocaleString("ko-KR")}
              </td>
              <td className="py-2 pr-4 text-right text-gray-300">
                {fmtMoney(pos.avgPrice)}
              </td>
              <td className="py-2 pr-4 text-right text-gray-300">
                {fmtMoney(pos.markPrice)}
              </td>
              <td className="py-2 pr-4 text-right text-gray-300">
                {fmtMoney(pos.marketValue)}
              </td>
              <td className="py-2 pr-4 text-right">
                <PnlText value={pos.unrealizedPnl} />
              </td>
              <td className="py-2 text-right">
                <PnlPctText value={pos.unrealizedPnlPct} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TossBalanceSection() {
  const [open, setOpen] = useState(false);
  const { data: accounts, isLoading } = useQuery<TossAccount[]>({
    queryKey: ["toss", "accounts"],
    queryFn: async () => (await fetchTossAccounts()).data ?? [],
    retry: false,
  });

  const configured = accounts && accounts.length > 0;

  return (
    <div className="rounded-lg bg-gray-800">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium text-gray-300 hover:text-white"
      >
        <span>토스 실계좌 잔고</span>
        <span className="text-xs text-gray-500">{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <div className="border-t border-white/10 px-4 py-3">
          {isLoading ? (
            <p className="text-sm text-gray-500">불러오는 중...</p>
          ) : !configured ? (
            <p className="text-sm text-gray-500">
              토스증권 미연동 —{" "}
              <a href="/trading/settings" className="underline hover:text-white">
                토스 설정
              </a>
              에서 자격증명을 등록하세요.
            </p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-gray-400">
                  <th className="pb-2 pr-4">계좌번호</th>
                  <th className="pb-2 pr-4">계좌명</th>
                  <th className="pb-2 pr-4 text-right">잔고</th>
                  <th className="pb-2 text-right">출금가능</th>
                </tr>
              </thead>
              <tbody>
                {accounts.map((acc) => (
                  <tr key={acc.accountNumber} className="border-t border-white/5">
                    <td className="py-2 pr-4 font-mono text-gray-300">{acc.accountNumber}</td>
                    <td className="py-2 pr-4 text-gray-300">{acc.accountName}</td>
                    <td className="py-2 pr-4 text-right text-white">
                      {acc.balance.toLocaleString("ko-KR")}원
                    </td>
                    <td className="py-2 text-right text-gray-300">
                      {acc.availableBalance.toLocaleString("ko-KR")}원
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

export function PaperDashboardPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ["trading", "paper", "dashboard"],
    queryFn: async () => (await fetchPaperDashboard()).data ?? null,
    refetchInterval: 30_000,
  });

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-20 rounded-lg bg-gray-800" />
          ))}
        </div>
        <div className="h-40 rounded-lg bg-gray-800" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="rounded-lg bg-gray-800 p-6 text-center text-sm text-gray-400">
        대시보드 데이터를 불러올 수 없습니다.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-white">모의 대시보드</h2>
        <p className="mt-1 text-sm text-gray-400">30초마다 자동 갱신</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <StatCard
          label="총 평가금액"
          value={fmtMoney(data.totalEquity)}
          sub={
            <PnlPctText
              value={
                data.cash > 0
                  ? ((data.totalEquity - data.cash) / data.cash) * 100
                  : 0
              }
            />
          }
        />
        <StatCard label="가용 현금" value={fmtMoney(data.cash)} />
        <StatCard
          label="오늘 실현손익"
          value={<PnlText value={data.todayRealizedPnl} />}
        />
        <StatCard
          label="활성 PAPER_LIVE 룰"
          value={`${data.activePaperLiveRuleCount}개`}
        />
      </div>

      {/* Unrealized PnL summary */}
      {data.positions.length > 0 && (
        <div className="flex items-center gap-2 text-sm text-gray-400">
          <span>미실현 손익 합계:</span>
          <PnlText value={data.totalUnrealizedPnl} />
        </div>
      )}

      {/* Positions table */}
      <div className="rounded-lg bg-gray-800 p-4">
        <h3 className="mb-4 text-sm font-medium text-gray-300">보유 포지션</h3>
        <PositionsTable positions={data.positions} />
      </div>

      {/* Toss real account balance (collapsible) */}
      <TossBalanceSection />
    </div>
  );
}

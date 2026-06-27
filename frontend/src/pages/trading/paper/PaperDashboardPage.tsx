import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { fetchPaperDashboard } from "@/lib/paperApi";
import { fetchTossAccounts, type TossAccount } from "@/lib/tossApi";
import type { PaperPositionItem } from "@/types/paper";
import {
  TradeCard,
  TradePageState,
  TradeStatCard,
  TradeTable,
  TradeTableHeader,
  TradeTableRow,
} from "@/components/trading/ui";

const fmtMoney = (n: number) =>
  n.toLocaleString("ko-KR", { maximumFractionDigits: 0 }) + "원";

const fmtPct = (n: number) =>
  `${n >= 0 ? "+" : ""}${n.toFixed(2)}%`;

function pnlColor(value: number): "up" | "down" | "neutral" {
  return value > 0 ? "up" : value < 0 ? "down" : "neutral";
}

function PositionsTable({ positions }: { positions: PaperPositionItem[] }) {
  if (positions.length === 0) {
    return (
      <p className="py-6 text-center text-sm text-trade-muted font-trade-sans">
        보유 포지션 없음
      </p>
    );
  }
  return (
    <TradeTable>
      <TradeTableHeader className="grid grid-cols-4 text-xs">
        <span>종목</span>
        <span className="text-right">수량</span>
        <span className="text-right">평균단가</span>
        <span className="text-right">평가손익</span>
      </TradeTableHeader>
      {positions.map((pos) => (
        <TradeTableRow key={pos.symbol} className="grid grid-cols-4 items-center text-sm">
          <span className="text-trade-body font-medium font-trade-sans">
            {pos.companyName ? `${pos.companyName} (${pos.symbol})` : pos.symbol}
          </span>
          <span className="text-right text-trade-muted font-trade-mono">
            {pos.qty.toLocaleString("ko-KR")}
          </span>
          <span className="text-right text-trade-muted font-trade-mono">
            {fmtMoney(pos.avgPrice)}
          </span>
          <span
            className={`text-right font-trade-mono ${
              pos.unrealizedPnl > 0
                ? "text-trade-up"
                : pos.unrealizedPnl < 0
                ? "text-trade-down"
                : "text-trade-muted"
            }`}
          >
            {fmtMoney(pos.unrealizedPnl)}
          </span>
        </TradeTableRow>
      ))}
    </TradeTable>
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
    <div className="rounded-lg bg-trade-surface border border-trade-hairline">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium text-trade-muted-strong hover:text-trade-body font-trade-sans"
      >
        <span>토스 실계좌 잔고</span>
        <span className="text-xs text-trade-muted">{open ? "▲" : "▼"}</span>
      </button>
      {open && (
        <div className="border-t border-trade-hairline px-4 py-3">
          {isLoading ? (
            <p className="text-sm text-trade-muted font-trade-sans">불러오는 중...</p>
          ) : !configured ? (
            <p className="text-sm text-trade-muted font-trade-sans">
              토스증권 미연동 —{" "}
              <a href="/trading/settings" className="underline hover:text-trade-body">
                토스 설정
              </a>
              에서 자격증명을 등록하세요.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-xs text-trade-muted">
                    <th className="pb-2 pr-4 font-medium font-trade-sans">계좌번호</th>
                    <th className="pb-2 pr-4 font-medium font-trade-sans">계좌명</th>
                    <th className="pb-2 pr-4 text-right font-medium font-trade-sans">잔고</th>
                    <th className="pb-2 text-right font-medium font-trade-sans">출금가능</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((acc) => (
                    <tr key={acc.accountNumber} className="border-t border-trade-hairline">
                      <td className="py-2 pr-4 font-trade-mono text-trade-muted">
                        {acc.accountNumber}
                      </td>
                      <td className="py-2 pr-4 text-trade-body">{acc.accountName}</td>
                      <td className="py-2 pr-4 text-right text-trade-on-dark font-trade-mono">
                        {acc.balance.toLocaleString("ko-KR")}원
                      </td>
                      <td className="py-2 text-right text-trade-muted font-trade-mono">
                        {acc.availableBalance.toLocaleString("ko-KR")}원
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export function PaperDashboardPage() {
  const { data, isLoading, error, dataUpdatedAt } = useQuery({
    queryKey: ["trading", "paper", "dashboard"],
    queryFn: async () => (await fetchPaperDashboard()).data ?? null,
    refetchInterval: 30_000,
  });

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
      <div className="space-y-6">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 대시보드
        </h2>
        <TradePageState variant="loading" className="h-[300px]" />
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="space-y-6">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 대시보드
        </h2>
        <TradePageState
          variant="error"
          message="대시보드 데이터를 불러올 수 없습니다."
        />
      </div>
    );
  }

  const returnPct =
    data.cash > 0 ? ((data.totalEquity - data.cash) / data.cash) * 100 : 0;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-trade-on-dark font-trade-sans">
          모의 대시보드
        </h2>
        {lastUpdated && (
          <p className="text-xs font-trade-mono text-trade-muted">
            30초 자동 갱신 · {lastUpdated}
          </p>
        )}
      </div>

      {/* 4 stat cards */}
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
        <TradeStatCard
          label="총 평가금액"
          value={fmtMoney(data.totalEquity)}
          sub={fmtPct(returnPct)}
          valueColor={pnlColor(returnPct)}
        />
        <TradeStatCard label="가용 현금" value={fmtMoney(data.cash)} />
        <TradeStatCard
          label="오늘 실현손익"
          value={fmtMoney(data.todayRealizedPnl)}
          valueColor={pnlColor(data.todayRealizedPnl)}
        />
        <TradeStatCard
          label="활성 PAPER_LIVE 룰"
          value={`${data.activePaperLiveRuleCount}개`}
        />
      </div>

      {/* Unrealized PnL summary */}
      {data.positions.length > 0 && (
        <div className="flex items-center gap-2 text-sm text-trade-muted font-trade-sans">
          <span>미실현 손익 합계:</span>
          <span
            className={`font-trade-mono ${
              data.totalUnrealizedPnl > 0
                ? "text-trade-up"
                : data.totalUnrealizedPnl < 0
                ? "text-trade-down"
                : "text-trade-muted"
            }`}
          >
            {fmtMoney(data.totalUnrealizedPnl)}
          </span>
        </div>
      )}

      {/* Positions table */}
      <TradeCard title="보유 포지션">
        <PositionsTable positions={data.positions} />
      </TradeCard>

      {/* Toss real account balance (collapsible) */}
      <TossBalanceSection />
    </div>
  );
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchPaperRules } from "@/lib/ruleApi";
import { copyRule, pauseRule, promoteRule, resumeRule } from "@/lib/paperApi";
import type { RuleStatus, TradingRule } from "@/types/trading";

// ---- Status badge ----
const STATUS_CONFIG: Record<RuleStatus, { label: string; className: string }> = {
  DRAFT:      { label: "DRAFT",      className: "bg-gray-600 text-gray-200" },
  ACTIVE:     { label: "ACTIVE",     className: "bg-blue-700 text-blue-100" },
  BACKTESTED: { label: "BACKTESTED", className: "bg-blue-500 text-white" },
  PAPER_LIVE: { label: "PAPER_LIVE", className: "bg-emerald-600 text-white" },
  PAUSED:     { label: "PAUSED",     className: "bg-yellow-600 text-white" },
  LIVE:       { label: "LIVE",       className: "bg-purple-600 text-white" },
};

function StatusBadge({ status }: { status: RuleStatus }) {
  const cfg = STATUS_CONFIG[status] ?? STATUS_CONFIG.DRAFT;
  return (
    <span className={`rounded px-2 py-0.5 text-xs font-medium ${cfg.className}`}>
      {cfg.label}
    </span>
  );
}

function RuleActions({
  rule,
  onAction,
  pending,
}: {
  rule: TradingRule;
  onAction: (action: string, id: number) => void;
  pending: boolean;
}) {
  const { status, backtested, id } = rule;
  return (
    <div className="flex flex-wrap items-center justify-end gap-2">
      {(status === "DRAFT" || status === "BACKTESTED") && (
        <button
          type="button"
          disabled={!backtested || pending}
          title={!backtested ? "백테스트를 1회 이상 실행해야 합니다" : "PAPER_LIVE로 승격"}
          onClick={() => onAction("promote", id)}
          className="rounded bg-emerald-600 px-2 py-1 text-xs text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
        >
          PAPER_LIVE 승격
        </button>
      )}
      {status === "PAPER_LIVE" && (
        <button
          type="button"
          disabled={pending}
          onClick={() => onAction("pause", id)}
          className="rounded bg-yellow-600 px-2 py-1 text-xs text-white hover:opacity-90 disabled:opacity-40"
        >
          일시 정지
        </button>
      )}
      {status === "PAUSED" && (
        <button
          type="button"
          disabled={pending}
          onClick={() => onAction("resume", id)}
          className="rounded bg-emerald-600 px-2 py-1 text-xs text-white hover:opacity-90 disabled:opacity-40"
        >
          재개
        </button>
      )}
      {status === "LIVE" && (
        <span
          title="LIVE 룰은 편집할 수 없습니다. 복사본을 만들어 수정하세요."
          className="cursor-not-allowed text-xs text-gray-500 line-through"
        >
          편집 불가
        </span>
      )}
      <button
        type="button"
        disabled={pending}
        onClick={() => onAction("copy", id)}
        className="rounded border border-white/20 px-2 py-1 text-xs text-gray-300 hover:bg-white/5 disabled:opacity-40"
      >
        복사
      </button>
    </div>
  );
}

export function TradingRulesPage() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["trading", "paper", "rules"] });

  const { data: rules, isLoading, isError } = useQuery({
    queryKey: ["trading", "paper", "rules"],
    queryFn: async () => (await fetchPaperRules()).data ?? [],
  });

  const promoteMutation = useMutation({ mutationFn: promoteRule, onSuccess: invalidate });
  const pauseMutation   = useMutation({ mutationFn: pauseRule,   onSuccess: invalidate });
  const resumeMutation  = useMutation({ mutationFn: resumeRule,  onSuccess: invalidate });
  const copyMutation    = useMutation({ mutationFn: copyRule,    onSuccess: invalidate });

  const anyPending =
    promoteMutation.isPending || pauseMutation.isPending ||
    resumeMutation.isPending  || copyMutation.isPending;

  const handleAction = (action: string, id: number) => {
    switch (action) {
      case "promote": promoteMutation.mutate(id); break;
      case "pause":   pauseMutation.mutate(id);   break;
      case "resume":  resumeMutation.mutate(id);  break;
      case "copy":    copyMutation.mutate(id);    break;
    }
  };

  const mutationError =
    promoteMutation.error ?? pauseMutation.error ??
    resumeMutation.error  ?? copyMutation.error;

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-white">현재 룰</h2>
        <p className="mt-1 text-sm text-gray-400">
          활성화된 매매 룰과 생애주기 상태를 관리합니다.
        </p>
      </div>

      {mutationError ? (
        <div className="mb-4 rounded-md bg-red-900/40 px-4 py-2 text-sm text-red-300">
          {mutationError instanceof Error ? mutationError.message : "작업에 실패했습니다."}
        </div>
      ) : null}

      {isLoading ? (
        <p className="text-sm text-gray-400">불러오는 중...</p>
      ) : isError ? (
        <p className="text-sm text-red-400">룰 목록을 불러오지 못했습니다.</p>
      ) : (rules ?? []).length === 0 ? (
        <p className="rounded-lg border border-white/10 bg-gray-900/50 p-6 text-sm text-gray-400">
          등록된 룰이 없습니다. 모의 룰 설정 탭에서 룰을 추가하세요.
        </p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-white/10 bg-gray-900/50">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-gray-400">
                <th className="px-4 py-3 font-medium">이름</th>
                <th className="px-4 py-3 font-medium">상태</th>
                <th className="px-4 py-3 font-medium">백테스트</th>
                <th className="px-4 py-3 font-medium">수정일</th>
                <th className="px-4 py-3 text-right font-medium">액션</th>
              </tr>
            </thead>
            <tbody>
              {(rules ?? []).map((rule: TradingRule) => (
                <tr key={rule.id} className="border-b border-white/5 last:border-0">
                  <td className="px-4 py-3 font-medium text-white">{rule.name}</td>
                  <td className="px-4 py-3">
                    <StatusBadge status={rule.status} />
                  </td>
                  <td className="px-4 py-3">
                    {rule.backtested ? (
                      <span className="text-xs text-emerald-400">완료</span>
                    ) : (
                      <span className="text-xs text-gray-500">미실행</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-400">
                    {new Date(rule.updatedAt).toLocaleDateString("ko-KR")}
                  </td>
                  <td className="px-4 py-3">
                    <RuleActions
                      rule={rule}
                      onAction={handleAction}
                      pending={anyPending}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

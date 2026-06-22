// PaperRulesPage — 전략 설정: config축(DRAFT/ACTIVE)만 제어, run 제어 미노출 (06.5-05)
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { deletePaperRule, fetchPaperRules } from "@/lib/ruleApi";
import { activateRule, copyRule, deactivateRule } from "@/lib/paperApi";
import type { TradingRule } from "@/types/trading";

function ConfigBadge({ configStatus }: { configStatus: "DRAFT" | "ACTIVE" }) {
  if (configStatus === "ACTIVE") {
    return (
      <span className="rounded bg-emerald-700/60 px-2 py-0.5 text-xs font-medium text-emerald-300">
        ACTIVE
      </span>
    );
  }
  return (
    <span className="rounded bg-white/10 px-2 py-0.5 text-xs font-medium text-gray-400">
      DRAFT
    </span>
  );
}

export function PaperRulesPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const { data: rules, isLoading, isError } = useQuery({
    queryKey: ["trading", "paper", "rules"],
    queryFn: async () => (await fetchPaperRules()).data ?? [],
  });

  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["trading", "paper", "rules"] });

  const activateMutation = useMutation({
    mutationFn: (id: number) => activateRule(id),
    onSuccess: invalidate,
  });

  const deactivateMutation = useMutation({
    mutationFn: (id: number) => deactivateRule(id),
    onSuccess: invalidate,
  });

  const copyMutation = useMutation({
    mutationFn: (id: number) => copyRule(id),
    onSuccess: invalidate,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deletePaperRule(id),
    onSuccess: invalidate,
  });

  const anyPending =
    activateMutation.isPending ||
    deactivateMutation.isPending ||
    copyMutation.isPending ||
    deleteMutation.isPending;

  const mutationError =
    activateMutation.error ??
    deactivateMutation.error ??
    copyMutation.error ??
    deleteMutation.error;

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h2 className="text-xl font-semibold text-white">전략 설정</h2>
          <p className="mt-1 text-sm text-gray-400">
            매매 룰을 정의하고 ACTIVE로 전환합니다. 실행 제어는 "전략 운영" 화면에서 합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={() => navigate("/trading/paper/rules/new")}
          className="rounded-md bg-emerald-600 px-4 py-2 text-sm text-white transition-opacity hover:opacity-90"
        >
          + 새 룰
        </button>
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
          등록된 룰이 없습니다. "+ 새 룰"로 추가하세요.
        </p>
      ) : (
        <div className="overflow-hidden rounded-lg border border-white/10 bg-gray-900/50">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-white/10 text-left text-gray-400">
                <th className="px-4 py-3 font-medium">이름</th>
                <th className="px-4 py-3 font-medium">설정 상태</th>
                <th className="px-4 py-3 font-medium">쿨다운</th>
                <th className="px-4 py-3 font-medium">수정일</th>
                <th className="px-4 py-3 text-right font-medium">관리</th>
              </tr>
            </thead>
            <tbody>
              {(rules ?? []).map((rule: TradingRule) => {
                const configStatus = rule.configStatus ?? "DRAFT";
                const runStatus = rule.runStatus ?? "STOPPED";
                const isRunning = runStatus === "RUNNING";
                const isActive = configStatus === "ACTIVE";

                return (
                  <tr key={rule.id} className="border-b border-white/5 last:border-0">
                    <td className="px-4 py-3 text-white">{rule.name}</td>
                    <td className="px-4 py-3">
                      <ConfigBadge configStatus={configStatus} />
                    </td>
                    <td className="px-4 py-3 text-gray-400">
                      {rule.definition.constraints?.cooldownBars != null
                        ? `${rule.definition.constraints.cooldownBars}봉 (${rule.definition.constraints.cooldownBars * 5}m)`
                        : "—"}
                    </td>
                    <td className="px-4 py-3 text-gray-400">
                      {new Date(rule.updatedAt).toLocaleDateString("ko-KR")}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {/* ACTIVE 토글 버튼 — run 제어 없음 */}
                      {isActive ? (
                        <button
                          type="button"
                          onClick={() => deactivateMutation.mutate(rule.id)}
                          disabled={anyPending || isRunning}
                          title={
                            isRunning
                              ? "실행 중인 룰은 전략 운영에서 먼저 중지하세요"
                              : "DRAFT로 하향"
                          }
                          className="mr-2 rounded border border-yellow-600/50 px-2 py-1 text-xs text-yellow-400 hover:bg-yellow-900/30 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          하향
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => activateMutation.mutate(rule.id)}
                          disabled={anyPending}
                          title="ACTIVE로 전환"
                          className="mr-2 rounded border border-emerald-600/50 px-2 py-1 text-xs text-emerald-400 hover:bg-emerald-900/30 disabled:opacity-40"
                        >
                          활성화
                        </button>
                      )}
                      {/* 편집 버튼 — ACTIVE+RUNNING 시 비활성 */}
                      <button
                        type="button"
                        onClick={() => navigate(`/trading/paper/rules/edit/${rule.id}`)}
                        disabled={isActive && isRunning}
                        title={
                          isActive && isRunning
                            ? "실행 중에는 편집할 수 없습니다"
                            : "편집"
                        }
                        className="mr-2 text-emerald-400 hover:underline disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        편집
                      </button>
                      <button
                        type="button"
                        onClick={() => copyMutation.mutate(rule.id)}
                        disabled={anyPending}
                        className="mr-2 text-blue-400 hover:underline disabled:opacity-50"
                      >
                        복제
                      </button>
                      <button
                        type="button"
                        onClick={() => deleteMutation.mutate(rule.id)}
                        disabled={anyPending}
                        className="text-red-400 hover:underline disabled:opacity-50"
                      >
                        삭제
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

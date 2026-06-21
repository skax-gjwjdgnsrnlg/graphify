import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import {
  fetchTossStatus,
  refreshTossToken,
  saveTossCredentials,
  type TossCredentialStatus,
} from "@/lib/tossApi";

function fmtKst(iso: string | null): string {
  if (!iso) return "-";
  return new Date(iso).toLocaleString("ko-KR", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function StatusBadge({ status }: { status: TossCredentialStatus | undefined }) {
  if (!status) return null;
  if (!status.configured) {
    return (
      <span className="rounded bg-gray-700 px-2 py-1 text-xs text-gray-300">미설정</span>
    );
  }
  if (status.tokenValid) {
    return (
      <span className="rounded bg-emerald-700 px-2 py-1 text-xs text-white">
        설정됨 · 토큰 유효
      </span>
    );
  }
  return (
    <span className="rounded bg-yellow-700 px-2 py-1 text-xs text-white">
      설정됨 · 토큰 만료
    </span>
  );
}

export function TossSettingsPage() {
  const queryClient = useQueryClient();
  const invalidate = () =>
    void queryClient.invalidateQueries({ queryKey: ["toss", "status"] });

  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(
    null
  );

  const { data: status, isLoading } = useQuery({
    queryKey: ["toss", "status"],
    queryFn: async () => (await fetchTossStatus()).data ?? undefined,
  });

  const saveMutation = useMutation({
    mutationFn: () => saveTossCredentials(clientId, clientSecret),
    onSuccess: () => {
      setMessage({ type: "success", text: "자격증명이 저장되었습니다." });
      setClientId("");
      setClientSecret("");
      invalidate();
    },
    onError: (err: Error) => {
      setMessage({ type: "error", text: err.message ?? "저장에 실패했습니다." });
    },
  });

  const refreshMutation = useMutation({
    mutationFn: refreshTossToken,
    onSuccess: () => {
      setMessage({ type: "success", text: "토큰이 갱신되었습니다." });
      invalidate();
    },
    onError: (err: Error) => {
      setMessage({ type: "error", text: err.message ?? "토큰 갱신에 실패했습니다." });
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    saveMutation.mutate();
  };

  return (
    <div className="max-w-lg space-y-6">
      <div>
        <h2 className="text-xl font-semibold text-white">토스 설정</h2>
        <p className="mt-1 text-sm text-gray-400">
          토스증권 Open API 자격증명을 등록합니다. 입력 정보는 AES-256-GCM으로 암호화되어
          저장됩니다.
        </p>
      </div>

      {/* Status card */}
      <div className="rounded-lg border border-white/10 bg-gray-900/50 p-4">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs text-gray-400">연결 상태</p>
            <div className="mt-2">
              {isLoading ? (
                <span className="text-sm text-gray-500">확인 중...</span>
              ) : (
                <StatusBadge status={status} />
              )}
            </div>
            {status?.tokenExpiresAt && (
              <p className="mt-1 text-xs text-gray-500">
                토큰 만료: {fmtKst(status.tokenExpiresAt)}
              </p>
            )}
          </div>
          {status?.configured && (
            <button
              type="button"
              disabled={refreshMutation.isPending}
              onClick={() => { setMessage(null); refreshMutation.mutate(); }}
              className="rounded border border-white/20 px-3 py-1.5 text-xs text-gray-300 hover:bg-white/5 disabled:opacity-40"
            >
              {refreshMutation.isPending ? "갱신 중..." : "토큰 수동 갱신"}
            </button>
          )}
        </div>
      </div>

      {/* Credentials form */}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="clientId" className="block text-sm font-medium text-gray-300">
            Client ID
          </label>
          <input
            id="clientId"
            type="password"
            autoComplete="off"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            placeholder="토스증권 client_id"
            className="mt-1 w-full rounded-md border border-white/20 bg-gray-800 px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>
        <div>
          <label htmlFor="clientSecret" className="block text-sm font-medium text-gray-300">
            Client Secret
          </label>
          <input
            id="clientSecret"
            type="password"
            autoComplete="off"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            placeholder="토스증권 client_secret"
            className="mt-1 w-full rounded-md border border-white/20 bg-gray-800 px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-emerald-500 focus:outline-none"
          />
        </div>

        {message && (
          <p
            className={`text-sm ${
              message.type === "success" ? "text-emerald-400" : "text-red-400"
            }`}
          >
            {message.text}
          </p>
        )}

        <button
          type="submit"
          disabled={!clientId.trim() || !clientSecret.trim() || saveMutation.isPending}
          className="rounded-md bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {saveMutation.isPending ? "저장 중..." : "자격증명 저장"}
        </button>
      </form>

      <p className="text-xs text-gray-600">
        토스증권 Open API는 토스증권 앱 → 서비스 → Open API에서 발급받을 수 있습니다.
      </p>
    </div>
  );
}

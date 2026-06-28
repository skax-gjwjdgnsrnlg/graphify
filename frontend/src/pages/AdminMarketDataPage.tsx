import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { PrimaryButton } from "@/components/shared/PrimaryButton";
import { ApiRequestError } from "@/lib/apiClient";
import {
  ingestKospi200Batch,
  seedKospi200,
  type Kospi200Counts,
} from "@/lib/adminApi";

const COUNT_LABELS: Record<string, string> = {
  inserted: "추가",
  updated: "갱신",
  flagged: "플래그",
  ingested: "적재",
  count: "건수",
  symbols: "종목",
  total: "합계",
  failed: "실패",
};

function formatCounts(counts: Kospi200Counts | null | undefined): string {
  if (!counts) return "완료";
  const parts = Object.entries(counts).map(
    ([key, value]) => `${COUNT_LABELS[key] ?? key} ${value}`
  );
  return parts.length > 0 ? parts.join(" · ") : "완료";
}

export function AdminMarketDataPage() {
  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!toast) return;
    const id = window.setTimeout(() => setToast(null), 5000);
    return () => window.clearTimeout(id);
  }, [toast]);

  const seedMutation = useMutation({
    mutationFn: () => seedKospi200(),
    onSuccess: (res) => {
      setError(null);
      setToast(`KOSPI200 시드 완료 — ${formatCounts(res.data)}`);
    },
    onError: (err) => {
      setToast(null);
      setError(
        err instanceof ApiRequestError ? err.message : "시드 실행에 실패했습니다."
      );
    },
  });

  // 청크 분할 적재 — 한 요청에 BATCH개씩, done까지 nextOffset으로 순회(게이트웨이 타임아웃 회피).
  // 각 청크가 독립 요청이라 Cloud Run 기본 CPU 정책에서도 완주한다.
  const INGEST_BATCH_SIZE = 10;
  const [ingestBusy, setIngestBusy] = useState(false);
  const [ingestProgress, setIngestProgress] = useState<{
    done: number;
    total: number;
  } | null>(null);

  const runChunkedIngest = async () => {
    setError(null);
    setToast(null);
    setIngestBusy(true);
    setIngestProgress({ done: 0, total: 0 });
    let offset = 0;
    let totalIngested = 0;
    let totalFailed = 0;
    // 일시적 hiccup(네트워크/콜드스타트/빈 응답)에 한 배치 실패로 전체가 중단되지 않게 재시도.
    const fetchBatchWithRetry = async (off: number) => {
      let lastErr: unknown;
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          const b = (await ingestKospi200Batch(off, INGEST_BATCH_SIZE)).data;
          if (b) return b;
          lastErr = new ApiRequestError("ERR_EMPTY", "적재 응답이 비어 있습니다.");
        } catch (e) {
          lastErr = e;
        }
        await new Promise((r) => window.setTimeout(r, 1500));
      }
      throw lastErr;
    };
    try {
      for (;;) {
        const b = await fetchBatchWithRetry(offset);
        totalIngested += b.ingested;
        totalFailed += b.failed;
        setIngestProgress({ done: b.nextOffset, total: b.total });
        offset = b.nextOffset;
        if (b.done) break;
      }
      setToast(
        `KOSPI200 일봉 적재 완료 — 적재 ${totalIngested}종목` +
          (totalFailed > 0 ? ` · 실패 ${totalFailed}` : "")
      );
    } catch (err) {
      setError(
        err instanceof ApiRequestError ? err.message : "적재 실행에 실패했습니다."
      );
    } finally {
      setIngestBusy(false);
    }
  };

  const ingestRunning = ingestBusy;
  const anyPending = seedMutation.isPending || ingestRunning;

  return (
    <div className="mx-auto w-full max-w-[800px] space-y-8 p-6 md:p-8">
      <header>
        <h1 className="text-2xl font-semibold text-charcoal">시장 데이터</h1>
        <p className="mt-1 text-sm text-muted-gray">
          KOSPI200 종목 마스터 시드와 일봉 적재를 실행합니다. 두 작업 모두 멱등하게 재실행할 수
          있습니다.
        </p>
      </header>

      {toast ? (
        <p
          className="rounded-lg border border-warm-border bg-cream px-4 py-2 text-sm text-charcoal"
          role="status"
        >
          {toast}
        </p>
      ) : null}

      {error ? (
        <p
          className="rounded-lg border border-warm-border bg-cream px-4 py-2 text-sm text-charcoal"
          role="alert"
        >
          {error}
        </p>
      ) : null}

      <section className="space-y-4 rounded-xl border border-warm-border bg-cream p-6">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-medium text-charcoal">KOSPI200 마스터 시드</h2>
            <p className="mt-1 text-sm text-muted-gray">
              큐레이션 리스트(kospi200.csv)를 종목 마스터에 UPSERT하고 in_kospi200 플래그를
              설정합니다.
            </p>
          </div>
          <PrimaryButton
            className="w-auto px-6"
            loading={seedMutation.isPending}
            disabled={anyPending}
            onClick={() => seedMutation.mutate()}
          >
            시드 실행
          </PrimaryButton>
        </div>

        <div className="border-t border-warm-border" />

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-base font-medium text-charcoal">KOSPI200 일봉 적재</h2>
            <p className="mt-1 text-sm text-muted-gray">
              KOSPI200 구성종목의 일봉 데이터를 적재합니다. 시드 후 실행하세요. 수 분 걸리는
              작업이라 백그라운드로 진행되며, 진행 상태가 아래에 표시됩니다.
            </p>
            {ingestRunning && ingestProgress ? (
              <p className="mt-1 text-sm text-charcoal">
                적재 진행 중… {ingestProgress.done}
                {ingestProgress.total > 0 ? `/${ingestProgress.total}` : ""}종목
              </p>
            ) : null}
          </div>
          <PrimaryButton
            className="w-auto px-6"
            loading={ingestRunning}
            disabled={anyPending}
            onClick={() => void runChunkedIngest()}
          >
            적재 실행
          </PrimaryButton>
        </div>
      </section>
    </div>
  );
}

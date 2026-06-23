import { useState } from "react";
import { useTradingStore } from "@/stores/tradingStore";
import { updateTradingMode } from "@/lib/tradingApi";
import type { TradingMode } from "@/types/user";

export function PaperTradingToggle() {
  const mode = useTradingStore((s) => s.mode);
  const setMode = useTradingStore((s) => s.setMode);
  const [pending, setPending] = useState(false);
  const [confirmLive, setConfirmLive] = useState(false);

  const isPaper = mode === "PAPER";

  const applyMode = async (next: TradingMode) => {
    const prev = mode;
    setMode(next); // 낙관적 업데이트
    setPending(true);
    try {
      await updateTradingMode(next);
    } catch {
      setMode(prev); // 실패 시 롤백
    } finally {
      setPending(false);
    }
  };

  const handleToggle = () => {
    if (pending) return;
    if (isPaper) {
      // PAPER -> LIVE: 위험 동작, 확인 필요
      setConfirmLive(true);
    } else {
      // LIVE -> PAPER: 안전 방향, 즉시 적용
      void applyMode("PAPER");
    }
  };

  return (
    <div className="border-t border-white/10 p-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex flex-col">
          <span className="text-sm text-gray-300">모의투자</span>
          <span
            className={`text-xs ${isPaper ? "text-emerald-400" : "text-red-400"}`}
          >
            {isPaper ? "모의투자 모드" : "실거래 중"}
          </span>
        </div>
        <button
          type="button"
          onClick={handleToggle}
          disabled={pending}
          aria-label={isPaper ? "실거래 모드로 전환" : "모의투자 모드로 전환"}
          className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors focus:outline-none disabled:opacity-50 ${
            isPaper ? "bg-emerald-500" : "bg-white/20"
          }`}
        >
          <span
            className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
              isPaper ? "translate-x-6" : "translate-x-1"
            }`}
          />
        </button>
      </div>

      {confirmLive ? (
        <div className="fixed inset-0 z-50 flex items-center justify-center">
          <button
            type="button"
            className="absolute inset-0 bg-black/60"
            aria-label="닫기"
            onClick={() => setConfirmLive(false)}
          />
          <div className="relative w-full max-w-sm rounded-lg border border-white/10 bg-gray-900 p-6 shadow-xl">
            <h2 className="text-base font-semibold text-white">실거래 모드 전환</h2>
            <p className="mt-2 text-sm text-gray-400">
              실거래 모드로 전환합니다. 실제 자금이 사용될 수 있습니다. 계속하시겠습니까?
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setConfirmLive(false)}
                className="rounded-md border border-white/20 px-4 py-2 text-sm text-gray-300 hover:bg-white/5"
              >
                취소
              </button>
              <button
                type="button"
                disabled={pending}
                onClick={() => {
                  setConfirmLive(false);
                  void applyMode("LIVE");
                }}
                className="rounded-md bg-red-600 px-4 py-2 text-sm text-white transition-opacity hover:opacity-90 disabled:opacity-50"
              >
                실거래로 전환
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

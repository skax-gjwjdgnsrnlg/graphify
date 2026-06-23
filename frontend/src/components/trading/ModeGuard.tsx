import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useTradingStore } from "@/stores/tradingStore";
import type { TradingMode } from "@/types/user";

const FALLBACK: Record<TradingMode, string> = {
  PAPER: "/trading/paper/dashboard",
  LIVE: "/trading/dashboard",
};

/**
 * 현재 트레이딩 모드가 요구 모드와 다르면 해당 모드의 기본 페이지로 리다이렉트한다.
 * 딥링크로 반대 모드 경로에 접근하는 것을 막는다.
 */
export function ModeGuard({
  mode: required,
  children,
}: {
  mode: TradingMode;
  children: ReactNode;
}) {
  const mode = useTradingStore((s) => s.mode);
  if (mode !== required) {
    return <Navigate to={FALLBACK[mode]} replace />;
  }
  return <>{children}</>;
}

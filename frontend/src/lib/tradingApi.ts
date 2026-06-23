import { apiGet, apiPut } from "@/lib/apiClient";
import type { TradingMode, TradingSettings } from "@/types/user";

export async function fetchTradingSettings() {
  return apiGet<TradingSettings>("/api/v1/trading/settings");
}

export async function updateTradingMode(mode: TradingMode) {
  return apiPut<TradingSettings, { mode: TradingMode }>("/api/v1/trading/mode", {
    mode,
  });
}

import { apiDelete, apiGet, apiPost, apiPut } from "@/lib/apiClient";
import type {
  BacktestRequest,
  BacktestResult,
  CandleBar,
  RuleUpsertRequest,
  TradingRule,
} from "@/types/trading";

const BASE = "/api/v1/trading/paper/rules";

export async function fetchPaperRules() {
  return apiGet<TradingRule[]>(BASE);
}

export async function fetchPaperRule(id: number) {
  return apiGet<TradingRule>(`${BASE}/${id}`);
}

export async function createPaperRule(payload: RuleUpsertRequest) {
  return apiPost<TradingRule, RuleUpsertRequest>(BASE, payload);
}

export async function updatePaperRule(id: number, payload: RuleUpsertRequest) {
  return apiPut<TradingRule, RuleUpsertRequest>(`${BASE}/${id}`, payload);
}

export async function deletePaperRule(id: number) {
  return apiDelete<void>(`${BASE}/${id}`);
}

export async function runBacktest(payload: BacktestRequest) {
  return apiPost<BacktestResult, BacktestRequest>("/api/v1/trading/paper/backtest", payload);
}

// date は YYYY-MM-DD 形式
export async function fetchBars(symbol: string, date: string) {
  return apiGet<CandleBar[]>(
    `/api/v1/trading/paper/backtest/bars?symbol=${encodeURIComponent(symbol)}&date=${date}`
  );
}

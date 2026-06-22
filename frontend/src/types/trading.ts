// 캔들 차트 — OHLCV 봉 데이터 (lightweight-charts v5 시계열 포맷)
export interface CandleBar {
  time: number;   // epoch-seconds (UTC)
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

// 룰 definition 스키마 — DESIGN.md [v1.3.0] 8절과 일치
export type RuleIndicator = "PRICE" | "SMA" | "EMA" | "RSI" | "VOLUME";
export type RuleOperator = ">" | ">=" | "<" | "<=" | "==" | "crossAbove" | "crossBelow";
export type RuleLogic = "AND" | "OR";
export type SizingType = "cash" | "percent" | "qty";
export type UniverseType = "symbols" | "watchlist" | "volume_top_n";

export interface RuleOperand {
  indicator?: RuleIndicator;
  value?: number;
  params?: { period?: number; [k: string]: unknown };
}

export interface RuleCondition {
  left: RuleOperand;
  op: RuleOperator;
  right: RuleOperand;
}

export interface RuleConditionGroup {
  logic: RuleLogic;
  conditions: RuleCondition[];
}

export interface RuleExitSpec {
  logic?: RuleLogic;
  conditions?: RuleCondition[];
  takeProfitPct?: number;
  stopLossPct?: number;
}

export interface RuleSizing {
  type: SizingType;
  value: number;
}

export interface RuleConstraints {
  maxPositionsPerSymbol?: number;
  cooldownBars?: number;
}

export interface RuleDefinition {
  version: 1;
  universe: { type: UniverseType; symbols?: string[]; market?: string; topN?: number; additionalSymbols?: string[] };
  entry: RuleConditionGroup;
  exit?: RuleExitSpec;
  sizing: RuleSizing;
  constraints?: RuleConstraints;
}

export type RuleStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "BACKTESTED" | "PAPER_LIVE" | "LIVE";
export type RuleMode = "PAPER" | "LIVE";
export type ConfigStatus = "DRAFT" | "ACTIVE";
export type RunStatus = "STOPPED" | "RUNNING";

export interface TradingRule {
  id: number;
  name: string;
  mode: RuleMode;
  status: RuleStatus;
  backtested: boolean;
  definition: RuleDefinition;
  promotedFrom: number | null;
  createdAt: string;
  updatedAt: string;
  configStatus: ConfigStatus;
  runStatus: RunStatus;
}

export interface RuleUpsertRequest {
  name: string;
  status?: RuleStatus;
  definition: RuleDefinition;
}

// 백테스트 — DESIGN.md [v1.3.0] 5~7절
export interface BacktestRequest {
  ruleId?: number;
  definition?: RuleDefinition;
  from?: string;       // YYYY-MM-DD
  to?: string;
  initialCash?: number;
  timeFrom?: string;   // HH:mm, e.g. "09:00"
  timeTo?: string;     // HH:mm, e.g. "12:00"
}

export interface BacktestTrade {
  datetime: string;
  symbol: string;
  companyName: string | null;
  side: "BUY" | "SELL";
  qty: number;
  price: number;
  pnl: number | null;
  rationaleJson: string | null;
}

export interface BacktestEquityPoint {
  datetime: string;  // was: date
  equity: number;
}

export interface DrawdownSegment {
  start: string;  // ISO datetime string e.g. "2026-01-05T09:15:00"
  end: string;
}

export interface BacktestResult {
  initialCash: number;
  finalEquity: number;
  returnPct: number;
  maxDrawdownPct: number;
  winRate: number;
  tradeCount: number;
  sharpeRatio: number;
  sortinoRatio: number;
  profitFactor: number;
  drawdownSegments: DrawdownSegment[];
  trades: BacktestTrade[];
  equityCurve: BacktestEquityPoint[];
}

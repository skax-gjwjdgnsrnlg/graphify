export interface PaperPositionItem {
  symbol: string;
  companyName: string | null;
  qty: number;
  avgPrice: number;
  markPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  unrealizedPnlPct: number;
}

export interface PaperDashboardData {
  cash: number;
  totalEquity: number;
  totalUnrealizedPnl: number;
  todayRealizedPnl: number;
  activePaperLiveRuleCount: number;
  positions: PaperPositionItem[];
}

export interface SignalLogItem {
  id: number;
  ruleId: number;
  symbol: string;
  ts: string;
  signal: "BUY" | "SELL" | "HOLD";
  executed: boolean;
  rsi14: number | null;
  sma20: number | null;
  price: number | null;
}

export interface TradeItem {
  id: number;
  symbol: string;
  side: "BUY" | "SELL";
  qty: number;
  price: number;
  pnl: number | null;
  tradedAt: string;
}

export interface PaperTradeHistoryItem {
  id: number;
  tradedAt: string;
  symbol: string;
  companyName: string | null;
  side: "BUY" | "SELL";
  qty: number;
  price: number;
  fee: number | null;
  pnl: number | null;
  rationaleJson: string | null;
}

export interface MonitorData {
  schedulerLastRun: string | null;
  marketStatus: "OPEN" | "CLOSED";
  recentSignals: SignalLogItem[];
  todayTrades: TradeItem[];
}

export interface EquityPoint {
  datetime: string;
  equity: number;
}

export interface ReportData {
  equityCurve: EquityPoint[];
  totalReturn: number;
  maxDrawdownPct: number;
  winRate: number;
  totalTrades: number;
  winTrades: number;
  sharpeRatio: number;
  sortinoRatio: number;
  periodFrom: string | null;
  periodTo: string | null;
}

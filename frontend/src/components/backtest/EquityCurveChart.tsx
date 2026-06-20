import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceArea,
  ResponsiveContainer,
} from "recharts";
import type { BacktestEquityPoint, DrawdownSegment } from "@/types/trading";

interface Props {
  data: BacktestEquityPoint[];
  drawdownSegments: DrawdownSegment[];
  initialCash: number;
}

// XAxis tick formatter: show date label only at session open (09:00), else empty string
function formatXTick(value: string): string {
  try {
    const dt = new Date(value);
    const hours = dt.getHours();
    const mins = dt.getMinutes();
    if (hours === 9 && mins === 0) {
      return dt.toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
    }
    return "";
  } catch {
    return "";
  }
}

// YAxis formatter: Korean won abbreviated
function formatYAxis(value: number): string {
  if (value >= 100_000_000) return `${(value / 100_000_000).toFixed(0)}억`;
  if (value >= 10_000) return `${(value / 10_000).toFixed(0)}만`;
  return value.toLocaleString("ko-KR");
}

interface TooltipPayloadItem {
  value: number;
  dataKey: string;
}

interface CustomTooltipProps {
  active?: boolean;
  payload?: TooltipPayloadItem[];
  label?: string;
  initialCash: number;
}

function CustomTooltip({ active, payload, label, initialCash }: CustomTooltipProps) {
  if (!active || !payload || payload.length === 0 || !label) return null;

  const equity = payload[0]?.value ?? 0;
  const returnPct = initialCash > 0 ? ((equity - initialCash) / initialCash) * 100 : 0;

  // Format datetime label: "2026-01-02T09:05:00" → "2026-01-02 09:05"
  let formattedDt = label;
  try {
    const dt = new Date(label);
    const date = dt.toLocaleDateString("ko-KR", { year: "numeric", month: "2-digit", day: "2-digit" });
    const time = dt.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hour12: false });
    formattedDt = `${date} ${time}`;
  } catch {
    // keep original label
  }

  return (
    <div className="rounded-md border border-white/10 bg-gray-800 px-3 py-2 text-xs shadow-lg">
      <p className="text-gray-400">{formattedDt}</p>
      <p className="mt-1 text-white font-medium">
        {equity.toLocaleString("ko-KR", { maximumFractionDigits: 0 })}원
      </p>
      <p className={`mt-0.5 font-semibold ${returnPct >= 0 ? "text-emerald-400" : "text-red-400"}`}>
        {returnPct >= 0 ? "+" : ""}{returnPct.toFixed(2)}%
      </p>
    </div>
  );
}

export function EquityCurveChart({ data, drawdownSegments, initialCash }: Props) {
  if (data.length === 0) {
    return (
      <div className="flex h-[350px] items-center justify-center rounded-lg border border-white/10 bg-gray-900/50 text-sm text-gray-500">
        수익 곡선 데이터가 없습니다.
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={350}>
      <LineChart
        data={data}
        margin={{ top: 8, right: 16, left: 16, bottom: 8 }}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
        <XAxis
          dataKey="datetime"
          tickFormatter={formatXTick}
          tick={{ fill: "#9ca3af", fontSize: 11 }}
          axisLine={{ stroke: "rgba(255,255,255,0.1)" }}
          tickLine={false}
        />
        <YAxis
          tickFormatter={formatYAxis}
          tick={{ fill: "#9ca3af", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={60}
        />
        <Tooltip
          content={<CustomTooltip initialCash={initialCash} />}
          cursor={{ stroke: "rgba(255,255,255,0.15)", strokeWidth: 1 }}
        />
        {drawdownSegments.map((seg, i) => (
          <ReferenceArea
            key={i}
            x1={seg.start}
            x2={seg.end}
            fill="rgba(239, 68, 68, 0.15)"
            stroke="none"
          />
        ))}
        <Line
          type="monotone"
          dataKey="equity"
          stroke="#10b981"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4, fill: "#10b981", stroke: "#1f2937", strokeWidth: 2 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

package com.graphify.trading.paper.dto;

import java.time.Instant;
import java.util.List;

public record ReportDto(
        List<EquityPoint> equityCurve,
        double totalReturn,
        double maxDrawdownPct,
        double winRate,
        int totalTrades,
        int winTrades,
        double sharpeRatio,
        double sortinoRatio,
        Instant periodFrom,
        Instant periodTo
) {
    public record EquityPoint(String datetime, double equity) {}

    public static ReportDto empty() {
        return new ReportDto(List.of(), 0.0, 0.0, 0.0, 0, 0, 0.0, 0.0, null, null);
    }
}

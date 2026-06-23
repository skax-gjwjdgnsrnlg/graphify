package com.graphify.trading.paper.dto;

public record PaperPositionItem(
        String symbol,
        String companyName,
        double qty,
        double avgPrice,
        double markPrice,
        double marketValue,
        double unrealizedPnl,
        double unrealizedPnlPct
) {}

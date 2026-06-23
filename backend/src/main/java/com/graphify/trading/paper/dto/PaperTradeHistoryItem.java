package com.graphify.trading.paper.dto;

import java.time.Instant;

public record PaperTradeHistoryItem(
        Long id,
        Instant tradedAt,
        String symbol,
        String companyName,
        String side,
        double qty,
        double price,
        Double fee,
        Double pnl,
        String rationaleJson) {}

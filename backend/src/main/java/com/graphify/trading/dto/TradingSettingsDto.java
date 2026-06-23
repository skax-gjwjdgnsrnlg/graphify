package com.graphify.trading.dto;

public record TradingSettingsDto(
        boolean tradingEnabled,
        String tradingMode
) {
}

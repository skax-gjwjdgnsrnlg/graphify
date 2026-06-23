package com.graphify.user.dto;

public record UserMeDto(
        Long id,
        String email,
        String displayName,
        String authProvider,
        boolean isPremium,
        String customPrompt,
        boolean tradingEnabled,
        String tradingMode
) {
}

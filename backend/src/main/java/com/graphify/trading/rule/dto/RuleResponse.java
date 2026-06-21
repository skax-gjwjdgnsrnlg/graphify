package com.graphify.trading.rule.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record RuleResponse(
        Long id,
        String name,
        String mode,
        String status,
        boolean backtested,
        JsonNode definition,
        Long promotedFrom,
        Instant createdAt,
        Instant updatedAt
) {
}

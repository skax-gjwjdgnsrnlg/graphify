package com.graphify.trading.rule.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record RuleUpsertRequest(
        String name,
        String status,
        JsonNode definition
) {
}

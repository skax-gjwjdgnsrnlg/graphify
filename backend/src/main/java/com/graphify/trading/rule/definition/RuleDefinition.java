package com.graphify.trading.rule.definition;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * 룰 definition (JSONB) 의 Java 모델. DESIGN.md [v1.3.0] 8절 스키마와 일치.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDefinition(
        Integer version,
        Universe universe,
        ConditionGroup entry,
        ExitSpec exit,
        Sizing sizing,
        Constraints constraints
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Universe(
            String type,                    // "symbols" (기존) | "volume_top_n" (신규) | "watchlist" (기존)
            List<String> symbols,           // type="symbols"일 때 사용
            String market,                  // type="volume_top_n"일 때: "KOSPI"
            Integer topN,                   // type="volume_top_n"일 때: 10
            List<String> additionalSymbols  // volume_top_n + 수동 추가 종목 (optional)
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConditionGroup(String logic, List<Condition> conditions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Condition(Operand left, String op, Operand right) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Operand(String indicator, Double value, Map<String, Object> params) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExitSpec(
            String logic,
            List<Condition> conditions,
            Double takeProfitPct,
            Double stopLossPct
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sizing(String type, Double value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Constraints(Integer maxPositionsPerSymbol, Integer cooldownBars) {
    }
}

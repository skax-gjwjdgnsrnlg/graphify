package com.graphify.trading.rule;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.common.dto.ApiResponse;
import com.graphify.common.exception.GraphifyException;
import com.graphify.history.HistoryService;
import com.graphify.trading.rule.definition.RuleDefinition;
import com.graphify.trading.rule.definition.RuleDefinitionValidator;
import com.graphify.trading.rule.dto.RuleResponse;
import com.graphify.trading.rule.dto.RuleUpsertRequest;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaperRuleService {

    private static final String MODE = "PAPER";
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED");

    private final TradingRuleRepository ruleRepository;
    private final RuleDefinitionValidator validator;
    private final ObjectMapper objectMapper;

    public PaperRuleService(
            TradingRuleRepository ruleRepository,
            RuleDefinitionValidator validator,
            ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public ApiResponse<List<RuleResponse>> list() {
        Long userId = HistoryService.requireCurrentUserId();
        List<RuleResponse> rules = ruleRepository
                .findByUserIdAndModeOrderByUpdatedAtDesc(userId, MODE)
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(rules);
    }

    public ApiResponse<RuleResponse> get(Long id) {
        return ApiResponse.ok(toResponse(findOwned(id)));
    }

    @Transactional
    public ApiResponse<RuleResponse> create(RuleUpsertRequest request) {
        Long userId = HistoryService.requireCurrentUserId();
        String name = normalizeName(request.name());
        String status = normalizeStatus(request.status());
        String definitionJson = validateAndSerialize(request.definition());

        TradingRule rule = new TradingRule(userId, name, MODE, status, definitionJson);
        return ApiResponse.ok(toResponse(ruleRepository.save(rule)));
    }

    @Transactional
    public ApiResponse<RuleResponse> update(Long id, RuleUpsertRequest request) {
        TradingRule rule = findOwned(id);
        rule.setName(normalizeName(request.name()));
        rule.setStatus(normalizeStatus(request.status()));
        rule.setDefinition(validateAndSerialize(request.definition()));
        return ApiResponse.ok(toResponse(ruleRepository.save(rule)));
    }

    @Transactional
    public ApiResponse<Void> delete(Long id) {
        TradingRule rule = findOwned(id);
        ruleRepository.delete(rule);
        return ApiResponse.ok(null);
    }

    private TradingRule findOwned(Long id) {
        Long userId = HistoryService.requireCurrentUserId();
        return ruleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GraphifyException(
                        "ERR_RULE_002",
                        "룰을 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private String validateAndSerialize(JsonNode definitionNode) {
        if (definitionNode == null || definitionNode.isNull()) {
            throw new GraphifyException("ERR_RULE_001", "룰 정의가 비어 있습니다.", HttpStatus.BAD_REQUEST);
        }
        RuleDefinition def;
        try {
            def = objectMapper.treeToValue(definitionNode, RuleDefinition.class);
        } catch (JsonProcessingException e) {
            throw new GraphifyException("ERR_RULE_001", "룰 정의 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        validator.validate(def);
        try {
            return objectMapper.writeValueAsString(def);
        } catch (JsonProcessingException e) {
            throw new GraphifyException("ERR_RULE_003", "룰 정의 직렬화에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new GraphifyException("ERR_RULE_004", "룰 이름이 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        if (trimmed.length() > 120) {
            throw new GraphifyException("ERR_RULE_004", "룰 이름은 120자 이하여야 합니다.", HttpStatus.BAD_REQUEST);
        }
        return trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "DRAFT";
        }
        String upper = status.trim().toUpperCase();
        if (!STATUSES.contains(upper)) {
            throw new GraphifyException("ERR_RULE_005", "지원하지 않는 룰 상태입니다.", HttpStatus.BAD_REQUEST);
        }
        return upper;
    }

    private RuleResponse toResponse(TradingRule rule) {
        JsonNode definition;
        try {
            definition = objectMapper.readTree(rule.getDefinition());
        } catch (JsonProcessingException e) {
            definition = objectMapper.createObjectNode();
        }
        return new RuleResponse(
                rule.getId(),
                rule.getName(),
                rule.getMode(),
                rule.getStatus(),
                rule.isBacktested(),
                definition,
                rule.getPromotedFrom(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}

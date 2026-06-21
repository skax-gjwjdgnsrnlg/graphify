package com.graphify.trading.paper;

import com.graphify.common.exception.GraphifyException;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.dto.RuleResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 룰 생애주기 상태 머신.
 * DRAFT → (backtested=true 조건) → PAPER_LIVE → PAUSED → PAPER_LIVE
 * LIVE → 편집 불가 (DRAFT 복사본 생성 경로만 허용)
 */
@Service
@Transactional
public class PaperLifecycleService {

    private final TradingRuleRepository ruleRepo;
    private final ObjectMapper objectMapper;

    public PaperLifecycleService(TradingRuleRepository ruleRepo, ObjectMapper objectMapper) {
        this.ruleRepo = ruleRepo;
        this.objectMapper = objectMapper;
    }

    /** DRAFT → PAPER_LIVE (백테스트 완료 조건 필요) */
    public RuleResponse promote(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"DRAFT".equals(rule.getStatus()) && !"BACKTESTED".equals(rule.getStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_001",
                "DRAFT 또는 BACKTESTED 상태인 룰만 PAPER_LIVE로 승격할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (!rule.isBacktested()) {
            throw new GraphifyException("ERR_LIFECYCLE_002",
                "백테스트를 1회 이상 실행한 룰만 PAPER_LIVE로 승격할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAPER_LIVE");
        return toResponse(ruleRepo.save(rule));
    }

    /** PAPER_LIVE → PAUSED */
    public RuleResponse pause(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"PAPER_LIVE".equals(rule.getStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_003",
                "PAPER_LIVE 상태인 룰만 일시 정지할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAUSED");
        return toResponse(ruleRepo.save(rule));
    }

    /** PAUSED → PAPER_LIVE */
    public RuleResponse resume(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"PAUSED".equals(rule.getStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_004",
                "PAUSED 상태인 룰만 재개할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAPER_LIVE");
        return toResponse(ruleRepo.save(rule));
    }

    /** 모든 상태 → DRAFT 복사본 생성 */
    public RuleResponse copy(Long userId, Long ruleId) {
        TradingRule original = findOwned(userId, ruleId);
        TradingRule copy = new TradingRule(
            userId,
            "복사본 - " + original.getName(),
            original.getMode(),
            "DRAFT",
            original.getDefinition()
        );
        return toResponse(ruleRepo.save(copy));
    }

    private TradingRule findOwned(Long userId, Long ruleId) {
        return ruleRepo.findByIdAndUserId(ruleId, userId)
            .orElseThrow(() -> new GraphifyException(
                "ERR_RULE_002", "룰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private RuleResponse toResponse(TradingRule rule) {
        com.fasterxml.jackson.databind.JsonNode definition;
        try {
            definition = objectMapper.readTree(rule.getDefinition());
        } catch (JsonProcessingException e) {
            definition = objectMapper.createObjectNode();
        }
        return new RuleResponse(
            rule.getId(), rule.getName(), rule.getMode(), rule.getStatus(),
            rule.isBacktested(), definition, rule.getPromotedFrom(), rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }
}

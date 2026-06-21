package com.graphify.trading.paper;

import com.graphify.common.exception.GraphifyException;
import com.graphify.trading.engine.MarketDataPort;
import com.graphify.trading.rule.PaperLiveSymbolService;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.definition.RuleDefinition;
import com.graphify.trading.rule.dto.RuleResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(PaperLifecycleService.class);

    private final TradingRuleRepository ruleRepo;
    private final ObjectMapper objectMapper;
    private final PaperLiveSymbolService paperLiveSymbolService;
    private final MarketDataPort marketData;

    public PaperLifecycleService(
            TradingRuleRepository ruleRepo,
            ObjectMapper objectMapper,
            PaperLiveSymbolService paperLiveSymbolService,
            MarketDataPort marketData) {
        this.ruleRepo = ruleRepo;
        this.objectMapper = objectMapper;
        this.paperLiveSymbolService = paperLiveSymbolService;
        this.marketData = marketData;
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
        List<String> symbols = resolveSymbols(rule);
        if (symbols.isEmpty()) {
            throw new GraphifyException("ERR_LIFECYCLE_005",
                "승격할 룰의 유니버스 종목을 확인할 수 없습니다. 먼저 종목 데이터를 수집하세요.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAPER_LIVE");
        TradingRule saved = ruleRepo.save(rule);
        paperLiveSymbolService.assignSymbols(saved.getId(), symbols);
        return toResponse(saved);
    }

    /** PAPER_LIVE → PAUSED */
    public RuleResponse pause(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"PAPER_LIVE".equals(rule.getStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_003",
                "PAPER_LIVE 상태인 룰만 일시 정지할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAUSED");
        TradingRule saved = ruleRepo.save(rule);
        paperLiveSymbolService.deactivateRule(saved.getId());
        return toResponse(saved);
    }

    /** PAUSED → PAPER_LIVE */
    public RuleResponse resume(Long userId, Long ruleId) {
        TradingRule rule = findOwned(userId, ruleId);
        if (!"PAUSED".equals(rule.getStatus())) {
            throw new GraphifyException("ERR_LIFECYCLE_004",
                "PAUSED 상태인 룰만 재개할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        List<String> symbols = resolveSymbols(rule);
        if (symbols.isEmpty()) {
            throw new GraphifyException("ERR_LIFECYCLE_005",
                "승격할 룰의 유니버스 종목을 확인할 수 없습니다. 먼저 종목 데이터를 수집하세요.", HttpStatus.BAD_REQUEST);
        }
        rule.setStatus("PAPER_LIVE");
        TradingRule saved = ruleRepo.save(rule);
        paperLiveSymbolService.assignSymbols(saved.getId(), symbols);
        return toResponse(saved);
    }

    /** 모든 상태 → DRAFT 복사본 생성. 원본 symbols는 유지 (copy는 비파괴적). */
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

    /**
     * 룰의 유니버스 정의로부터 종목 목록을 결정한다. mode/status 무관 (SEAM 3).
     * Phase 6 LIVE 승격도 이 메서드를 재사용한다.
     *
     * volume_top_n: symbolsByMarket(market) ∪ additionalSymbols 전체 후보군 반환
     * symbols/watchlist: u.symbols() 그대로 반환
     */
    private List<String> resolveSymbols(TradingRule rule) {
        try {
            RuleDefinition def = objectMapper.readValue(rule.getDefinition(), RuleDefinition.class);
            RuleDefinition.Universe u = def.universe();
            if (u == null) return List.of();
            if ("volume_top_n".equals(u.type())) {
                String market = u.market() != null ? u.market() : "KOSPI";
                LinkedHashSet<String> all = new LinkedHashSet<>(marketData.symbolsByMarket(market));
                if (u.additionalSymbols() != null) {
                    all.addAll(u.additionalSymbols());
                }
                return List.copyOf(all);
            }
            // type="symbols" or "watchlist"
            return u.symbols() != null ? u.symbols() : List.of();
        } catch (Exception e) {
            log.warn("Cannot parse rule definition for rule {}: {}", rule.getId(), e.getMessage());
            return List.of();
        }
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

package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.rule.PaperLiveSymbolService;
import com.graphify.trading.rule.RuleStatus;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.definition.RuleDefinition;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 매 틱 volume_top_n 룰의 paper_live_symbols를 재선정한다.
 *
 * <p>재선정 대상: {@code universe.type == "volume_top_n"}인 RUNNING 룰만.
 * symbols/watchlist 타입 룰은 건드리지 않는다 (RESEARCH Pitfall 3).
 *
 * <p>재선정 집합 = 새 top-N ∪ 보유 포지션 종목.
 * 보유 포지션 종목은 top-N 이탈 후에도 수집·청산 평가 대상으로 유지된다 (SC4, Pitfall 2).
 *
 * <p>한 룰 실패가 전체 틱을 막지 않도록 per-rule try/catch + log.warn 처리.
 */
@Component
public class VolumeRankRefresher {

    private static final Logger log = LoggerFactory.getLogger(VolumeRankRefresher.class);

    private final TradingRuleRepository       ruleRepo;
    private final PaperLiveSymbolService      paperLiveSymbolService;
    private final PaperAccountRepository      accountRepo;
    private final PaperPositionRepository     positionRepo;
    private final VolumeRankingProvider       liveRanking;
    private final ObjectMapper                objectMapper;

    public VolumeRankRefresher(
            TradingRuleRepository ruleRepo,
            PaperLiveSymbolService paperLiveSymbolService,
            PaperAccountRepository accountRepo,
            PaperPositionRepository positionRepo,
            @Qualifier("yahooCumulativeVolumeAdapter") VolumeRankingProvider liveRanking,
            ObjectMapper objectMapper) {
        this.ruleRepo               = ruleRepo;
        this.paperLiveSymbolService = paperLiveSymbolService;
        this.accountRepo            = accountRepo;
        this.positionRepo           = positionRepo;
        this.liveRanking            = liveRanking;
        this.objectMapper           = objectMapper;
    }

    /**
     * RUNNING 상태 volume_top_n 룰 각각에 대해 paper_live_symbols를 재선정한다.
     * LiveDataScheduler.collectLiveData()에서 activeSymbolsUnion() 직전에 호출.
     *
     * @param today 오늘 날짜 (KST LocalDate) — YahooCumulativeVolumeAdapter에 당일 전달
     */
    public void refreshIfVolumeTopN(LocalDate today) {
        List<TradingRule> activeRules = ruleRepo.findAll().stream()
            .filter(RuleStatus::isLiveActive)
            .toList();

        for (TradingRule rule : activeRules) {
            try {
                refreshOneRule(rule, today);
            } catch (Exception e) {
                log.warn("VolumeRankRefresher: error refreshing rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }
    }

    private void refreshOneRule(TradingRule rule, LocalDate today) throws Exception {
        RuleDefinition def = objectMapper.readValue(rule.getDefinition(), RuleDefinition.class);
        RuleDefinition.Universe u = def.universe();

        // Pitfall 3: volume_top_n 타입 룰만 재선정 대상
        if (u == null || !"volume_top_n".equals(u.type())) {
            return;
        }

        String  market = u.market() != null ? u.market() : "KOSPI";
        int     topN   = u.topN()   != null ? u.topN()   : 10;

        // 1. 새 top-N 조회
        LinkedHashSet<String> merged = new LinkedHashSet<>(
            liveRanking.topVolume(market, today, topN, true)
        );

        // 2. additionalSymbols 합산 (optional)
        if (u.additionalSymbols() != null) {
            merged.addAll(u.additionalSymbols());
        }

        // 3. 보유 포지션 union (Pitfall 2 — top-N 이탈 종목도 청산 평가 위해 수집 대상 유지)
        PaperAccount account = accountRepo.findByUserId(rule.getUserId()).orElse(null);
        if (account != null) {
            List<PaperPosition> positions = positionRepo.findByAccountId(account.getId());
            for (PaperPosition pos : positions) {
                merged.add(pos.getSymbol());
            }
        }

        // 4. paper_live_symbols 교체 (delete + insert)
        paperLiveSymbolService.assignSymbols(rule.getId(), merged);
        log.debug("VolumeRankRefresher: rule {} → {} symbols (top-N={}, market={})",
            rule.getId(), merged.size(), topN, market);
    }
}

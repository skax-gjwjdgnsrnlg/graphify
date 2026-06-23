package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.engine.EvalResult;
import com.graphify.trading.engine.Indicators;
import com.graphify.trading.engine.MarketDataPort;
import com.graphify.trading.engine.RuleEvaluator;
import com.graphify.trading.engine.Signal;
import com.graphify.trading.rule.PaperLiveSymbol;
import com.graphify.trading.rule.PaperLiveSymbolRepository;
import com.graphify.trading.rule.RuleStatus;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.definition.RuleDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAPER_LIVE 룰 평가 서비스. LiveDataScheduler가 인제스트 완료 후 호출.
 *
 * 평가 순서 (per tick):
 *   1. PAPER_LIVE 룰 목록 로드
 *   2. 룰별 → 종목별 최신 5분봉 조회
 *   3. staleness / min-bars 체크
 *   4. RuleEvaluator로 진입/청산 조건 평가
 *   5. PaperExecutor로 가상 체결 (신호 있을 때)
 *   6. PaperEquitySnapshot 저장
 */
@Service
public class LiveEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(LiveEvaluationService.class);
    static final int  MIN_BARS_REQUIRED = 20;   // RSI-14 + SMA-20 계산 최소치
    // Yahoo 5분봉(라이브 시세 소스)이 KRX 대비 ~15분 지연되므로 10분 임계는 장중 거의
    // 모든 종목을 stale로 스킵시킨다. 지연 마진을 확보해 25분으로 상향 (ISSUE-2).
    static final long STALENESS_MINUTES = 25L;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TradingRuleRepository         ruleRepo;
    private final MarketDataPort                marketDataPort;
    private final MarketBarIntradayRepository   intradayRepo;
    private final RuleEvaluator                 ruleEvaluator;
    private final List<OrderExecutorPort>       executors;
    private final PaperAccountRepository        accountRepo;
    private final PaperPositionRepository       positionRepo;
    private final PaperEquitySnapshotRepository snapshotRepo;
    private final ObjectMapper                  objectMapper;
    private final PaperLiveSymbolRepository     paperLiveSymbolRepository;
    private final VolumeRankingProvider         liveRanking;

    public LiveEvaluationService(
            TradingRuleRepository ruleRepo,
            MarketDataPort marketDataPort,
            MarketBarIntradayRepository intradayRepo,
            RuleEvaluator ruleEvaluator,
            List<OrderExecutorPort> executors,
            PaperAccountRepository accountRepo,
            PaperPositionRepository positionRepo,
            PaperEquitySnapshotRepository snapshotRepo,
            ObjectMapper objectMapper,
            PaperLiveSymbolRepository paperLiveSymbolRepository,
            @Qualifier("naverTradingValueRankingAdapter") VolumeRankingProvider liveRanking) {
        this.ruleRepo                  = ruleRepo;
        this.marketDataPort            = marketDataPort;
        this.intradayRepo              = intradayRepo;
        this.ruleEvaluator             = ruleEvaluator;
        this.executors                 = executors;
        this.accountRepo               = accountRepo;
        this.positionRepo              = positionRepo;
        this.snapshotRepo              = snapshotRepo;
        this.objectMapper              = objectMapper;
        this.paperLiveSymbolRepository = paperLiveSymbolRepository;
        this.liveRanking               = liveRanking;
    }

    private OrderExecutorPort executorFor(TradingRule rule) {
        return executors.stream()
            .filter(e -> e.supports(rule))
            .findFirst()
            .orElse(null);
    }

    /**
     * 매 틱 진입점. LiveDataScheduler.collectLiveData() 마지막에 호출.
     */
    @Transactional
    public void evaluateTick(Instant tickTime) {
        List<TradingRule> paperLiveRules = ruleRepo.findAll().stream()
            .filter(RuleStatus::isLiveActive)
            .toList();

        if (paperLiveRules.isEmpty()) {
            log.debug("No PAPER_LIVE rules — skipping evaluation tick at {}", tickTime);
            return;
        }

        for (TradingRule rule : paperLiveRules) {
            try {
                evaluateRule(rule, tickTime);
            } catch (Exception e) {
                log.error("Error evaluating rule {}: {}", rule.getId(), e.getMessage(), e);
            }
        }
    }

    private void evaluateRule(TradingRule rule, Instant tickTime) {
        RuleDefinition def;
        try {
            def = objectMapper.readValue(rule.getDefinition(), RuleDefinition.class);
        } catch (Exception e) {
            log.warn("Cannot parse rule definition for rule {}: {}", rule.getId(), e.getMessage());
            return;
        }

        List<String> symbols = resolveSymbols(rule);
        PaperAccount account = accountRepo.findByUserId(rule.getUserId()).orElse(null);
        if (account == null) {
            log.warn("No paper account for user {} (rule {}), skipping", rule.getUserId(), rule.getId());
            return;
        }

        // 진입 게이팅: volume_top_n 룰이면 현재 top-N 계산(룰당 1회). null이면 게이팅 없음.
        Set<String> entrySet = buildEntrySet(def, tickTime);

        for (String symbol : symbols) {
            try {
                evaluateSymbol(rule, def, account, symbol, tickTime, entrySet);
            } catch (Exception e) {
                log.warn("Error evaluating symbol {} for rule {}: {}", symbol, rule.getId(), e.getMessage());
            }
        }

        // Equity snapshot after all symbols evaluated for this rule/account
        saveEquitySnapshot(account, tickTime);
    }

    /**
     * volume_top_n 룰이면 현재 top-N 집합을 1회 계산해 반환. 비-volume_top_n이면 null(게이팅 없음).
     * top-N 조회 실패 시 null 반환 — 실패가 평가 자체를 막지 않게 함.
     */
    private Set<String> buildEntrySet(RuleDefinition def, Instant tickTime) {
        if (def.universe() == null || !"volume_top_n".equals(def.universe().type())) {
            return null; // 게이팅 없음
        }
        try {
            String market = def.universe().market() != null ? def.universe().market() : "KOSPI";
            int topN = def.universe().topN() != null ? def.universe().topN() : 10;
            LocalDate today = tickTime.atZone(KST).toLocalDate();
            List<String> topList = liveRanking.topVolume(market, today, topN, true);
            return new HashSet<>(topList);
        } catch (Exception e) {
            log.warn("Failed to compute entrySet for volume_top_n rule: {}", e.getMessage());
            return null; // 실패 시 게이팅 없이 기존 동작 유지
        }
    }

    private void evaluateSymbol(TradingRule rule, RuleDefinition def,
                                PaperAccount account, String symbol, Instant tickTime,
                                Set<String> entrySet) {
        // Staleness check
        Optional<Instant> maxTs = intradayRepo.findMaxTsBySymbolAndInterval(symbol, "5m");
        if (maxTs.isEmpty() || maxTs.get().isBefore(tickTime.minus(STALENESS_MINUTES, ChronoUnit.MINUTES))) {
            log.debug("Skipping stale/missing bars for {} at {}", symbol, tickTime);
            return;
        }

        List<MarketBarIntraday> bars = marketDataPort.recentIntradayBars(symbol);
        if (bars.size() < MIN_BARS_REQUIRED) {
            log.debug("Insufficient bars ({}) for {} — need {}", bars.size(), symbol, MIN_BARS_REQUIRED);
            return;
        }

        double[] closes  = bars.stream().mapToDouble(MarketBarIntraday::getClose).toArray();
        Double[] volumes = bars.stream()
            .map(b -> b.getVolume() != null ? b.getVolume().doubleValue() : null)
            .toArray(Double[]::new);
        int last = closes.length - 1;
        double lastPrice = closes[last];

        // Indicator snapshot for signal log (MON-04)
        String indicatorJson = buildIndicatorSnapshot(closes, last, lastPrice);

        // Position check → determine signal + capture EvalResult for rationale
        Optional<PaperPosition> posOpt = positionRepo.findByAccountIdAndSymbol(account.getId(), symbol);

        Signal signal;
        String snapshotJson = indicatorJson; // default: plain snapshot (no rationale)
        if (posOpt.isPresent()) {
            // Already holding — check exit (청산은 top-N 무관 항상 평가, SC4)
            double entryPrice = posOpt.get().getAvgPrice().doubleValue();
            EvalResult exitResult = ruleEvaluator.evalExit(def.exit(), closes, volumes, last, entryPrice);
            signal = exitResult.triggered() ? Signal.SELL : Signal.HOLD;
            if (exitResult.triggered()) {
                snapshotJson = mergeRationale(indicatorJson, exitResult, "SELL");
            }
        } else {
            // 진입 게이팅: volume_top_n 룰이면 top-N 멤버십 체크 (SC4 — 비멤버 진입 차단)
            if (entrySet != null && !entrySet.contains(symbol)) {
                log.debug("Entry gated for {}: not in current top-N", symbol);
                return;
            }
            // No position — check entry
            EvalResult entryResult = ruleEvaluator.evalEntry(def.entry(), closes, volumes, last);
            signal = entryResult.triggered() ? Signal.BUY : Signal.HOLD;
            if (entryResult.triggered()) {
                snapshotJson = mergeRationale(indicatorJson, entryResult, "BUY");
            }
        }

        if (signal != Signal.HOLD) {
            OrderExecutorPort executor = executorFor(rule);
            if (executor == null) {
                log.warn("No OrderExecutorPort supports rule {} (mode={}), skipping execution",
                    rule.getId(), rule.getMode());
                return;
            }
            executor.execute(signal, rule, symbol, lastPrice, tickTime, snapshotJson);
        }
    }

    /**
     * 기존 indicatorSnapshot JSON에 rationale 블록을 병합한다.
     * 결과: {"price":..., "rsi14":..., "sma20":..., "rationale":{"side":..., "exitReason":..., "exitPct":..., "conditions":[...]}}
     * Plan 03(백테스트)와 동일 스키마 (RULE-09 SC5).
     */
    String mergeRationale(String snapshotJson, EvalResult evalResult, String side) {
        try {
            ObjectNode root = snapshotJson != null
                    ? (ObjectNode) objectMapper.readTree(snapshotJson)
                    : objectMapper.createObjectNode();

            ObjectNode rationale = objectMapper.createObjectNode();
            rationale.put("side", side);
            if (evalResult.exitReason() != null) {
                rationale.put("exitReason", evalResult.exitReason().name());
            } else {
                rationale.putNull("exitReason");
            }
            if (evalResult.exitPct() != null) {
                rationale.put("exitPct", evalResult.exitPct());
            } else {
                rationale.putNull("exitPct");
            }
            rationale.set("conditions", objectMapper.valueToTree(evalResult.conditions()));
            root.set("rationale", rationale);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Failed to merge rationale into snapshot: {}", e.getMessage());
            return snapshotJson;
        }
    }

    private String buildIndicatorSnapshot(double[] closes, int last, double price) {
        try {
            double rsi14 = Indicators.rsi(closes, last, 14);
            double sma20 = Indicators.sma(closes, last, 20);
            Map<String, Object> snap = Map.of(
                "price", price,
                "rsi14", Double.isNaN(rsi14) ? 0.0 : Math.round(rsi14 * 100.0) / 100.0,
                "sma20", Double.isNaN(sma20) ? 0.0 : Math.round(sma20 * 100.0) / 100.0
            );
            return objectMapper.writeValueAsString(snap);
        } catch (Exception e) {
            return null;
        }
    }

    private void saveEquitySnapshot(PaperAccount account, Instant tickTime) {
        try {
            // Equity = cash + market value of all positions
            List<PaperPosition> positions = positionRepo.findByAccountId(account.getId());
            double equity = account.getCash().doubleValue();
            for (PaperPosition pos : positions) {
                List<MarketBarIntraday> bars = marketDataPort.recentIntradayBars(pos.getSymbol());
                double markPrice = bars.isEmpty()
                    ? pos.getAvgPrice().doubleValue()
                    : bars.get(bars.size() - 1).getClose();
                equity += pos.getQty().doubleValue() * markPrice;
            }
            snapshotRepo.save(new PaperEquitySnapshot(
                account.getId(), tickTime,
                BigDecimal.valueOf(equity).setScale(4, RoundingMode.HALF_UP),
                account.getCash()
            ));
        } catch (Exception e) {
            log.warn("Failed to save equity snapshot for account {}: {}", account.getId(), e.getMessage());
        }
    }

    /**
     * paper_live_symbols 테이블을 단일 정보 소스로 사용.
     * promote()가 이미 유니버스를 해석해 행을 삽입했으므로 ingested symbols == evaluated symbols 보장.
     * volume_top_n 룰도 올바르게 평가됨 (후보군 전체가 테이블에 저장된 상태).
     */
    private List<String> resolveSymbols(TradingRule rule) {
        List<String> symbols = paperLiveSymbolRepository.findByRuleId(rule.getId())
            .stream()
            .map(PaperLiveSymbol::getSymbol)
            .distinct()
            .toList();
        if (symbols.isEmpty()) {
            log.debug("No symbols in paper_live_symbols for rule {} — rule may have been promoted with empty universe",
                rule.getId());
        }
        return symbols;
    }
}

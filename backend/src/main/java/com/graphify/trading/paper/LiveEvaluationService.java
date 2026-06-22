package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    static final long STALENESS_MINUTES = 10L;

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
            PaperLiveSymbolRepository paperLiveSymbolRepository) {
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
            .filter(r -> RuleStatus.isLiveActive(r.getStatus()))
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

        for (String symbol : symbols) {
            try {
                evaluateSymbol(rule, def, account, symbol, tickTime);
            } catch (Exception e) {
                log.warn("Error evaluating symbol {} for rule {}: {}", symbol, rule.getId(), e.getMessage());
            }
        }

        // Equity snapshot after all symbols evaluated for this rule/account
        saveEquitySnapshot(account, tickTime);
    }

    private void evaluateSymbol(TradingRule rule, RuleDefinition def,
                                PaperAccount account, String symbol, Instant tickTime) {
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
            // Already holding — check exit
            double entryPrice = posOpt.get().getAvgPrice().doubleValue();
            EvalResult exitResult = ruleEvaluator.evalExit(def.exit(), closes, volumes, last, entryPrice);
            signal = exitResult.triggered() ? Signal.SELL : Signal.HOLD;
            if (exitResult.triggered()) {
                snapshotJson = mergeRationale(indicatorJson, exitResult, "SELL");
            }
        } else {
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

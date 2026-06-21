package com.graphify.trading.backtest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.common.dto.ApiResponse;
import com.graphify.common.exception.GraphifyException;
import com.graphify.history.HistoryService;
import com.graphify.trading.backtest.dto.BacktestRequest;
import com.graphify.trading.backtest.dto.BacktestResult;
import com.graphify.trading.engine.Bar;
import com.graphify.trading.engine.FillSimulator;
import com.graphify.trading.engine.MarketDataPort;
import com.graphify.trading.engine.PaperLedger;
import com.graphify.trading.engine.RuleEvaluator;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import com.graphify.trading.rule.definition.RuleDefinition;
import com.graphify.trading.rule.definition.RuleDefinitionValidator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BacktestService {

    private static final double DEFAULT_CASH = 10_000_000;

    private final TradingRuleRepository ruleRepository;
    private final RuleDefinitionValidator validator;
    private final ObjectMapper objectMapper;
    private final MarketDataPort marketData;
    private final RuleEvaluator evaluator;
    private final FillSimulator fillSimulator;
    private final IntradayBacktestEngine intradayEngine;

    public BacktestService(
            TradingRuleRepository ruleRepository,
            RuleDefinitionValidator validator,
            ObjectMapper objectMapper,
            MarketDataPort marketData,
            RuleEvaluator evaluator,
            FillSimulator fillSimulator,
            IntradayBacktestEngine intradayEngine
    ) {
        this.ruleRepository = ruleRepository;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.marketData = marketData;
        this.evaluator = evaluator;
        this.fillSimulator = fillSimulator;
        this.intradayEngine = intradayEngine;
    }

    @Transactional
    public ApiResponse<BacktestResult> run(BacktestRequest request) {
        RuleDefinition def = resolveDefinition(request);
        double initialCash = request.initialCash() != null && request.initialCash() > 0
                ? request.initialCash()
                : DEFAULT_CASH;

        List<String> allSymbols = resolveInitialSymbols(def);

        // Load daily close data for volume_top_n symbol resolution
        Map<String, double[]> closesBySymbol = new LinkedHashMap<>();
        Map<String, Map<LocalDate, Integer>> indexBySymbol = new HashMap<>();

        for (String symbol : allSymbols) {
            List<Bar> bars = filterRange(marketData.historicalDailyBars(symbol), request.from(), request.to());
            if (bars.isEmpty()) {
                continue;
            }
            double[] closes = new double[bars.size()];
            Map<LocalDate, Integer> idx = new HashMap<>();
            for (int i = 0; i < bars.size(); i++) {
                closes[i] = bars.get(i).close();
                idx.put(bars.get(i).date(), i);
            }
            closesBySymbol.put(symbol, closes);
            indexBySymbol.put(symbol, idx);
        }

        PaperLedger ledger = new PaperLedger(initialCash, fillSimulator);

        // Delegate to intraday engine (5m is now the standard mode for Phase 1+)
        BacktestResult result = intradayEngine.run(
                request,
                def,
                allSymbols,
                (d, date) -> resolveSymbolsForDate(d, date, closesBySymbol, indexBySymbol),
                ledger
        );

        // Mark rule as backtested so lifecycle promote becomes available (Phase 4)
        if (request.ruleId() != null) {
            Long userId = HistoryService.requireCurrentUserId();
            ruleRepository.findByIdAndUserId(request.ruleId(), userId).ifPresent(rule -> {
                if (!rule.isBacktested()) {
                    rule.setBacktested(true);
                    ruleRepository.save(rule);
                }
            });
        }

        return ApiResponse.ok(result);
    }

    private List<Bar> filterRange(List<Bar> bars, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return bars;
        }
        List<Bar> out = new ArrayList<>();
        for (Bar b : bars) {
            if (from != null && b.date().isBefore(from)) {
                continue;
            }
            if (to != null && b.date().isAfter(to)) {
                continue;
            }
            out.add(b);
        }
        return out;
    }

    /**
     * 데이터 로드용 초기 종목 결정.
     * - symbols 타입: 유니버스 종목 목록 그대로
     * - volume_top_n 타입: 해당 시장의 KOSPI 200 전체 종목 + additionalSymbols
     *   (날짜 루프에서 거래량 상위 N종목만 평가하지만, 데이터는 미리 전체 로드해야 함)
     */
    private List<String> resolveInitialSymbols(RuleDefinition def) {
        RuleDefinition.Universe u = def.universe();
        if (u == null) {
            throw new GraphifyException(
                    "ERR_BACKTEST_002", "유니버스가 없습니다.", HttpStatus.BAD_REQUEST);
        }

        if ("volume_top_n".equals(u.type())) {
            String market = u.market() != null ? u.market() : "KOSPI";
            Set<String> all = new LinkedHashSet<>(marketData.symbolsByMarket(market));
            if (u.additionalSymbols() != null) {
                all.addAll(u.additionalSymbols());
            }
            if (all.isEmpty()) {
                throw new GraphifyException(
                        "ERR_BACKTEST_002",
                        "volume_top_n 유니버스에 수집된 종목이 없습니다. "
                        + "먼저 ingestDailyForKospi200()를 실행하세요.",
                        HttpStatus.BAD_REQUEST);
            }
            return new ArrayList<>(all);
        }

        // symbols / watchlist 타입
        if (u.symbols() == null || u.symbols().isEmpty()) {
            throw new GraphifyException(
                    "ERR_BACKTEST_002", "유니버스 종목이 비어 있습니다.", HttpStatus.BAD_REQUEST);
        }
        return u.symbols();
    }

    /**
     * 해당 날짜의 실제 평가 종목 결정.
     * - symbols/watchlist 타입: closesBySymbol의 모든 종목(date에 데이터 있는 것)
     * - volume_top_n 타입: marketData.topVolumeSymbols(date, topN) + additionalSymbols
     *   (단, closesBySymbol에 데이터가 있는 종목만)
     */
    private List<String> resolveSymbolsForDate(
            RuleDefinition def, LocalDate date,
            Map<String, double[]> closesBySymbol,
            Map<String, Map<LocalDate, Integer>> indexBySymbol) {

        RuleDefinition.Universe u = def.universe();
        if ("volume_top_n".equals(u.type())) {
            int topN = u.topN() != null ? u.topN() : 10;
            List<String> dynamic = marketData.topVolumeSymbols(date, topN);
            Set<String> result = new LinkedHashSet<>(dynamic);
            if (u.additionalSymbols() != null) {
                result.addAll(u.additionalSymbols());
            }
            return result.stream()
                    .filter(s -> closesBySymbol.containsKey(s)
                            && indexBySymbol.get(s).containsKey(date))
                    .toList();
        }
        // symbols/watchlist 타입: 기존 방식 — closesBySymbol 전체 키 중 해당 날짜 데이터 있는 종목
        return closesBySymbol.keySet().stream()
                .filter(s -> indexBySymbol.get(s).containsKey(date))
                .toList();
    }

    private RuleDefinition resolveDefinition(BacktestRequest request) {
        JsonNode node;
        if (request.ruleId() != null) {
            Long userId = HistoryService.requireCurrentUserId();
            TradingRule rule = ruleRepository.findByIdAndUserId(request.ruleId(), userId)
                    .orElseThrow(() -> new GraphifyException(
                            "ERR_RULE_002", "룰을 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
            try {
                node = objectMapper.readTree(rule.getDefinition());
            } catch (Exception e) {
                throw new GraphifyException(
                        "ERR_BACKTEST_003", "룰 정의를 읽을 수 없습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else if (request.definition() != null && !request.definition().isNull()) {
            node = request.definition();
        } else {
            throw new GraphifyException(
                    "ERR_BACKTEST_004", "ruleId 또는 definition 중 하나가 필요합니다.", HttpStatus.BAD_REQUEST);
        }
        RuleDefinition def;
        try {
            def = objectMapper.treeToValue(node, RuleDefinition.class);
        } catch (Exception e) {
            throw new GraphifyException(
                    "ERR_BACKTEST_005", "룰 정의 형식이 올바르지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        validator.validate(def);
        return def;
    }
}

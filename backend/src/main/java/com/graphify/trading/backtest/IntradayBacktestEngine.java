package com.graphify.trading.backtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.company.Company;
import com.graphify.company.CompanyRepository;
import com.graphify.company.market.IntradayBar;
import com.graphify.company.market.YahooFinanceChartClient;
import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
import com.graphify.trading.backtest.dto.BacktestRequest;
import com.graphify.trading.backtest.dto.BacktestResult;
import com.graphify.trading.engine.EvalResult;
import com.graphify.trading.engine.FillSimulator;
import com.graphify.trading.engine.PaperLedger;
import com.graphify.trading.engine.RuleEvaluator;
import com.graphify.trading.engine.Signal;
import com.graphify.trading.rule.definition.RuleDefinition;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 5분봉 인트라데이 백테스트 엔진.
 * DB 캐시(market_bars_intraday) 우선 조회, 미스 시 Yahoo Finance fallback + 저장.
 */
@Component
public class IntradayBacktestEngine {

    private static final Logger log = LoggerFactory.getLogger(IntradayBacktestEngine.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final double DEFAULT_CASH = 10_000_000.0;

    private final MarketBarIntradayRepository intradayRepository;
    private final YahooFinanceChartClient yahooClient;
    private final RuleEvaluator ruleEvaluator;
    private final FillSimulator fillSimulator;
    private final IntradayBarCacheService cacheService;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    public IntradayBacktestEngine(
            MarketBarIntradayRepository intradayRepository,
            YahooFinanceChartClient yahooClient,
            RuleEvaluator ruleEvaluator,
            FillSimulator fillSimulator,
            IntradayBarCacheService cacheService,
            CompanyRepository companyRepository,
            ObjectMapper objectMapper
    ) {
        this.intradayRepository = intradayRepository;
        this.yahooClient = yahooClient;
        this.ruleEvaluator = ruleEvaluator;
        this.fillSimulator = fillSimulator;
        this.cacheService = cacheService;
        this.companyRepository = companyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 5분봉 인트라데이 백테스트 실행.
     *
     * @param request        백테스트 요청 (from, to, timeFrom, timeTo, initialCash)
     * @param def            룰 정의
     * @param allSymbols     초기 종목 목록 (resolveInitialSymbols 결과)
     * @param symbolResolver 날짜별 평가 종목 결정 함수 (def, date) → symbols
     * @param ledger         초기화된 PaperLedger (초기 현금 설정됨)
     */
    @Transactional
    public BacktestResult run(
            BacktestRequest request,
            RuleDefinition def,
            List<String> allSymbols,
            BiFunction<RuleDefinition, LocalDate, List<String>> symbolResolver,
            PaperLedger ledger
    ) {
        double initialCash = request.initialCash() != null && request.initialCash() > 0
                ? request.initialCash()
                : DEFAULT_CASH;

        LocalTime timeFrom = LocalTime.parse(request.timeFrom() != null ? request.timeFrom() : "09:00");
        LocalTime timeTo = LocalTime.parse(request.timeTo() != null ? request.timeTo() : "12:00");

        LocalDate resolvedTo = request.to() != null ? request.to() : LocalDate.now();
        LocalDate resolvedFrom = request.from() != null ? request.from() : resolvedTo.minusDays(60);
        List<LocalDate> dates = resolvedFrom.datesUntil(resolvedTo.plusDays(1)).toList();
        List<BacktestResult.EquityPoint> curve = new ArrayList<>();
        Map<String, Double> lastPrices = new HashMap<>();
        Map<String, Integer> lastExitIndex = new HashMap<>();
        int cooldown = def.constraints() != null && def.constraints().cooldownBars() != null
                ? def.constraints().cooldownBars() : 0;

        // Bar index counter per symbol across entire run (for cooldown tracking)
        Map<String, Integer> globalBarIndex = new HashMap<>();

        for (LocalDate date : dates) {
            List<String> symbols = symbolResolver.apply(def, date);
            for (String symbol : symbols) {
                List<MarketBarIntraday> bars = loadBars(symbol, date, timeFrom, timeTo);
                if (bars.isEmpty()) {
                    continue;
                }

                double[] closes = bars.stream().mapToDouble(MarketBarIntraday::getClose).toArray();
                Double[] volumes = bars.stream()
                        .map(b -> b.getVolume() != null ? b.getVolume().doubleValue() : null)
                        .toArray(Double[]::new);

                for (int i = 0; i < bars.size(); i++) {
                    MarketBarIntraday bar = bars.get(i);
                    LocalDateTime barDt = bar.getTs().atZone(KST).toLocalDateTime();

                    int gIdx = globalBarIndex.getOrDefault(symbol, 0);
                    globalBarIndex.put(symbol, gIdx + 1);

                    lastPrices.put(symbol, closes[i]);

                    if (ledger.holds(symbol)) {
                        double entryPrice = ledger.position(symbol).avgPrice();
                        EvalResult exitResult = ruleEvaluator.evalExit(def.exit(), closes, volumes, i, entryPrice);
                        if (exitResult.triggered()) {
                            String rationaleJson = buildRationale(exitResult, "SELL");
                            ledger.sell(date, symbol, closes[i], rationaleJson);
                            lastExitIndex.put(symbol, gIdx);
                        }
                    } else {
                        Integer exitedAt = lastExitIndex.get(symbol);
                        if (exitedAt != null && gIdx - exitedAt <= cooldown) {
                            // in cooldown — skip
                        } else {
                            EvalResult entryResult = ruleEvaluator.evalEntry(def.entry(), closes, volumes, i);
                            if (entryResult.triggered()) {
                                String rationaleJson = buildRationale(entryResult, "BUY");
                                double qty = sizeQty(def.sizing(), ledger.cash(), closes[i]);
                                ledger.buy(date, symbol, qty, closes[i], rationaleJson);
                            }
                        }
                    }

                    curve.add(new BacktestResult.EquityPoint(barDt, ledger.equity(lastPrices)));
                }
            }
        }

        return buildResult(initialCash, ledger, curve);
    }

    /**
     * DB 캐시에서 5분봉 로드. 없으면 Yahoo Finance fallback → DB 저장.
     */
    private List<MarketBarIntraday> loadBars(
            String symbol, LocalDate date, LocalTime timeFrom, LocalTime timeTo) {

        // 1. DB range query (KST 날 시작~끝)
        Instant fromInstant = date.atStartOfDay(KST).toInstant();
        Instant toInstant = date.atTime(23, 59, 59).atZone(KST).toInstant();

        List<MarketBarIntraday> dbBars = intradayRepository.findBySymbolAndRange(symbol, fromInstant, toInstant);

        // 2. Filter by KST time window
        List<MarketBarIntraday> filtered = dbBars.stream()
                .filter(b -> {
                    LocalTime t = b.getTs().atZone(KST).toLocalTime();
                    return !t.isBefore(timeFrom) && !t.isAfter(timeTo);
                })
                .toList();

        if (!dbBars.isEmpty()) {
            // Day already in DB — return whatever matches the window (may be empty)
            return filtered;
        }

        // 3. Yahoo Finance fallback
        log.debug("DB miss for symbol={} date={}, fetching from Yahoo", symbol, date);
        String yahooSymbol = symbol.endsWith(".KS") ? symbol : symbol + ".KS";
        List<IntradayBar> fetched = yahooClient.fetchIntradayForDateRange(yahooSymbol, date, date);

        if (fetched.isEmpty()) {
            return List.of();
        }

        // 4. Convert — filter to requested date only to avoid duplicate key conflicts
        // Yahoo returns a fixed recent window (e.g. last 5 days) regardless of requested date.
        List<MarketBarIntraday> toSave = fetched.stream()
                .filter(bar -> bar.ts().atZone(KST).toLocalDate().equals(date))
                .map(bar -> new MarketBarIntraday(
                        symbol,
                        bar.ts(),
                        "5m",
                        bar.open(),
                        bar.high(),
                        bar.low(),
                        bar.close(),
                        bar.volume(),
                        "YAHOO"
                ))
                .toList();

        // Save in a separate transaction so a conflict doesn't corrupt the outer backtest tx.
        cacheService.saveQuietly(symbol, date, toSave);

        // 5. Filter saved bars by time window
        return toSave.stream()
                .filter(b -> {
                    LocalTime t = b.getTs().atZone(KST).toLocalTime();
                    return !t.isBefore(timeFrom) && !t.isAfter(timeTo);
                })
                .toList();
    }

    private BacktestResult buildResult(
            double initialCash,
            PaperLedger ledger,
            List<BacktestResult.EquityPoint> curve
    ) {
        double finalEquity = curve.isEmpty() ? initialCash : curve.get(curve.size() - 1).equity();
        double returnPct = (finalEquity - initialCash) / initialCash * 100.0;

        // Max drawdown
        double peak = Double.NEGATIVE_INFINITY;
        double maxDd = 0;
        for (BacktestResult.EquityPoint p : curve) {
            peak = Math.max(peak, p.equity());
            if (peak > 0) {
                maxDd = Math.max(maxDd, (peak - p.equity()) / peak * 100.0);
            }
        }

        // Symbol → company name lookup (batch, one query per unique symbol)
        Map<String, String> nameBySymbol = new HashMap<>();
        for (PaperLedger.TradeRecord t : ledger.trades()) {
            nameBySymbol.computeIfAbsent(t.symbol(), sym ->
                companyRepository.findByTicker(sym).map(Company::getName).orElse(null));
        }

        // Trades
        int wins = 0;
        int sells = 0;
        List<BacktestResult.TradeDto> trades = new ArrayList<>();
        for (PaperLedger.TradeRecord t : ledger.trades()) {
            LocalDateTime dt = t.date().atStartOfDay(); // date-only → use start of day
            trades.add(new BacktestResult.TradeDto(dt, t.symbol(), nameBySymbol.get(t.symbol()),
                    t.side(), t.qty(), t.price(), t.pnl(), t.rationaleJson()));
            if ("SELL".equals(t.side())) {
                sells++;
                if (t.pnl() != null && t.pnl() > 0) {
                    wins++;
                }
            }
        }
        double winRate = sells > 0 ? (double) wins / sells * 100.0 : 0;

        double sharpeRatio = computeSharpeRatio(curve);
        double sortinoRatio = computeSortinoRatio(curve);
        double profitFactor = computeProfitFactor(trades);
        List<BacktestResult.DrawdownSegment> drawdownSegments = computeDrawdownSegments(curve);

        return new BacktestResult(
                initialCash, finalEquity, returnPct, maxDd, winRate, sells,
                sharpeRatio, sortinoRatio, profitFactor, drawdownSegments,
                trades, curve
        );
    }

    // ── Package-private static helpers (testable without Spring context) ──────

    /**
     * Sharpe ratio: (meanReturn / stdDev) * sqrt(9000).
     * Returns 0.0 if stdDev == 0 or fewer than 2 return observations.
     */
    static double computeSharpeRatio(List<BacktestResult.EquityPoint> curve) {
        double[] returns = computeReturns(curve);
        if (returns.length < 2) {
            return 0.0;
        }
        double mean = mean(returns);
        double std = stdDev(returns, mean);
        if (std == 0.0) {
            return 0.0;
        }
        return (mean / std) * Math.sqrt(9000.0);
    }

    /**
     * Sortino ratio: (meanReturn / downsideDev) * sqrt(9000).
     * Returns 0.0 if no downside observations.
     */
    static double computeSortinoRatio(List<BacktestResult.EquityPoint> curve) {
        double[] returns = computeReturns(curve);
        if (returns.length < 2) {
            return 0.0;
        }
        double mean = mean(returns);
        double downsideDev = downsideDeviation(returns);
        if (downsideDev == 0.0) {
            return 0.0;
        }
        return (mean / downsideDev) * Math.sqrt(9000.0);
    }

    /**
     * Profit factor: grossProfit / grossLoss for SELL trades.
     * Returns MAX_VALUE if grossLoss == 0 and grossProfit > 0.
     * Returns 0.0 if no trades.
     */
    static double computeProfitFactor(List<BacktestResult.TradeDto> trades) {
        double grossProfit = 0.0;
        double grossLoss = 0.0;
        for (BacktestResult.TradeDto t : trades) {
            if (!"SELL".equals(t.side()) || t.pnl() == null) {
                continue;
            }
            if (t.pnl() > 0) {
                grossProfit += t.pnl();
            } else if (t.pnl() < 0) {
                grossLoss += Math.abs(t.pnl());
            }
        }
        if (grossProfit == 0.0 && grossLoss == 0.0) {
            return 0.0;
        }
        if (grossLoss == 0.0) {
            return Double.MAX_VALUE;
        }
        return grossProfit / grossLoss;
    }

    /**
     * Computes drawdown segments as monotonic peak-to-trough spans.
     * A segment starts when equity drops below current peak,
     * ends when equity recovers above or equals the peak again.
     */
    static List<BacktestResult.DrawdownSegment> computeDrawdownSegments(
            List<BacktestResult.EquityPoint> curve) {
        List<BacktestResult.DrawdownSegment> segments = new ArrayList<>();
        if (curve.isEmpty()) {
            return segments;
        }
        double peak = curve.get(0).equity();
        LocalDateTime ddStart = null;

        for (BacktestResult.EquityPoint p : curve) {
            if (p.equity() >= peak) {
                if (ddStart != null) {
                    segments.add(new BacktestResult.DrawdownSegment(ddStart, p.datetime()));
                    ddStart = null;
                }
                peak = p.equity();
            } else if (p.equity() < peak && ddStart == null) {
                ddStart = p.datetime();
            }
        }
        if (ddStart != null) {
            segments.add(new BacktestResult.DrawdownSegment(
                    ddStart, curve.get(curve.size() - 1).datetime()));
        }
        return segments;
    }

    // ── Private math helpers ──────────────────────────────────────────────────

    private static double[] computeReturns(List<BacktestResult.EquityPoint> curve) {
        if (curve.size() < 2) {
            return new double[0];
        }
        double[] returns = new double[curve.size() - 1];
        for (int i = 1; i < curve.size(); i++) {
            double prev = curve.get(i - 1).equity();
            double curr = curve.get(i).equity();
            returns[i - 1] = prev != 0 ? (curr - prev) / prev : 0.0;
        }
        return returns;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private static double stdDev(double[] values, double mean) {
        double sumSq = 0.0;
        for (double v : values) {
            double diff = v - mean;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / values.length);
    }

    private static double downsideDeviation(double[] returns) {
        double sumSq = 0.0;
        int count = 0;
        for (double r : returns) {
            if (r < 0) {
                sumSq += r * r;
                count++;
            }
        }
        if (count == 0) {
            return 0.0;
        }
        return Math.sqrt(sumSq / returns.length); // denominator = total N (not just downside N)
    }

    /**
     * EvalResult를 RESEARCH Discretion 2 스키마의 rationale JSON 문자열로 직렬화.
     * {side, exitReason, exitPct, conditions:[{expr,leftLabel,leftValue,op,rightLabel,rightValue,passed}]}
     */
    private String buildRationale(EvalResult r, String side) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("side", side);
        map.put("exitReason", r.exitReason() != null ? r.exitReason().name() : null);
        map.put("exitPct", r.exitPct());
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (EvalResult.ConditionResult c : r.conditions()) {
            Map<String, Object> cMap = new LinkedHashMap<>();
            cMap.put("expr", c.expr());
            cMap.put("leftLabel", c.leftLabel());
            cMap.put("leftValue", c.leftValue());
            cMap.put("op", c.op());
            cMap.put("rightLabel", c.rightLabel());
            cMap.put("rightValue", c.rightValue());
            cMap.put("passed", c.passed());
            conditions.add(cMap);
        }
        map.put("conditions", conditions);
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rationale JSON for side={}", side, e);
            return null;
        }
    }

    private double sizeQty(RuleDefinition.Sizing sizing, double cash, double price) {
        if (sizing == null || price <= 0) {
            return 0;
        }
        double value = sizing.value() == null ? 0 : sizing.value();
        String type = sizing.type() == null ? "cash" : sizing.type().toLowerCase();
        double amount = switch (type) {
            case "percent" -> cash * (value / 100.0);
            case "qty" -> value * price;
            default -> value; // cash
        };
        if ("qty".equals(type)) {
            return Math.floor(value);
        }
        return Math.floor(amount / price);
    }
}

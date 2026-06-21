package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.trading.engine.FillSimulator;
import com.graphify.trading.engine.Signal;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.definition.RuleDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB write-through 가상 체결 실행기.
 * 틱마다: 계좌 로드 → 신호 평가 → 포지션/거래 기록 → 계좌 플러시.
 * @Transactional: 체결 + 포지션 + 계좌 업데이트 원자적 실행.
 */
@Service
public class PaperExecutor implements OrderExecutorPort {

    private static final Logger log = LoggerFactory.getLogger(PaperExecutor.class);

    private final PaperAccountRepository accountRepo;
    private final PaperPositionRepository positionRepo;
    private final PaperTradeRepository tradeRepo;
    private final PaperSignalLogRepository signalLogRepo;
    private final FillSimulator fillSimulator;

    public PaperExecutor(
            PaperAccountRepository accountRepo,
            PaperPositionRepository positionRepo,
            PaperTradeRepository tradeRepo,
            PaperSignalLogRepository signalLogRepo,
            FillSimulator fillSimulator) {
        this.accountRepo = accountRepo;
        this.positionRepo = positionRepo;
        this.tradeRepo = tradeRepo;
        this.signalLogRepo = signalLogRepo;
        this.fillSimulator = fillSimulator;
    }

    @Override
    public boolean supports(TradingRule rule) {
        return "PAPER".equals(rule.getMode());
    }

    @Override
    @Transactional
    public TradeResult execute(Signal signal, TradingRule rule, String symbol,
                               double price, Instant ts, String indicatorSnapshotJson) {
        if (signal == Signal.HOLD) {
            saveSignalLog(rule.getId(), symbol, ts, "HOLD", indicatorSnapshotJson, false);
            return TradeResult.skipped("HOLD", symbol, "HOLD");
        }

        PaperAccount account = accountRepo.findByUserId(rule.getUserId())
            .orElseGet(() -> createDefaultAccount(rule.getUserId()));

        TradeResult result = signal == Signal.BUY
            ? executeBuy(account, rule, symbol, price, ts)
            : executeSell(account, rule, symbol, price, ts);

        saveSignalLog(rule.getId(), symbol, ts, signal.name(), indicatorSnapshotJson, result.executed());
        return result;
    }

    private TradeResult executeBuy(PaperAccount account, TradingRule rule, String symbol,
                                   double price, Instant ts) {
        // Guard: already holds
        if (positionRepo.findByAccountIdAndSymbol(account.getId(), symbol).isPresent()) {
            return TradeResult.skipped("BUY", symbol, "ALREADY_HOLDS");
        }

        double fillPrice = fillSimulator.fillPrice(Signal.BUY, price);
        double allocCash = resolveSizingCash(rule, account.getCash().doubleValue());
        double qty = Math.floor(allocCash / (fillPrice * (1 + FillSimulator.FEE_RATE)));
        if (qty < 1) {
            return TradeResult.skipped("BUY", symbol, "INSUFFICIENT_CASH");
        }
        double cost = fillPrice * qty + fillSimulator.fee(fillPrice, qty);
        if (cost > account.getCash().doubleValue()) {
            return TradeResult.skipped("BUY", symbol, "INSUFFICIENT_CASH");
        }

        // Persist
        BigDecimal bdQty   = BigDecimal.valueOf(qty).setScale(4, RoundingMode.HALF_UP);
        BigDecimal bdPrice = BigDecimal.valueOf(fillPrice).setScale(4, RoundingMode.HALF_UP);
        positionRepo.save(new PaperPosition(account.getId(), symbol, bdQty, bdPrice));
        tradeRepo.save(new PaperTrade(account.getId(), rule.getId(), symbol, "BUY",
            bdQty, bdPrice, null, ts));
        account.setCash(BigDecimal.valueOf(account.getCash().doubleValue() - cost)
            .setScale(4, RoundingMode.HALF_UP));
        accountRepo.save(account);

        log.info("PAPER BUY {} x{} @ {} (rule {})", symbol, qty, fillPrice, rule.getId());
        return TradeResult.filled("BUY", symbol, fillPrice, qty, null);
    }

    private TradeResult executeSell(PaperAccount account, TradingRule rule, String symbol,
                                    double price, Instant ts) {
        PaperPosition pos = positionRepo.findByAccountIdAndSymbol(account.getId(), symbol)
            .orElse(null);
        if (pos == null) {
            return TradeResult.skipped("SELL", symbol, "NO_POSITION");
        }

        double fillPrice = fillSimulator.fillPrice(Signal.SELL, price);
        double qty = pos.getQty().doubleValue();
        double proceeds = fillPrice * qty - fillSimulator.fee(fillPrice, qty);
        double pnl = proceeds - pos.getAvgPrice().doubleValue() * qty;

        // Persist
        BigDecimal bdQty   = BigDecimal.valueOf(qty).setScale(4, RoundingMode.HALF_UP);
        BigDecimal bdPrice = BigDecimal.valueOf(fillPrice).setScale(4, RoundingMode.HALF_UP);
        BigDecimal bdPnl   = BigDecimal.valueOf(pnl).setScale(4, RoundingMode.HALF_UP);
        positionRepo.deleteByAccountIdAndSymbol(account.getId(), symbol);
        tradeRepo.save(new PaperTrade(account.getId(), rule.getId(), symbol, "SELL",
            bdQty, bdPrice, bdPnl, ts));
        account.setCash(BigDecimal.valueOf(account.getCash().doubleValue() + proceeds)
            .setScale(4, RoundingMode.HALF_UP));
        accountRepo.save(account);

        log.info("PAPER SELL {} x{} @ {} pnl={} (rule {})", symbol, qty, fillPrice, pnl, rule.getId());
        return TradeResult.filled("SELL", symbol, fillPrice, qty, pnl);
    }

    /**
     * Sizing 해석: fixed_cash → value원, full_cash → 전액.
     * RuleDefinition 파싱 실패 시 기본 1,000,000원.
     */
    private double resolveSizingCash(TradingRule rule, double availableCash) {
        try {
            ObjectMapper om = new ObjectMapper();
            RuleDefinition def = om.readValue(rule.getDefinition(), RuleDefinition.class);
            if (def.sizing() == null) return availableCash;
            if ("full_cash".equalsIgnoreCase(def.sizing().type())) return availableCash;
            if ("fixed_cash".equalsIgnoreCase(def.sizing().type()) && def.sizing().value() != null) {
                return Math.min(def.sizing().value(), availableCash);
            }
        } catch (Exception e) {
            log.warn("Failed to parse sizing from rule {}: {}", rule.getId(), e.getMessage());
        }
        return 1_000_000.0;
    }

    private void saveSignalLog(Long ruleId, String symbol, Instant ts, String signal,
                               String indicatorJson, boolean executed) {
        try {
            signalLogRepo.save(new PaperSignalLog(ruleId, symbol, ts, signal, indicatorJson, executed));
        } catch (Exception e) {
            log.warn("Failed to save signal log for rule {} symbol {}: {}", ruleId, symbol, e.getMessage());
        }
    }

    @Transactional
    protected PaperAccount createDefaultAccount(Long userId) {
        log.info("Creating default paper account for user {}", userId);
        return accountRepo.save(new PaperAccount(userId, BigDecimal.valueOf(10_000_000)));
    }
}

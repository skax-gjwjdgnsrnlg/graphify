package com.graphify.trading.paper;

import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
import com.graphify.market.SymbolNameService;
import com.graphify.trading.paper.dto.PaperDashboardDto;
import com.graphify.trading.paper.dto.PaperPositionItem;
import com.graphify.trading.rule.TradingRuleRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaperDashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PaperAccountRepository       accountRepo;
    private final PaperPositionRepository      positionRepo;
    private final PaperTradeRepository         tradeRepo;
    private final MarketBarIntradayRepository  intradayRepo;
    private final TradingRuleRepository        ruleRepo;
    private final SymbolNameService            symbolNameService;

    public PaperDashboardService(
            PaperAccountRepository accountRepo,
            PaperPositionRepository positionRepo,
            PaperTradeRepository tradeRepo,
            MarketBarIntradayRepository intradayRepo,
            TradingRuleRepository ruleRepo,
            SymbolNameService symbolNameService) {
        this.accountRepo  = accountRepo;
        this.positionRepo = positionRepo;
        this.tradeRepo    = tradeRepo;
        this.intradayRepo = intradayRepo;
        this.ruleRepo     = ruleRepo;
        this.symbolNameService = symbolNameService;
    }

    public PaperDashboardDto getDashboard(Long userId) {
        Optional<PaperAccount> accountOpt = accountRepo.findByUserId(userId);
        if (accountOpt.isEmpty()) {
            return PaperDashboardDto.empty();
        }
        PaperAccount account = accountOpt.get();
        double cash = account.getCash().doubleValue();

        // Positions marked-to-market
        List<PaperPosition> positions = positionRepo.findByAccountId(account.getId());
        List<PaperPositionItem> positionItems = positions.stream()
            .map(pos -> toPositionItem(pos))
            .toList();

        double totalUnrealizedPnl = positionItems.stream().mapToDouble(PaperPositionItem::unrealizedPnl).sum();
        double totalEquity = cash + positionItems.stream().mapToDouble(PaperPositionItem::marketValue).sum();

        // Today's realized PnL: SELL trades since midnight KST
        Instant todayMidnightKst = LocalDate.now(KST).atStartOfDay(KST).toInstant();
        double todayRealizedPnl = tradeRepo.findByAccountIdOrderByTradedAtDesc(account.getId()).stream()
            .filter(t -> "SELL".equals(t.getSide()))
            .filter(t -> t.getTradedAt() != null && t.getTradedAt().isAfter(todayMidnightKst))
            .mapToDouble(t -> t.getPnl() != null ? t.getPnl().doubleValue() : 0.0)
            .sum();

        // Active PAPER_LIVE rule count
        int activePaperLiveRuleCount = (int) ruleRepo
            .findByUserIdAndModeOrderByUpdatedAtDesc(userId, "PAPER").stream()
            .filter(r -> "PAPER_LIVE".equals(r.getStatus()))
            .count();

        return new PaperDashboardDto(cash, totalEquity, totalUnrealizedPnl,
            todayRealizedPnl, activePaperLiveRuleCount, positionItems);
    }

    private PaperPositionItem toPositionItem(PaperPosition pos) {
        double qty      = pos.getQty().doubleValue();
        double avgPrice = pos.getAvgPrice().doubleValue();
        double markPrice = latestMarkPrice(pos.getSymbol(), avgPrice);
        double marketValue   = qty * markPrice;
        double costBasis     = qty * avgPrice;
        double unrealizedPnl = marketValue - costBasis;
        double unrealizedPnlPct = costBasis > 0 ? unrealizedPnl / costBasis * 100.0 : 0.0;
        String companyName = symbolNameService.resolve(pos.getSymbol());
        return new PaperPositionItem(pos.getSymbol(), companyName, qty, avgPrice, markPrice,
            marketValue, unrealizedPnl, unrealizedPnlPct);
    }

    private double latestMarkPrice(String symbol, double fallback) {
        List<MarketBarIntraday> bars = intradayRepo.findBySymbolAndIntervalOrderByTsAsc(symbol, "5m");
        if (bars.isEmpty()) return fallback;
        return bars.get(bars.size() - 1).getClose();
    }
}

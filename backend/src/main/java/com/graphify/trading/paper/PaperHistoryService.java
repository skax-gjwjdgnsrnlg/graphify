package com.graphify.trading.paper;

import com.graphify.market.SymbolNameService;
import com.graphify.trading.paper.dto.PaperTradeHistoryItem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PaperHistoryService {

    private final PaperAccountRepository    accountRepo;
    private final PaperTradeRepository      tradeRepo;
    private final PaperSignalLogRepository  signalLogRepo;
    private final SymbolNameService         symbolNameService;

    public PaperHistoryService(PaperAccountRepository accountRepo,
                               PaperTradeRepository tradeRepo,
                               PaperSignalLogRepository signalLogRepo,
                               SymbolNameService symbolNameService) {
        this.accountRepo   = accountRepo;
        this.tradeRepo     = tradeRepo;
        this.signalLogRepo = signalLogRepo;
        this.symbolNameService = symbolNameService;
    }

    public List<PaperTradeHistoryItem> getHistory(Long userId) {
        Optional<PaperAccount> accountOpt = accountRepo.findByUserId(userId);
        if (accountOpt.isEmpty()) {
            return List.of();
        }
        PaperAccount account = accountOpt.get();
        List<PaperTrade> trades = tradeRepo.findByAccountIdOrderByTradedAtDesc(account.getId());

        // 종목명 배치 매핑 (고유 symbol만 1회 해석 — companies → Naver 폴백)
        Map<String, String> nameBySymbol = symbolNameService.resolveAll(
                trades.stream().map(PaperTrade::getSymbol).distinct().toList());

        return trades.stream()
                .map(t -> {
                    // JOIN: rule_id + symbol + ts(=traded_at) + signal(=side)
                    // Pitfall 2: signal=side 조건 포함으로 동일 ts BUY+SELL 방어
                    String rationaleJson = null;
                    if (t.getRuleId() != null) {
                        Optional<PaperSignalLog> logOpt = signalLogRepo
                                .findFirstByRuleIdAndSymbolAndTsAndSignal(
                                        t.getRuleId(), t.getSymbol(), t.getTradedAt(), t.getSide());
                        rationaleJson = logOpt.map(PaperSignalLog::getIndicatorSnapshot).orElse(null);
                    }
                    return new PaperTradeHistoryItem(
                            t.getId(),
                            t.getTradedAt(),
                            t.getSymbol(),
                            nameBySymbol.get(t.getSymbol()),
                            t.getSide(),
                            t.getQty().doubleValue(),
                            t.getPrice().doubleValue(),
                            null,  // fee: paper_trades has no fee column
                            t.getPnl() != null ? t.getPnl().doubleValue() : null,
                            rationaleJson);
                })
                .toList();
    }
}

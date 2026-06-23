package com.graphify.trading.paper;

import com.graphify.market.SymbolNameService;
import com.graphify.trading.paper.dto.PaperTradeHistoryItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RULE-09: paper_trades ↔ paper_signal_log JOIN으로 각 거래의 근거 반환 검증.
 */
@ExtendWith(MockitoExtension.class)
class PaperHistoryServiceTest {

    @Mock PaperAccountRepository    accountRepo;
    @Mock PaperTradeRepository      tradeRepo;
    @Mock PaperSignalLogRepository  signalLogRepo;
    @Mock SymbolNameService         symbolNameService;

    PaperHistoryService service;

    static final Long   USER_ID    = 1L;
    static final Long   ACCOUNT_ID = 10L;
    static final Long   RULE_ID    = 42L;
    static final String SYMBOL     = "A005930";
    static final Instant TRADED_AT = Instant.parse("2026-06-22T09:30:00Z");
    static final String SNAPSHOT_WITH_RATIONALE =
            "{\"price\":70000.0,\"rsi14\":28.3,\"sma20\":68000.0," +
            "\"rationale\":{\"side\":\"BUY\",\"exitReason\":null,\"exitPct\":null," +
            "\"conditions\":[{\"expr\":\"RSI(14) < 30\",\"leftLabel\":\"RSI(14)\"," +
            "\"leftValue\":28.3,\"op\":\"<\",\"rightLabel\":\"30.0\",\"rightValue\":30.0,\"passed\":true}]}}";

    @BeforeEach
    void setUp() {
        service = new PaperHistoryService(accountRepo, tradeRepo, signalLogRepo, symbolNameService);
        lenient().when(symbolNameService.resolveAll(any())).thenReturn(Map.of());
    }

    /** 계정 없음 → 빈 리스트 반환 */
    @Test
    void getHistory_noAccount_returnsEmpty() {
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.empty());

        List<PaperTradeHistoryItem> result = service.getHistory(USER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(tradeRepo, signalLogRepo);
    }

    /** 매칭 signal_log 존재 시 rationaleJson이 해당 snapshot과 일치 */
    @Test
    void getHistory_matchingSignalLog_returnsRationale() {
        PaperAccount account = makeAccount();
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        PaperTrade trade = makeTrade("BUY");
        when(tradeRepo.findByAccountIdOrderByTradedAtDesc(ACCOUNT_ID)).thenReturn(List.of(trade));

        PaperSignalLog signalLog = makeSignalLog("BUY", SNAPSHOT_WITH_RATIONALE);
        when(signalLogRepo.findFirstByRuleIdAndSymbolAndTsAndSignal(RULE_ID, SYMBOL, TRADED_AT, "BUY"))
                .thenReturn(Optional.of(signalLog));

        List<PaperTradeHistoryItem> result = service.getHistory(USER_ID);

        assertThat(result).hasSize(1);
        PaperTradeHistoryItem item = result.get(0);
        assertThat(item.rationaleJson()).isEqualTo(SNAPSHOT_WITH_RATIONALE);
        assertThat(item.symbol()).isEqualTo(SYMBOL);
        assertThat(item.side()).isEqualTo("BUY");
    }

    /** 매칭 signal_log 없을 때 rationaleJson = null */
    @Test
    void getHistory_noMatchingSignalLog_rationaleIsNull() {
        PaperAccount account = makeAccount();
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        PaperTrade trade = makeTrade("BUY");
        when(tradeRepo.findByAccountIdOrderByTradedAtDesc(ACCOUNT_ID)).thenReturn(List.of(trade));

        when(signalLogRepo.findFirstByRuleIdAndSymbolAndTsAndSignal(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        List<PaperTradeHistoryItem> result = service.getHistory(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rationaleJson()).isNull();
    }

    /** signal=side 조건: BUY 거래는 BUY 로그만 조회 (동일 ts SELL 로그는 조회 안 함) */
    @Test
    void getHistory_signalMatchesSide_notCrossMatched() {
        PaperAccount account = makeAccount();
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        PaperTrade buyTrade = makeTrade("BUY");
        PaperTrade sellTrade = makeTrade("SELL");
        when(tradeRepo.findByAccountIdOrderByTradedAtDesc(ACCOUNT_ID)).thenReturn(List.of(buyTrade, sellTrade));

        String buySnapshot = "{\"rationale\":{\"side\":\"BUY\"}}";
        String sellSnapshot = "{\"rationale\":{\"side\":\"SELL\"}}";

        PaperSignalLog buyLog  = makeSignalLog("BUY",  buySnapshot);
        PaperSignalLog sellLog = makeSignalLog("SELL", sellSnapshot);

        when(signalLogRepo.findFirstByRuleIdAndSymbolAndTsAndSignal(RULE_ID, SYMBOL, TRADED_AT, "BUY"))
                .thenReturn(Optional.of(buyLog));
        when(signalLogRepo.findFirstByRuleIdAndSymbolAndTsAndSignal(RULE_ID, SYMBOL, TRADED_AT, "SELL"))
                .thenReturn(Optional.of(sellLog));

        List<PaperTradeHistoryItem> result = service.getHistory(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rationaleJson()).isEqualTo(buySnapshot);
        assertThat(result.get(1).rationaleJson()).isEqualTo(sellSnapshot);
    }

    /** ruleId = null 인 거래는 signalLogRepo 조회 생략 */
    @Test
    void getHistory_tradeWithNullRuleId_skipsSignalLogQuery() {
        PaperAccount account = makeAccount();
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        // ruleId=null trade (no-rule trade)
        PaperTrade trade = new PaperTrade(
                ACCOUNT_ID, null, SYMBOL, "BUY",
                BigDecimal.valueOf(100), BigDecimal.valueOf(70_000), null, TRADED_AT);
        when(tradeRepo.findByAccountIdOrderByTradedAtDesc(ACCOUNT_ID)).thenReturn(List.of(trade));

        List<PaperTradeHistoryItem> result = service.getHistory(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rationaleJson()).isNull();
        verifyNoInteractions(signalLogRepo);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private PaperAccount makeAccount() {
        PaperAccount acc = new PaperAccount(USER_ID, BigDecimal.valueOf(10_000_000));
        // Simulate ID assignment (normally done by DB)
        try {
            var f = PaperAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(acc, ACCOUNT_ID);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return acc;
    }

    private PaperTrade makeTrade(String side) {
        return new PaperTrade(
                ACCOUNT_ID, RULE_ID, SYMBOL, side,
                BigDecimal.valueOf(100), BigDecimal.valueOf(70_000), null, TRADED_AT);
    }

    private PaperSignalLog makeSignalLog(String signal, String snapshot) {
        return new PaperSignalLog(RULE_ID, SYMBOL, TRADED_AT, signal, snapshot, true);
    }
}

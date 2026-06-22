package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.rule.PaperLiveSymbolService;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VolumeRankRefresherTest {

    @Mock TradingRuleRepository ruleRepo;
    @Mock PaperLiveSymbolService paperLiveSymbolService;
    @Mock PaperAccountRepository accountRepo;
    @Mock PaperPositionRepository positionRepo;
    @Mock VolumeRankingProvider liveRanking;

    ObjectMapper objectMapper = new ObjectMapper();

    VolumeRankRefresher refresher;

    static final Long VOLUME_RULE_ID = 10L;
    static final Long SYMBOLS_RULE_ID = 20L;
    static final LocalDate TODAY = LocalDate.of(2026, 6, 23);

    /** volume_top_n RUNNING 룰 정의 */
    static final String VOLUME_TOP_N_DEF = """
            {"version":1,"universe":{"type":"volume_top_n","market":"KOSPI","topN":3},
             "entry":{"logic":"AND","conditions":[]},
             "exit":{"takeProfitPct":5.0},"sizing":{"type":"cash","value":1000000},"constraints":null}
            """;

    /** symbols 타입 RUNNING 룰 정의 */
    static final String SYMBOLS_DEF = """
            {"version":1,"universe":{"type":"symbols","symbols":["005930","000660"]},
             "entry":{"logic":"AND","conditions":[]},
             "exit":{"takeProfitPct":5.0},"sizing":{"type":"cash","value":1000000},"constraints":null}
            """;

    private TradingRule makeRule(Long userId, Long ruleId, String definition) {
        TradingRule rule = new TradingRule(userId, "Test", "PAPER", "DRAFT", definition);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("RUNNING");
        try {
            var field = TradingRule.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(rule, ruleId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rule;
    }

    @BeforeEach
    void setUp() {
        refresher = new VolumeRankRefresher(
            ruleRepo, paperLiveSymbolService, accountRepo, positionRepo,
            liveRanking, objectMapper
        );
    }

    /**
     * Test 1 (DATA-06-SC4): RUNNING volume_top_n 룰 + symbols 타입 RUNNING 룰 혼재
     * → volume_top_n 룰만 assignSymbols 호출, symbols 룰은 assignSymbols 미호출 (Pitfall 3)
     */
    @Test
    void refreshIfVolumeTopN_onlyAssignsSymbolsForVolumeTopNRules_notSymbolsTypeRules() {
        TradingRule volumeRule  = makeRule(1L, VOLUME_RULE_ID, VOLUME_TOP_N_DEF);
        TradingRule symbolsRule = makeRule(1L, SYMBOLS_RULE_ID, SYMBOLS_DEF);

        when(ruleRepo.findAll()).thenReturn(List.of(volumeRule, symbolsRule));
        when(liveRanking.topVolume("KOSPI", TODAY, 3, true)).thenReturn(List.of("A", "B", "C"));

        PaperAccount acc = new PaperAccount(1L, BigDecimal.valueOf(10_000_000));
        when(accountRepo.findByUserId(1L)).thenReturn(Optional.of(acc));
        when(positionRepo.findByAccountId(acc.getId())).thenReturn(List.of());

        refresher.refreshIfVolumeTopN(TODAY);

        // volume_top_n 룰: assignSymbols 호출됨
        verify(paperLiveSymbolService).assignSymbols(eq(VOLUME_RULE_ID), any());
        // symbols 타입 룰: assignSymbols 절대 호출 안 됨
        verify(paperLiveSymbolService, never()).assignSymbols(eq(SYMBOLS_RULE_ID), any());
    }

    /**
     * Test 2 (DATA-06-SC5): top-N=[A,B], 보유 포지션=[C](이탈)
     * → assignSymbols(ruleId, {A,B,C}) 호출 (보유 종목 union 보존)
     */
    @Test
    void refreshIfVolumeTopN_mergesHeldPositionSymbols_whenDroppedFromTopN() {
        TradingRule volumeRule = makeRule(1L, VOLUME_RULE_ID, VOLUME_TOP_N_DEF);

        when(ruleRepo.findAll()).thenReturn(List.of(volumeRule));
        when(liveRanking.topVolume("KOSPI", TODAY, 3, true)).thenReturn(List.of("A", "B"));

        PaperAccount acc = new PaperAccount(1L, BigDecimal.valueOf(10_000_000));
        when(accountRepo.findByUserId(1L)).thenReturn(Optional.of(acc));

        // 보유 포지션 C (top-N 이탈 종목)
        PaperPosition posC = new PaperPosition(acc.getId(), "C", BigDecimal.valueOf(100), BigDecimal.valueOf(50000));
        when(positionRepo.findByAccountId(acc.getId())).thenReturn(List.of(posC));

        refresher.refreshIfVolumeTopN(TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(paperLiveSymbolService).assignSymbols(eq(VOLUME_RULE_ID), captor.capture());

        Collection<String> assigned = captor.getValue();
        assertThat(assigned).containsExactlyInAnyOrder("A", "B", "C");
    }

    /**
     * Test 3: top-N과 보유가 겹치면 중복 없이 합집합 (LinkedHashSet)
     */
    @Test
    void refreshIfVolumeTopN_noDuplicates_whenTopNAndPositionsOverlap() {
        TradingRule volumeRule = makeRule(1L, VOLUME_RULE_ID, VOLUME_TOP_N_DEF);

        when(ruleRepo.findAll()).thenReturn(List.of(volumeRule));
        when(liveRanking.topVolume("KOSPI", TODAY, 3, true)).thenReturn(List.of("A", "B", "C"));

        PaperAccount acc = new PaperAccount(1L, BigDecimal.valueOf(10_000_000));
        when(accountRepo.findByUserId(1L)).thenReturn(Optional.of(acc));

        // 보유 포지션 B (top-N과 겹침)
        PaperPosition posB = new PaperPosition(acc.getId(), "B", BigDecimal.valueOf(50), BigDecimal.valueOf(60000));
        when(positionRepo.findByAccountId(acc.getId())).thenReturn(List.of(posB));

        refresher.refreshIfVolumeTopN(TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(paperLiveSymbolService).assignSymbols(eq(VOLUME_RULE_ID), captor.capture());

        Collection<String> assigned = captor.getValue();
        assertThat(assigned).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(assigned).hasSize(3); // no duplicates
    }

    /**
     * Test 4: 계좌 없음/포지션 없음 → top-N만 assign, NPE 없음
     */
    @Test
    void refreshIfVolumeTopN_noAccount_assignsOnlyTopN_withoutNPE() {
        TradingRule volumeRule = makeRule(1L, VOLUME_RULE_ID, VOLUME_TOP_N_DEF);

        when(ruleRepo.findAll()).thenReturn(List.of(volumeRule));
        when(liveRanking.topVolume("KOSPI", TODAY, 3, true)).thenReturn(List.of("X", "Y"));
        when(accountRepo.findByUserId(1L)).thenReturn(Optional.empty()); // 계좌 없음

        refresher.refreshIfVolumeTopN(TODAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(paperLiveSymbolService).assignSymbols(eq(VOLUME_RULE_ID), captor.capture());

        Collection<String> assigned = captor.getValue();
        assertThat(assigned).containsExactlyInAnyOrder("X", "Y");
        // positionRepo never called since no account
        verify(positionRepo, never()).findByAccountId(any());
    }
}

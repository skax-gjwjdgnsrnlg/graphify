package com.graphify.trading.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.company.CompanyRepository;
import com.graphify.market.KrxMarketCalendar;
import com.graphify.market.MarketDataIngestionService;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.rule.PaperLiveSymbolService;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for PaperLifecycleService.stopAllRunningForMarketClose().
 * Verifies that all RUNNING PAPER rules are stopped, their live symbols deactivated,
 * and open runs closed when the scheduler triggers auto-stop on market close.
 */
@ExtendWith(MockitoExtension.class)
class MarketAutoStopTest {

    @Mock private TradingRuleRepository ruleRepo;
    @Mock private PaperLiveSymbolService paperLiveSymbolService;
    @Mock private CompanyRepository companyRepo;
    @Mock private VolumeRankingProvider liveRanking;
    @Mock private MarketDataIngestionService ingestionService;
    @Mock private PaperRunRepository runRepo;
    @Mock private KrxMarketCalendar marketCalendar;

    private PaperLifecycleService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new PaperLifecycleService(
                ruleRepo, objectMapper, paperLiveSymbolService, companyRepo,
                liveRanking, ingestionService, runRepo, marketCalendar);
    }

    @Test
    void stopAllRunning_stopsEachRule_deactivatesSymbols_closesOpenRun_returnsCount() {
        // Given: 2 RUNNING PAPER rules with open runs
        TradingRule rule1 = runningRule(10L);
        TradingRule rule2 = runningRule(20L);
        when(ruleRepo.findByModeAndRunStatus("PAPER", "RUNNING"))
                .thenReturn(List.of(rule1, rule2));
        when(ruleRepo.save(any(TradingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PaperRun openRun1 = new PaperRun(10L, 1L, Instant.now(), null);
        PaperRun openRun2 = new PaperRun(20L, 1L, Instant.now(), null);
        when(runRepo.findFirstByRuleIdAndStatus(10L, "RUNNING")).thenReturn(Optional.of(openRun1));
        when(runRepo.findFirstByRuleIdAndStatus(20L, "RUNNING")).thenReturn(Optional.of(openRun2));
        when(runRepo.save(any(PaperRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        int stopped = service.stopAllRunningForMarketClose();

        // Then: returns count of stopped rules
        assertThat(stopped).isEqualTo(2);

        // Both rules set to STOPPED and saved
        assertThat(rule1.getRunStatus()).isEqualTo("STOPPED");
        assertThat(rule2.getRunStatus()).isEqualTo("STOPPED");
        verify(ruleRepo, times(2)).save(any(TradingRule.class));

        // Live symbols deactivated for each rule
        verify(paperLiveSymbolService).deactivateRule(10L);
        verify(paperLiveSymbolService).deactivateRule(20L);

        // Open runs closed (status=STOPPED, endedAt set)
        assertThat(openRun1.getStatus()).isEqualTo("STOPPED");
        assertThat(openRun1.getEndedAt()).isNotNull();
        assertThat(openRun2.getStatus()).isEqualTo("STOPPED");
        assertThat(openRun2.getEndedAt()).isNotNull();
        verify(runRepo).save(openRun1);
        verify(runRepo).save(openRun2);
    }

    @Test
    void stopAllRunning_returnsZero_whenNoRunningRules() {
        when(ruleRepo.findByModeAndRunStatus("PAPER", "RUNNING")).thenReturn(List.of());

        int stopped = service.stopAllRunningForMarketClose();

        assertThat(stopped).isEqualTo(0);
        verify(paperLiveSymbolService, org.mockito.Mockito.never()).deactivateRule(any());
        verify(runRepo, org.mockito.Mockito.never()).save(any());
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private TradingRule runningRule(Long id) {
        String def = "{\"version\":1,\"universe\":{\"type\":\"symbols\",\"symbols\":[\"005930\"]}," +
                "\"entry\":{\"logic\":\"AND\",\"conditions\":[]},\"exit\":null," +
                "\"sizing\":{\"type\":\"cash\",\"value\":1000000}}";
        TradingRule rule = new TradingRule(1L, "rule-" + id, "PAPER", "ACTIVE", def);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("RUNNING");
        try {
            var field = TradingRule.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(rule, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rule;
    }
}

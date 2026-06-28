package com.graphify.trading.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.company.CompanyRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Phase 6.9 Wave 1 — start/stop run lifecycle unit tests.
 * Covers UIX-02-BE-1, UIX-02-BE-2.
 *
 * Uses "symbols" universe type to bypass liveRanking resolution.
 * 7-arg constructor includes PaperRunRepository (added in Phase 6.9).
 */
@ExtendWith(MockitoExtension.class)
class PaperRunLifecycleServiceTest {

    @Mock private TradingRuleRepository ruleRepo;
    @Mock private PaperLiveSymbolService paperLiveSymbolService;
    @Mock private CompanyRepository companyRepo;
    @Mock private VolumeRankingProvider liveRanking;
    @Mock private MarketDataIngestionService ingestionService;
    @Mock private PaperRunRepository runRepo;

    private PaperLifecycleService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Long USER_ID = 1L;
    private static final Long RULE_ID = 10L;

    // "symbols" type avoids liveRanking call inside resolveSymbols()
    private static final String SYMBOLS_DEF = """
            { "version": 1, "universe": { "type": "symbols", "symbols": ["005930","000660"] },
              "entry": { "logic": "AND", "conditions": [] },
              "exit": { "takeProfitPct": 10.0, "stopLossPct": -5.0 },
              "sizing": { "type": "cash", "value": 1000000 } }
            """;

    @BeforeEach
    void setUp() {
        service = new PaperLifecycleService(
                ruleRepo, objectMapper, paperLiveSymbolService, companyRepo,
                liveRanking, ingestionService, runRepo);
    }

    private TradingRule activeStoppedRule() {
        TradingRule rule = new TradingRule(USER_ID, "run test rule", "PAPER", "ACTIVE", SYMBOLS_DEF);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("STOPPED");
        return rule;
    }

    // ── UIX-02-BE-1: start() creates a RUNNING paper_run row ──────────────────

    @Test
    void start_savesNewPaperRun_withStatusRunning_andCorrectUserIdAndSnapshot() {
        TradingRule rule = activeStoppedRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any(TradingRule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runRepo.save(any(PaperRun.class))).thenAnswer(inv -> inv.getArgument(0));

        service.start(USER_ID, RULE_ID);

        ArgumentCaptor<PaperRun> captor = ArgumentCaptor.forClass(PaperRun.class);
        verify(runRepo).save(captor.capture());
        PaperRun saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getUniverseSnapshot()).contains("005930");
    }

    // ── UIX-02-BE-1 (cont): re-start after stop creates a NEW run (회차 누적) ─

    @Test
    void startStopStart_createsNewRunPerExecution() {
        TradingRule rule = activeStoppedRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any(TradingRule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runRepo.save(any(PaperRun.class))).thenAnswer(inv -> inv.getArgument(0));

        // First start — creates run 1 (rule.runStatus becomes "RUNNING")
        service.start(USER_ID, RULE_ID);

        // Stop — rule is now "RUNNING"; provide an existing run to close
        PaperRun run1 = new PaperRun(RULE_ID, USER_ID, Instant.now(), "[\"005930\"]");
        when(runRepo.findFirstByRuleIdAndStatus(any(), anyString())).thenReturn(Optional.of(run1));
        service.stop(USER_ID, RULE_ID);
        // rule.runStatus is back to "STOPPED" after stop()

        // Second start — creates run 2 (new 회차)
        service.start(USER_ID, RULE_ID);

        // runRepo.save calls: start1(new PaperRun) + stop(update run1) + start2(new PaperRun) = 3
        ArgumentCaptor<PaperRun> captor = ArgumentCaptor.forClass(PaperRun.class);
        verify(runRepo, times(3)).save(captor.capture());
        List<PaperRun> all = captor.getAllValues();
        // Index 0: new run from first start — RUNNING
        assertThat(all.get(0).getStatus()).isEqualTo("RUNNING");
        // Index 1: run1 updated by stop — STOPPED
        assertThat(all.get(1).getStatus()).isEqualTo("STOPPED");
        // Index 2: new run from second start — RUNNING (새 회차)
        assertThat(all.get(2).getStatus()).isEqualTo("RUNNING");
    }

    // ── UIX-02-BE-2: stop() closes the RUNNING run with endedAt set ───────────

    @Test
    void stop_closesRunningRun_withStatusStoppedAndEndedAt() {
        TradingRule rule = activeStoppedRule();
        rule.setRunStatus("RUNNING");
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any(TradingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        PaperRun existingRun = new PaperRun(RULE_ID, USER_ID, Instant.now(), null);
        when(runRepo.findFirstByRuleIdAndStatus(any(), anyString())).thenReturn(Optional.of(existingRun));
        when(runRepo.save(any(PaperRun.class))).thenAnswer(inv -> inv.getArgument(0));

        service.stop(USER_ID, RULE_ID);

        assertThat(existingRun.getStatus()).isEqualTo("STOPPED");
        assertThat(existingRun.getEndedAt()).isNotNull();
        verify(runRepo).save(existingRun);
    }
}

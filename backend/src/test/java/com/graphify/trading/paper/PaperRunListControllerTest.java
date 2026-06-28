package com.graphify.trading.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphify.trading.rule.TradingRule;
import com.graphify.trading.rule.TradingRuleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UIX-02-BE-5: GET /runs 리스트 컬럼 검증.
 *
 * PaperRunListService.listRuns() 를 직접 테스트한다 (컨트롤러는 얇은 래퍼).
 * 검증 항목: realizedPnl, returnPct, tradeCount, finalEquity, status(RUNNING+STOPPED 혼재), runIndex.
 */
@ExtendWith(MockitoExtension.class)
class PaperRunListControllerTest {

    @Mock PaperRunRepository           runRepo;
    @Mock TradingRuleRepository        ruleRepo;
    @Mock PaperTradeRepository         tradeRepo;
    @Mock PaperAccountRepository       accountRepo;
    @Mock PaperEquitySnapshotRepository snapshotRepo;

    PaperRunListService service;

    static final Long USER_ID    = 1L;
    static final Long RULE_ID    = 3L;
    static final Long ACCOUNT_ID = 11L;
    static final Long RUN_ID_1   = 7L;   // STOPPED (1회차)
    static final Long RUN_ID_2   = 8L;   // RUNNING  (2회차)

    static final Instant RUN1_START  = Instant.parse("2026-06-20T00:00:00Z");
    static final Instant RUN1_END    = Instant.parse("2026-06-25T00:00:00Z");
    static final Instant RUN2_START  = Instant.parse("2026-06-26T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = new PaperRunListService(
                runRepo, ruleRepo, tradeRepo, accountRepo, snapshotRepo, new ObjectMapper());
    }

    /**
     * 단일 STOPPED run: realizedPnl, returnPct, tradeCount, finalEquity 컬럼 검증.
     */
    @Test
    void listRuns_stoppedRun_computesAllColumns() {
        PaperRun run = makeRun(RUN_ID_1, RULE_ID, "STOPPED", RUN1_START, RUN1_END, null);
        when(runRepo.findByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of(run));

        TradingRule rule = mock(TradingRule.class);
        when(rule.getName()).thenReturn("RSI 과매도 반등");
        when(ruleRepo.findById(RULE_ID)).thenReturn(Optional.of(rule));

        // Run-scoped trades: 2 BUY (각 100주 × 70,000), 1 SELL (pnl=200,000)
        PaperTrade buy1  = makeTrade(RUN_ID_1, "BUY",  100, 70_000, null);
        PaperTrade buy2  = makeTrade(RUN_ID_1, "BUY",   50, 48_000, null);
        PaperTrade sell1 = makeTrade(RUN_ID_1, "SELL", 100, 72_000, 200_000.0);
        when(tradeRepo.findByRunIdOrderByTradedAtDesc(RUN_ID_1)).thenReturn(List.of(sell1, buy2, buy1));

        // Account (for STOPPED finalEquity via snapshot before endedAt)
        PaperAccount account = mock(PaperAccount.class);
        when(account.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getCash()).thenReturn(BigDecimal.valueOf(5_000_000)); // fallback if no snapshot
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        // Snapshot: latest before endedAt → finalEquity
        PaperEquitySnapshot snap = mock(PaperEquitySnapshot.class);
        when(snap.getTs()).thenReturn(RUN1_END.minusSeconds(1));
        when(snap.getEquity()).thenReturn(BigDecimal.valueOf(5_241_800));
        when(snapshotRepo.findByAccountIdOrderByTsDesc(ACCOUNT_ID)).thenReturn(List.of(snap));

        List<PaperRunListService.RunListItem> result = service.listRuns(USER_ID);

        assertThat(result).hasSize(1);
        var item = result.get(0);

        assertThat(item.runId()).isEqualTo(RUN_ID_1);
        assertThat(item.status()).isEqualTo("STOPPED");
        assertThat(item.ruleName()).isEqualTo("RSI 과매도 반등");
        assertThat(item.runIndex()).isEqualTo(1);   // 최초(유일) run → 1회차

        // realizedPnl = 200,000 (SELL pnl)
        assertThat(item.realizedPnl()).isCloseTo(200_000.0, within(0.01));

        // returnPct = 200,000 / (100*70,000 + 50*48,000) * 100 = 200,000 / 9,400,000 * 100 ≈ 2.128
        double invested = 100.0 * 70_000.0 + 50.0 * 48_000.0;
        assertThat(item.returnPct()).isCloseTo(200_000.0 / invested * 100.0, within(0.01));

        // tradeCount = 2 BUY
        assertThat(item.tradeCount()).isEqualTo(2);

        // finalEquity = snapshot equity before endedAt
        assertThat(item.finalEquity()).isCloseTo(5_241_800.0, within(0.01));
    }

    /**
     * RUNNING + STOPPED 혼재: 2 runs 동일 전략, 회차 번호 검증.
     */
    @Test
    void listRuns_runningAndStopped_correctRunIndex() {
        // 최신 순: RUN_ID_2(RUNNING, 2회차) → RUN_ID_1(STOPPED, 1회차)
        PaperRun run1 = makeRun(RUN_ID_1, RULE_ID, "STOPPED", RUN1_START, RUN1_END, null);
        PaperRun run2 = makeRun(RUN_ID_2, RULE_ID, "RUNNING", RUN2_START, null, null);
        when(runRepo.findByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of(run2, run1));

        TradingRule rule = mock(TradingRule.class);
        when(rule.getName()).thenReturn("테스트 전략");
        when(ruleRepo.findById(RULE_ID)).thenReturn(Optional.of(rule));

        // Both runs have empty trades (minimal setup)
        when(tradeRepo.findByRunIdOrderByTradedAtDesc(any())).thenReturn(List.of());

        // Account for RUNNING finalEquity via latest snapshot
        PaperAccount account = mock(PaperAccount.class);
        when(account.getId()).thenReturn(ACCOUNT_ID);
        lenient().when(account.getCash()).thenReturn(BigDecimal.valueOf(5_000_000)); // fallback if no snapshot
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));

        PaperEquitySnapshot snap = mock(PaperEquitySnapshot.class);
        when(snap.getTs()).thenReturn(Instant.now());
        when(snap.getEquity()).thenReturn(BigDecimal.valueOf(5_100_000));
        when(snapshotRepo.findByAccountIdOrderByTsDesc(ACCOUNT_ID)).thenReturn(List.of(snap));

        List<PaperRunListService.RunListItem> result = service.listRuns(USER_ID);

        assertThat(result).hasSize(2);

        // 최신 순 반환: run2(RUNNING) 먼저
        var running = result.get(0);
        var stopped = result.get(1);

        assertThat(running.status()).isEqualTo("RUNNING");
        assertThat(running.runIndex()).isEqualTo(2);   // startedAt ASC 기준 2번째

        assertThat(stopped.status()).isEqualTo("STOPPED");
        assertThat(stopped.runIndex()).isEqualTo(1);   // 1회차
    }

    /**
     * 빈 runs → 빈 리스트 반환 (NPE 없음).
     */
    @Test
    void listRuns_noRuns_returnsEmpty() {
        when(runRepo.findByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of());

        List<PaperRunListService.RunListItem> result = service.listRuns(USER_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(ruleRepo, tradeRepo, accountRepo, snapshotRepo);
    }

    /**
     * tradeCount = BUY 횟수 (SELL 제외), realizedPnl = SELL pnl 합산.
     */
    @Test
    void listRuns_stoppedRun_tradeCountIsBuyOnly() {
        PaperRun run = makeRun(RUN_ID_1, RULE_ID, "STOPPED", RUN1_START, RUN1_END, null);
        when(runRepo.findByUserIdOrderByStartedAtDesc(USER_ID)).thenReturn(List.of(run));

        TradingRule rule = mock(TradingRule.class);
        when(rule.getName()).thenReturn("전략A");
        when(ruleRepo.findById(RULE_ID)).thenReturn(Optional.of(rule));

        // 3 BUY, 2 SELL
        PaperTrade b1 = makeTrade(RUN_ID_1, "BUY",  100, 70_000, null);
        PaperTrade b2 = makeTrade(RUN_ID_1, "BUY",   50, 68_000, null);
        PaperTrade b3 = makeTrade(RUN_ID_1, "BUY",   80, 65_000, null);
        PaperTrade s1 = makeTrade(RUN_ID_1, "SELL", 100, 73_000, 300_000.0);
        PaperTrade s2 = makeTrade(RUN_ID_1, "SELL",  50, 71_000, 150_000.0);
        when(tradeRepo.findByRunIdOrderByTradedAtDesc(RUN_ID_1))
                .thenReturn(List.of(s2, s1, b3, b2, b1));

        PaperAccount account = mock(PaperAccount.class);
        when(account.getId()).thenReturn(ACCOUNT_ID);
        when(account.getCash()).thenReturn(BigDecimal.valueOf(5_000_000));
        when(accountRepo.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(snapshotRepo.findByAccountIdOrderByTsDesc(ACCOUNT_ID)).thenReturn(List.of());

        List<PaperRunListService.RunListItem> result = service.listRuns(USER_ID);

        assertThat(result).hasSize(1);
        var item = result.get(0);
        assertThat(item.tradeCount()).isEqualTo(3);              // BUY 3개
        assertThat(item.realizedPnl()).isCloseTo(450_000.0, within(0.01));  // 300k+150k
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private PaperRun makeRun(Long id, Long ruleId, String status,
                             Instant startedAt, Instant endedAt, String universe) {
        PaperRun run = mock(PaperRun.class);
        lenient().when(run.getId()).thenReturn(id);
        lenient().when(run.getRuleId()).thenReturn(ruleId);
        lenient().when(run.getStatus()).thenReturn(status);
        lenient().when(run.getStartedAt()).thenReturn(startedAt);
        lenient().when(run.getEndedAt()).thenReturn(endedAt);
        lenient().when(run.getUniverseSnapshot()).thenReturn(universe);
        return run;
    }

    private PaperTrade makeTrade(Long runId, String side, int qty, double price, Double pnl) {
        PaperTrade t = new PaperTrade(
                ACCOUNT_ID, RULE_ID, runId,
                "A005930", side,
                BigDecimal.valueOf(qty),
                BigDecimal.valueOf(price),
                pnl != null ? BigDecimal.valueOf(pnl) : null,
                Instant.now());
        return t;
    }
}

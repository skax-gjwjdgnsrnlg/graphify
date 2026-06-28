package com.graphify.trading.rule;

import com.graphify.common.exception.GraphifyException;
import com.graphify.company.Company;
import com.graphify.company.CompanyRepository;
import com.graphify.market.KrxMarketCalendar;
import com.graphify.market.MarketDataIngestionService;
import com.graphify.market.volume.VolumeRankingProvider;
import com.graphify.trading.paper.PaperLifecycleService;
import com.graphify.trading.paper.PaperRunRepository;
import com.graphify.trading.rule.dto.RuleResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RULE-08 전이 가드 + ACTIVE 필터 검증.
 */
@ExtendWith(MockitoExtension.class)
class PaperLifecycleServiceTest {

    @Mock TradingRuleRepository ruleRepo;
    @Mock PaperLiveSymbolService paperLiveSymbolService;
    @Mock CompanyRepository companyRepo;
    @Mock VolumeRankingProvider liveRanking;
    @Mock MarketDataIngestionService ingestionService;
    @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock PaperRunRepository runRepo;
    @Mock KrxMarketCalendar marketCalendar;

    PaperLifecycleService service;

    static final Long USER_ID = 1L;
    static final Long RULE_ID = 42L;
    static final String DEFINITION = "{\"version\":1,\"universe\":{\"type\":\"symbols\",\"symbols\":[\"A005930\"]}," +
        "\"entry\":{\"logic\":\"AND\",\"conditions\":[]},\"exit\":null," +
        "\"sizing\":{\"type\":\"fixed_cash\",\"value\":10000000},\"constraints\":null}";

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
            .when(marketCalendar.isOperatingWindowOpen(org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        service = new PaperLifecycleService(
            ruleRepo,
            new com.fasterxml.jackson.databind.ObjectMapper(),
            paperLiveSymbolService,
            companyRepo,
            liveRanking,
            ingestionService,
            runRepo,
            marketCalendar
        );
    }

    // ─── activate ───────────────────────────────────────────────────────────────

    @Test
    void activate_draft_rule_succeeds_without_backtest() {
        // RULE-08: DRAFT 룰은 백테스트 없이 ACTIVE로 전환 가능 (RULE-01 게이트 폐지)
        TradingRule rule = draftRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse resp = service.activate(USER_ID, RULE_ID);

        assertThat(resp.configStatus()).isEqualTo("ACTIVE");
        assertThat(rule.getConfigStatus()).isEqualTo("ACTIVE");
        // 백테스트 안 했어도 예외 없음
        assertThat(rule.isBacktested()).isFalse();
    }

    @Test
    void activate_non_draft_throws() {
        TradingRule rule = activeRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.activate(USER_ID, RULE_ID))
            .isInstanceOf(GraphifyException.class)
            .hasMessageContaining("DRAFT");
    }

    // ─── deactivate ─────────────────────────────────────────────────────────────

    @Test
    void deactivate_active_stopped_rule_succeeds() {
        TradingRule rule = activeRule(); // configStatus=ACTIVE, runStatus=STOPPED
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse resp = service.deactivate(USER_ID, RULE_ID);

        assertThat(resp.configStatus()).isEqualTo("DRAFT");
    }

    @Test
    void deactivate_running_rule_throws() {
        // RULE-08: RUNNING 중인 룰은 하향 차단
        TradingRule rule = activeRule();
        rule.setRunStatus("RUNNING");
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.deactivate(USER_ID, RULE_ID))
            .isInstanceOf(GraphifyException.class)
            .hasMessageContaining("RUNNING");
    }

    // ─── start ──────────────────────────────────────────────────────────────────

    @Test
    void start_active_stopped_rule_sets_running() {
        TradingRule rule = activeRule(); // configStatus=ACTIVE, runStatus=STOPPED
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse resp = service.start(USER_ID, RULE_ID);

        assertThat(resp.runStatus()).isEqualTo("RUNNING");
        assertThat(resp.configStatus()).isEqualTo("ACTIVE");
        verify(paperLiveSymbolService).assignSymbols(eq(RULE_ID), any());
    }

    @Test
    void start_draft_rule_throws() {
        TradingRule rule = draftRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.start(USER_ID, RULE_ID))
            .isInstanceOf(GraphifyException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void start_volume_top_n_resolves_live_topN_and_eager_ingests() {
        // volume_top_n: 라이브 거래대금 상위 topN으로 선정 후 즉시 수집(upsert)하고 assign한다.
        TradingRule rule = volumeTopNRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(liveRanking.topVolume(eq("KOSPI"), any(LocalDate.class), eq(10), eq(true)))
            .thenReturn(List.of("005930", "000660"));

        RuleResponse resp = service.start(USER_ID, RULE_ID);

        assertThat(resp.runStatus()).isEqualTo("RUNNING");
        // 즉시 수집(upsert) — 선정된 각 종목에 대해 일봉/분봉 적재 호출
        verify(ingestionService).ingestDailyInNewTx("005930");
        verify(ingestionService).ingestDailyInNewTx("000660");
        verify(ingestionService).ingestIntradayInNewTx("005930", "5m", "1d");
        verify(ingestionService).ingestIntradayInNewTx("000660", "5m", "1d");
        // companies 폴백은 호출되지 않음 (라이브 랭킹 성공)
        verify(companyRepo, never()).findByInKospi200True();
        org.mockito.ArgumentCaptor<List<String>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(paperLiveSymbolService).assignSymbols(eq(RULE_ID), captor.capture());
        assertThat(captor.getValue()).containsExactly("005930", "000660");
    }

    @Test
    @SuppressWarnings("unchecked")
    void start_volume_top_n_falls_back_to_kospi200_when_live_ranking_empty() {
        // 장외/조회 실패로 라이브 랭킹이 비면 companies(in_kospi200) 후보군으로 폴백해 시작은 가능.
        TradingRule rule = volumeTopNRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Company c1 = company("005930");
        Company c2 = company("000660");
        when(liveRanking.topVolume(eq("KOSPI"), any(LocalDate.class), eq(10), eq(true)))
            .thenReturn(List.of());
        when(companyRepo.findByInKospi200True()).thenReturn(List.of(c1, c2));

        RuleResponse resp = service.start(USER_ID, RULE_ID);

        assertThat(resp.runStatus()).isEqualTo("RUNNING");
        org.mockito.ArgumentCaptor<List<String>> captor =
            org.mockito.ArgumentCaptor.forClass(List.class);
        verify(paperLiveSymbolService).assignSymbols(eq(RULE_ID), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("005930", "000660");
    }

    @Test
    void start_volume_top_n_throws_when_live_and_fallback_both_empty() {
        // 라이브 랭킹도 비고 companies 시드도 없으면 명시적 가드로 차단.
        TradingRule rule = volumeTopNRule();
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(liveRanking.topVolume(eq("KOSPI"), any(LocalDate.class), eq(10), eq(true)))
            .thenReturn(List.of());
        when(companyRepo.findByInKospi200True()).thenReturn(List.of());

        assertThatThrownBy(() -> service.start(USER_ID, RULE_ID))
            .isInstanceOf(GraphifyException.class)
            .hasMessageContaining("종목을 직접 선택하세요");
        verify(paperLiveSymbolService, never()).assignSymbols(any(), any());
        verify(ingestionService, never()).ingestDailyInNewTx(any());
    }

    // ─── stop ───────────────────────────────────────────────────────────────────

    @Test
    void stop_running_rule_returns_active_stopped() {
        // RULE-08: stop 후 config는 ACTIVE 유지 (PAUSED 없음)
        TradingRule rule = activeRule();
        rule.setRunStatus("RUNNING");
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RuleResponse resp = service.stop(USER_ID, RULE_ID);

        assertThat(resp.runStatus()).isEqualTo("STOPPED");
        assertThat(resp.configStatus()).isEqualTo("ACTIVE"); // config 유지
        verify(paperLiveSymbolService).deactivateRule(RULE_ID);
    }

    @Test
    void stop_non_running_rule_throws() {
        TradingRule rule = activeRule(); // runStatus=STOPPED already
        when(ruleRepo.findByIdAndUserId(RULE_ID, USER_ID)).thenReturn(Optional.of(rule));

        assertThatThrownBy(() -> service.stop(USER_ID, RULE_ID))
            .isInstanceOf(GraphifyException.class)
            .hasMessageContaining("RUNNING");
    }

    // ─── listActive ─────────────────────────────────────────────────────────────

    @Test
    void listActive_returns_only_active_rules_not_draft() {
        // RULE-08: 전략 운영 화면은 configStatus=ACTIVE 룰만 반환
        TradingRule activeRule = activeRule();
        TradingRule draftRule = draftRule();
        draftRule.setRunStatus("STOPPED");

        when(ruleRepo.findByUserIdAndModeOrderByUpdatedAtDesc(USER_ID, "PAPER"))
            .thenReturn(List.of(activeRule, draftRule));

        List<RuleResponse> result = service.listActive(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).configStatus()).isEqualTo("ACTIVE");
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private TradingRule draftRule() {
        TradingRule rule = new TradingRule(USER_ID, "Test Rule", "PAPER", "DRAFT", DEFINITION);
        setId(rule, RULE_ID);
        rule.setConfigStatus("DRAFT");
        rule.setRunStatus("STOPPED");
        return rule;
    }

    private TradingRule activeRule() {
        TradingRule rule = new TradingRule(USER_ID, "Test Rule", "PAPER", "DRAFT", DEFINITION);
        setId(rule, RULE_ID);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("STOPPED");
        return rule;
    }

    private TradingRule volumeTopNRule() {
        String def = "{\"version\":1,\"universe\":{\"type\":\"volume_top_n\",\"market\":\"KOSPI\",\"topN\":10}," +
            "\"entry\":{\"logic\":\"AND\",\"conditions\":[]},\"exit\":null," +
            "\"sizing\":{\"type\":\"fixed_cash\",\"value\":10000000},\"constraints\":null}";
        TradingRule rule = new TradingRule(USER_ID, "Volume Rule", "PAPER", "DRAFT", def);
        setId(rule, RULE_ID);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("STOPPED");
        return rule;
    }

    private Company company(String ticker) {
        Company c = mock(Company.class);
        when(c.getTicker()).thenReturn(ticker);
        return c;
    }

    private void setId(TradingRule rule, Long id) {
        try {
            var field = TradingRule.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(rule, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set rule id", e);
        }
    }
}

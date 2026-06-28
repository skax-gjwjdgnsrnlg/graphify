package com.graphify.trading.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * v1.6.0 단위 테스트: PaperLifecycleService.start(userId, ruleId, overrideSymbols).
 *
 * overrideSymbols가 있으면 라이브 거래대금 랭킹(resolveSymbols→liveRanking) 해석을 우회하고
 * 선택 종목을 paperLiveSymbols에 그대로 할당하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PaperLifecycleServiceOverrideTest {

    @Mock
    private TradingRuleRepository ruleRepo;

    @Mock
    private PaperLiveSymbolService paperLiveSymbolService;

    @Mock
    private CompanyRepository companyRepo;

    @Mock
    private VolumeRankingProvider liveRanking;

    @Mock
    private MarketDataIngestionService ingestionService;

    @Mock
    private PaperRunRepository runRepo;

    @Mock
    private KrxMarketCalendar marketCalendar;

    private PaperLifecycleService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VOLUME_TOP_N_DEF = """
            { "version": 1, "universe": { "type": "volume_top_n", "market": "KOSPI", "topN": 10 },
              "entry": { "logic": "AND", "conditions": [] },
              "exit": { "takeProfitPct": 10.0, "stopLossPct": -5.0 },
              "sizing": { "type": "cash", "value": 1000000 } }
            """;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
            .when(marketCalendar.isOperatingWindowOpen(org.mockito.ArgumentMatchers.any()))
            .thenReturn(true);
        service = new PaperLifecycleService(
                ruleRepo, objectMapper, paperLiveSymbolService, companyRepo,
                liveRanking, ingestionService, runRepo, marketCalendar);
    }

    private TradingRule activeStoppedRule() {
        TradingRule rule = new TradingRule(1L, "vol rule", "PAPER", "ACTIVE", VOLUME_TOP_N_DEF);
        rule.setConfigStatus("ACTIVE");
        rule.setRunStatus("STOPPED");
        return rule;
    }

    @Test
    void start_withOverride_bypassesLiveRankingAndAssignsOverride() {
        TradingRule rule = activeStoppedRule();
        when(ruleRepo.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any(TradingRule.class))).thenAnswer(inv -> inv.getArgument(0));

        service.start(1L, 10L, List.of("005930", "000660"));

        // override 경로 → 라이브 랭킹 해석 우회
        verify(liveRanking, never()).topVolume(anyString(), any(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
        // 선택 종목이 그대로 할당됨 (ruleId는 persist 전이라 null — symbols만 검증)
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(paperLiveSymbolService).assignSymbols(any(), captor.capture());
        assertThat(captor.getValue()).containsExactly("005930", "000660");
        assertThat(rule.getRunStatus()).isEqualTo("RUNNING");
    }
}

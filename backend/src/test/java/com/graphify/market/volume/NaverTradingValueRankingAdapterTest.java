package com.graphify.market.volume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.graphify.market.volume.NaverStockRankingClient.RankingRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * NaverTradingValueRankingAdapter — 시장 전체 장중 거래대금 DESC 정렬, ETF 제외, topN 캡, TTL 캐시.
 */
@ExtendWith(MockitoExtension.class)
class NaverTradingValueRankingAdapterTest {

    @Mock
    private NaverStockRankingClient client;

    @InjectMocks
    private NaverTradingValueRankingAdapter adapter;

    private static RankingRow row(String code, double tradingValue, boolean etf) {
        return new RankingRow(code, "name-" + code, tradingValue, etf);
    }

    @Test
    void topVolume_sortsByTradingValueDesc_andCapsToTopN() {
        when(client.fetchAll("KOSPI")).thenReturn(List.of(
                row("AAA", 100, false),
                row("BBB", 300, false),
                row("CCC", 200, false)));

        List<String> result = adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 2, true);

        assertThat(result).containsExactly("BBB", "CCC"); // 거래대금 DESC, 상위 2
    }

    @Test
    void topVolume_excludesEtf_whenExcludeEtfTrue() {
        when(client.fetchAll("KOSPI")).thenReturn(List.of(
                row("ETF1", 9999, true),   // 거래대금 1위지만 ETF
                row("STK1", 500, false),
                row("STK2", 400, false)));

        List<String> result = adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 10, true);

        assertThat(result).containsExactly("STK1", "STK2");
        assertThat(result).doesNotContain("ETF1");
    }

    @Test
    void topVolume_includesEtf_whenExcludeEtfFalse() {
        when(client.fetchAll("KOSPI")).thenReturn(List.of(
                row("ETF1", 9999, true),
                row("STK1", 500, false)));

        List<String> result = adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 10, false);

        assertThat(result).containsExactly("ETF1", "STK1");
    }

    @Test
    void topVolume_dropsZeroTradingValue() {
        when(client.fetchAll("KOSPI")).thenReturn(List.of(
                row("AAA", 0, false),      // 거래 없음 → 제외
                row("BBB", 100, false)));

        List<String> result = adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 10, true);

        assertThat(result).containsExactly("BBB");
    }

    @Test
    void topVolume_usesTtlCache_onlyFetchesOncePerWindow() {
        when(client.fetchAll("KOSPI")).thenReturn(List.of(row("AAA", 100, false)));

        adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 10, true);
        adapter.topVolume("KOSPI", LocalDate.of(2026, 6, 23), 10, true);

        verify(client, times(1)).fetchAll("KOSPI"); // 두 번째는 캐시 히트
    }

    @Test
    void topVolume_returnsEmpty_whenClientEmptyAndNoCache() {
        when(client.fetchAll("KOSDAQ")).thenReturn(List.of());

        List<String> result = adapter.topVolume("KOSDAQ", LocalDate.of(2026, 6, 23), 10, true);

        assertThat(result).isEmpty();
    }
}

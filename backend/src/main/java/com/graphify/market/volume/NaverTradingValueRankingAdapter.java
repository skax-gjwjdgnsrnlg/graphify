package com.graphify.market.volume;

import com.graphify.market.volume.NaverStockRankingClient.RankingRow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 라이브 {@link VolumeRankingProvider} 구현체 — Naver 모바일 API 기반 시장 전체 장중 거래대금 랭킹.
 *
 * <p><b>배경:</b> 기존 {@link YahooCumulativeVolumeAdapter}는 사전 적재된 후보 풀(in_kospi200)에
 * 대해서만 5분봉을 모아 랭킹하므로 "시장 전체에서 거래대금 상위를 그때그때 발굴"하지 못했다.
 * 본 어댑터는 {@link NaverStockRankingClient}로 시장 전체 종목의 당일 누적 거래대금을 직접 받아
 * 정렬하므로 후보 풀 사전 적재가 불필요하다(부트스트랩 의존성 제거).</p>
 *
 * <p><b>동작:</b> 전 종목 조회 → ETF 제외(excludeEtf) → 거래대금 DESC 정렬 → 상위 topN itemCode.
 * 시장별 1분 TTL 수동 캐시로 매 틱 반복 조회를 방지(YahooCumulativeVolumeAdapter 패턴).</p>
 *
 * <p><b>@VolumeRankingSemantics:</b> 라이브는 당일 누적 거래대금(금액) 기준, 시장 전체.
 * date 파라미터는 무시한다(Naver는 항상 현재 장중 스냅샷 반환). 백테스트는
 * {@link DbVolumeRankingAdapter}(완결 일봉 거래대금) — 분봉/일봉 차이만 존재.</p>
 *
 * <p><b>한계:</b> Naver {@code stockEndType}은 stock/etf만 구분 — 우선주는 stock으로 분류되어
 * 제외되지 않는다(보통주 한정이 필요하면 호출측 instrument_type 필터에 의존).</p>
 */
@Component("naverTradingValueRankingAdapter")
public class NaverTradingValueRankingAdapter implements VolumeRankingProvider {

    private static final Logger log = LoggerFactory.getLogger(NaverTradingValueRankingAdapter.class);

    /** 1분 TTL — YahooCumulativeVolumeAdapter와 동일 패턴 */
    static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final NaverStockRankingClient client;

    // 시장별 수동 TTL 캐시 (거래대금 DESC 정렬된 티커 목록)
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private record CacheEntry(List<String> tickers, Instant ts) {
    }

    public NaverTradingValueRankingAdapter(NaverStockRankingClient client) {
        this.client = client;
    }

    @Override
    public List<String> topVolume(String market, LocalDate date, int topN, boolean excludeEtf) {
        String mkt = (market != null && !market.isBlank()) ? market.trim().toUpperCase() : "KOSPI";
        int limit = topN > 0 ? topN : Integer.MAX_VALUE;

        String cacheKey = mkt + ":" + excludeEtf;
        CacheEntry cached = cache.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.ts().plus(CACHE_TTL)) && !cached.tickers().isEmpty()) {
            return cap(cached.tickers(), limit);
        }

        List<RankingRow> rows = client.fetchAll(mkt);
        List<String> ranked = rows.stream()
                .filter(r -> !(excludeEtf && r.etf()))
                .filter(r -> r.tradingValue() > 0)
                .sorted(Comparator.comparingDouble(RankingRow::tradingValue).reversed())
                .map(RankingRow::itemCode)
                .toList();

        // 빈 응답으로 캐시 오염 방지 — 비어있지 않을 때만 갱신, 실패 시 stale 캐시 유지
        if (!ranked.isEmpty()) {
            cache.put(cacheKey, new CacheEntry(ranked, Instant.now()));
            return cap(ranked, limit);
        }
        if (cached != null) {
            log.warn("Naver trading-value ranking empty for {} — serving stale cache", mkt);
            return cap(cached.tickers(), limit);
        }
        log.warn("Naver trading-value ranking empty for {} and no cache", mkt);
        return List.of();
    }

    /** 캐시 원본 리스트를 노출하지 않도록 독립 불변 리스트로 상위 limit개 반환. */
    private static List<String> cap(List<String> list, int limit) {
        return list.stream().limit(limit).toList();
    }
}

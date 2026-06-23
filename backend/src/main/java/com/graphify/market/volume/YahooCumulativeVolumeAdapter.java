package com.graphify.market.volume;

import com.graphify.market.MarketBarIntradayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 라이브 VolumeRankingProvider 구현체 — Yahoo Finance 5분봉 누적 거래대금(거래량×종가) 기반 랭킹.
 *
 * <p><b>배경:</b> KRX MDC getJsonData.cmd는 인증된 세션(KRX_ID/PW)이 필요하며
 * 무인증 호출 시 HTTP 400 "LOGOUT"을 반환한다 (06.7-01-SUMMARY 스파이크 확인).
 * 따라서 Plan 02에서는 KrxMdcVolumeAdapter 대신 Yahoo 5분봉 누적 거래량을
 * DB에서 집계해 랭킹을 계산하는 이 어댑터를 라이브 구현체로 채택한다.
 * KRX 직접 연동은 인증 방식 확보 후 별도 계획에서 추가 가능 (TODO: KRX_ID/PW 세션 경로).</p>
 *
 * <p><b>동작:</b>
 * <ol>
 *   <li>market_bars_intraday 테이블에서 당일 5분봉(interval='5m')을 symbol별 volume SUM 집계</li>
 *   <li>companies.instrument_type='COMMON_STOCK' JOIN 필터로 ETF/ETN/우선주/SPAC 제외 (V36 컬럼)</li>
 *   <li>SUM(volume × close) DESC 정렬 — 거래대금 상위 N 반환</li>
 *   <li>~1분 TTL 수동 캐시로 매 틱 DB 재조회 방지</li>
 * </ol>
 * </p>
 *
 * <p><b>빈 선택:</b> {@link DbVolumeRankingAdapter}와 동시에 {@link VolumeRankingProvider} 구현.
 * 백테스트 엔진은 {@code @Qualifier("dbVolumeRankingAdapter")}로 명시 주입,
 * <b>현재 라이브 미사용(fallback):</b> v1.5.2에서 라이브 랭킹이
 * {@code @Qualifier("naverTradingValueRankingAdapter")}(시장 전체 거래대금)로 전환됨.
 * 본 어댑터는 후보 풀(in_kospi200) 기반 분봉 누적 방식으로, Naver 소스 장애 시 대체용으로만 보존.</p>
 *
 * <p><b>@VolumeRankingSemantics:</b> 라이브는 당일 누적 5분봉 거래대금(거래량×종가) 기준.
 * 백테스트(DbVolumeRankingAdapter)는 완결 일봉 거래대금 기준 — 분봉 vs 일봉 차이만 존재, 문서화됨.</p>
 */
@Component("yahooCumulativeVolumeAdapter")
public class YahooCumulativeVolumeAdapter implements VolumeRankingProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooCumulativeVolumeAdapter.class);

    /** KST 시간대 — 당일 거래일 범위 계산에 사용 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 1분 TTL — IntradayBarCacheService 패턴 (RESEARCH §캐시 설계) */
    static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final MarketBarIntradayRepository intradayRepository;

    // Manual TTL cache (RESEARCH §캐시 설계 패턴 — Spring @Cacheable 미사용)
    private volatile List<String> cache = List.of();
    private volatile Instant cacheTs = Instant.EPOCH;

    public YahooCumulativeVolumeAdapter(MarketBarIntradayRepository intradayRepository) {
        this.intradayRepository = intradayRepository;
    }

    /**
     * 당일 5분봉 누적 거래량 상위 topN 티커 반환.
     *
     * @param market     시장 코드 ("KOSPI", "KOSDAQ")
     * @param date       오늘 날짜 (KST 기준 거래일)
     * @param topN       상위 N
     * @param excludeEtf true 권장 — JPQL이 항상 COMMON_STOCK 필터 적용하므로 무시됨
     * @return 거래량 DESC 정렬 티커 목록 (최대 topN개); 실패 시 stale 캐시 또는 빈 리스트
     */
    @Override
    public List<String> topVolume(String market, LocalDate date, int topN, boolean excludeEtf) {
        // Cache hit: TTL 이내이고 캐시가 비어있지 않으면 캐시 반환
        if (Instant.now().isBefore(cacheTs.plus(CACHE_TTL)) && !cache.isEmpty()) {
            return cache.subList(0, Math.min(topN, cache.size()));
        }

        // KST 기준 당일 시작/끝 Instant 계산
        Instant dayStart = date.atStartOfDay(KST).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(KST).toInstant();

        try {
            // topN개 만큼 DB에서 가져옴 (Pageable limit — 더 많이 캐시할 필요 없음)
            List<String> fresh = intradayRepository.findCumulativeVolumeByMarketAndDate(
                    market != null ? market : "KOSPI",
                    dayStart,
                    dayEnd,
                    PageRequest.of(0, topN > 0 ? topN : Integer.MAX_VALUE)
            );

            // Pitfall 5: 빈 응답으로 캐시 오염 방지 — 비어있지 않을 때만 갱신
            if (!fresh.isEmpty()) {
                cache = fresh;
                cacheTs = Instant.now();
            }
        } catch (Exception ex) {
            log.warn("Yahoo cumulative volume ranking fetch failed market={} date={}: {}",
                    market, date, ex.getMessage());
            // 실패 시 stale 캐시 반환 (RESEARCH Pitfall 1 패턴)
        }

        return cache.subList(0, Math.min(topN, cache.size()));
    }
}

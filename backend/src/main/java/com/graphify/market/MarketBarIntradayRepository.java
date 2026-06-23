package com.graphify.market;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketBarIntradayRepository extends JpaRepository<MarketBarIntraday, Long> {

    List<MarketBarIntraday> findBySymbolAndIntervalOrderByTsAsc(String symbol, String interval);

    Optional<MarketBarIntraday> findBySymbolAndIntervalAndTs(String symbol, String interval, Instant ts);

    @Query("""
        SELECT m FROM MarketBarIntraday m
        WHERE m.symbol = :symbol
          AND m.interval = '5m'
          AND m.ts >= :from
          AND m.ts <= :to
        ORDER BY m.ts ASC
        """)
    List<MarketBarIntraday> findBySymbolAndRange(
        @Param("symbol") String symbol,
        @Param("from") Instant from,
        @Param("to") Instant to
    );

    @Query("SELECT MAX(m.ts) FROM MarketBarIntraday m WHERE m.symbol = :symbol AND m.interval = :interval")
    Optional<Instant> findMaxTsBySymbolAndInterval(
        @Param("symbol") String symbol,
        @Param("interval") String interval
    );

    /**
     * 당일 5분봉 누적 거래대금(거래량×종가) 기준 COMMON_STOCK 종목 랭킹 (DATA-06-SC2/SC3).
     *
     * <p>instrument_type='COMMON_STOCK' 필터로 ETF/ETN/우선주/SPAC 제외 (V36 컬럼).
     * market_bars_intraday의 (volume × close)를 symbol 별 SUM 후 DESC 정렬.
     * 순수 거래량(주식 수)이 아닌 거래대금(금액) 기준 — 저가 대량거래주 왜곡 방지.
     * Pageable로 topN 제어 (limit 절).</p>
     *
     * <p>date 파라미터는 KST 기준 거래일 날짜 (LocalDate). ts 비교는
     * 해당 날짜 00:00:00 KST(=전일 15:00 UTC) 이상, 익일 00:00:00 KST 미만으로 필터.</p>
     */
    @Query("""
        SELECT m.symbol
        FROM MarketBarIntraday m
        JOIN com.graphify.company.Company c ON c.ticker = m.symbol
        WHERE c.market = :market
          AND c.instrumentType = 'COMMON_STOCK'
          AND m.interval = '5m'
          AND m.ts >= :dayStart
          AND m.ts < :dayEnd
          AND m.volume IS NOT NULL
        GROUP BY m.symbol
        ORDER BY SUM(m.volume * m.close) DESC
        """)
    List<String> findCumulativeVolumeByMarketAndDate(
        @Param("market") String market,
        @Param("dayStart") Instant dayStart,
        @Param("dayEnd") Instant dayEnd,
        Pageable pageable
    );
}

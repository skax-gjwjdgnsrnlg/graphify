package com.graphify.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketBarRepository extends JpaRepository<MarketBar, Long> {

    List<MarketBar> findBySymbolOrderByTradingDateAsc(String symbol);

    Optional<MarketBar> findBySymbolAndTradingDate(String symbol, LocalDate tradingDate);

    boolean existsBySymbol(String symbol);

    /**
     * in_kospi200=TRUE 종목 중 지정 날짜 거래량 상위 N 티커 반환 (DATA-04).
     * volume=null 봉은 제외. Pageable로 topN 제어.
     */
    @Query("""
            SELECT b.symbol
            FROM MarketBar b
            JOIN Company c ON c.ticker = b.symbol
            WHERE b.tradingDate = :date
              AND c.inKospi200 = true
              AND b.volume IS NOT NULL
            ORDER BY b.volume DESC
            """)
    List<String> findTopVolumeSymbolsOnDate(
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * 지정 시장의 COMMON_STOCK 종목 중 날짜별 거래량 상위 N 티커 반환 (DATA-06).
     * ETF/ETN/우선주 제외 (instrument_type = 'COMMON_STOCK' 조건).
     * in_kospi200 제약 없음 (RESEARCH Pitfall 4 — 전체 KOSPI 보통주 대상).
     */
    @Query("""
            SELECT b.symbol
            FROM MarketBar b
            JOIN Company c ON c.ticker = b.symbol
            WHERE b.tradingDate = :date
              AND c.market = :market
              AND c.instrumentType = 'COMMON_STOCK'
              AND b.volume IS NOT NULL
            ORDER BY b.volume DESC
            """)
    List<String> findTopVolumeByMarketOnDate(
            @Param("market") String market,
            @Param("date") LocalDate date,
            Pageable pageable);

    /**
     * 지정 시장의 in_kospi200=true 종목 전체 고유 티커 반환.
     * volume_top_n 백테스트 시 데이터 사전 로드 후보군 생성에 사용.
     */
    @Query("""
            SELECT DISTINCT b.symbol
            FROM MarketBar b
            JOIN Company c ON c.ticker = b.symbol
            WHERE c.inKospi200 = true
              AND c.market = :market
            """)
    List<String> findDistinctKospi200Symbols(@Param("market") String market);
}

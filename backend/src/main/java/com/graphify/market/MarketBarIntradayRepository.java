package com.graphify.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
}

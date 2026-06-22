package com.graphify.trading.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.graphify.market.MarketBarIntraday;
import com.graphify.market.MarketBarIntradayRepository;
import com.graphify.trading.backtest.dto.CandleBarDto;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * API-1: bars endpoint returns OHLCV bars for requested (symbol, date).
 * API-2: each bar's time field = epoch-seconds (not millis).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
})
class BarQueryTest {

    @Autowired
    MarketBarIntradayRepository repository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String SYMBOL = "005930";

    // -------------------------------------------------------
    // Unit tests for CandleBarDto.from() — no Spring context
    // -------------------------------------------------------

    @Test
    void candleBarDto_from_maps_epoch_seconds() {
        // API-2: time must be epoch seconds, not millis
        Instant ts = Instant.parse("2026-01-05T00:05:00Z"); // 09:05 KST
        MarketBarIntraday bar = new MarketBarIntraday(
            SYMBOL, ts, "5m", 74000.0, 75000.0, 73500.0, 74500.0, 5000L, "YAHOO");

        CandleBarDto dto = CandleBarDto.from(bar);

        assertThat(dto.time()).isEqualTo(ts.getEpochSecond());
        // epoch seconds for current-era dates are ~1.7e9 — well below 1e11
        assertThat(dto.time()).isLessThan((long) 1e11);
    }

    @Test
    void candleBarDto_from_maps_ohlcv_correctly() {
        Instant ts = Instant.parse("2026-01-05T00:05:00Z");
        MarketBarIntraday bar = new MarketBarIntraday(
            SYMBOL, ts, "5m", 74000.0, 75000.0, 73500.0, 74500.0, 5000L, "YAHOO");

        CandleBarDto dto = CandleBarDto.from(bar);

        assertThat(dto.open()).isEqualTo(74000.0);
        assertThat(dto.high()).isEqualTo(75000.0);
        assertThat(dto.low()).isEqualTo(73500.0);
        assertThat(dto.close()).isEqualTo(74500.0);
        assertThat(dto.volume()).isEqualTo(5000L);
    }

    @Test
    void candleBarDto_from_null_ohlc_falls_back_to_close() {
        // Null open/high/low must fall back to close — no null OHLC in response
        Instant ts = Instant.parse("2026-01-05T00:05:00Z");
        MarketBarIntraday bar = new MarketBarIntraday(
            SYMBOL, ts, "5m", null, null, null, 74500.0, null, "YAHOO");

        CandleBarDto dto = CandleBarDto.from(bar);

        assertThat(dto.open()).isEqualTo(74500.0);
        assertThat(dto.high()).isEqualTo(74500.0);
        assertThat(dto.low()).isEqualTo(74500.0);
        assertThat(dto.close()).isEqualTo(74500.0);
        assertThat(dto.volume()).isEqualTo(0L);
    }

    // -------------------------------------------------------
    // @DataJpaTest slice — KST-day range filtering (API-1)
    // -------------------------------------------------------

    @Test
    void findBySymbolAndRange_returns_only_requested_kst_day() {
        // Day 1: 2026-01-05 KST — bars at 09:00 and 09:05
        LocalDate day1 = LocalDate.of(2026, 1, 5);
        Instant ts1 = day1.atTime(0, 0, 0).atZone(KST).toInstant(); // 00:00 KST = prev UTC day
        Instant ts2 = day1.atTime(0, 5, 0).atZone(KST).toInstant();

        // Day 2: 2026-01-06 KST — bar at 09:00
        LocalDate day2 = LocalDate.of(2026, 1, 6);
        Instant ts3 = day2.atTime(0, 0, 0).atZone(KST).toInstant();

        repository.save(new MarketBarIntraday(SYMBOL, ts1, "5m", 74000.0, 75000.0, 73500.0, 74500.0, 1000L, "YAHOO"));
        repository.save(new MarketBarIntraday(SYMBOL, ts2, "5m", 74500.0, 75500.0, 74000.0, 75000.0, 1100L, "YAHOO"));
        repository.save(new MarketBarIntraday(SYMBOL, ts3, "5m", 75000.0, 76000.0, 74500.0, 75500.0, 1200L, "YAHOO"));

        // Query day1 KST range (exact pattern from interfaces block)
        Instant from = day1.atStartOfDay(KST).toInstant();
        Instant to   = day1.atTime(23, 59, 59).atZone(KST).toInstant();

        List<MarketBarIntraday> bars = repository.findBySymbolAndRange(SYMBOL, from, to);

        // API-1: only day1 bars returned, in ascending order
        assertThat(bars).hasSize(2);
        assertThat(bars.get(0).getTs()).isEqualTo(ts1);
        assertThat(bars.get(1).getTs()).isEqualTo(ts2);
    }

    @Test
    void findBySymbolAndRange_bars_map_to_epoch_seconds() {
        // API-2: verify the full mapping chain via CandleBarDto.from
        LocalDate day = LocalDate.of(2026, 1, 5);
        Instant ts = day.atTime(0, 5, 0).atZone(KST).toInstant();

        repository.save(new MarketBarIntraday(SYMBOL, ts, "5m", 74000.0, 75000.0, 73500.0, 74500.0, 500L, "YAHOO"));

        Instant from = day.atStartOfDay(KST).toInstant();
        Instant to   = day.atTime(23, 59, 59).atZone(KST).toInstant();

        List<CandleBarDto> dtos = repository.findBySymbolAndRange(SYMBOL, from, to)
            .stream().map(CandleBarDto::from).toList();

        assertThat(dtos).hasSize(1);
        CandleBarDto dto = dtos.get(0);
        assertThat(dto.time()).isEqualTo(ts.getEpochSecond());
        assertThat(dto.time()).isLessThan((long) 1e11);
    }
}

package com.graphify.market;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KrxMarketCalendarTest {

    @Mock
    MarketHolidayRepository holidayRepository;

    @InjectMocks
    KrxMarketCalendar calendar;

    @Test
    void weekday_not_holiday_is_trading_day() {
        // 2026-06-22 is a Monday
        LocalDate monday = LocalDate.of(2026, 6, 22);
        when(holidayRepository.existsByHolidayDate(monday)).thenReturn(false);
        assertThat(calendar.isTradingDay(monday)).isTrue();
    }

    @Test
    void saturday_is_not_trading_day() {
        LocalDate saturday = LocalDate.of(2026, 6, 20);
        assertThat(calendar.isTradingDay(saturday)).isFalse();
        verifyNoInteractions(holidayRepository);
    }

    @Test
    void sunday_is_not_trading_day() {
        LocalDate sunday = LocalDate.of(2026, 6, 21);
        assertThat(calendar.isTradingDay(sunday)).isFalse();
        verifyNoInteractions(holidayRepository);
    }

    @Test
    void weekday_in_market_holidays_is_not_trading_day() {
        // 2026-01-01 신정 — Thursday
        LocalDate holiday = LocalDate.of(2026, 1, 1);
        when(holidayRepository.existsByHolidayDate(holiday)).thenReturn(true);
        assertThat(calendar.isTradingDay(holiday)).isFalse();
    }

    @Test
    void weekday_not_in_market_holidays_is_trading_day() {
        LocalDate regularDay = LocalDate.of(2026, 6, 19); // Friday
        when(holidayRepository.existsByHolidayDate(regularDay)).thenReturn(false);
        assertThat(calendar.isTradingDay(regularDay)).isTrue();
    }

    // ─── isOperatingWindowOpen ──────────────────────────────────────────────────

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 2026-06-22 is a Monday (trading day when not a holiday)
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 22);

    @Test
    void operatingWindow_weekday_at_nine_am_is_open() {
        when(holidayRepository.existsByHolidayDate(MONDAY)).thenReturn(false);
        ZonedDateTime t = MONDAY.atTime(9, 0).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isTrue();
    }

    @Test
    void operatingWindow_weekday_at_17_59_is_open() {
        when(holidayRepository.existsByHolidayDate(MONDAY)).thenReturn(false);
        ZonedDateTime t = MONDAY.atTime(17, 59).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isTrue();
    }

    @Test
    void operatingWindow_weekday_at_08_59_is_closed() {
        when(holidayRepository.existsByHolidayDate(MONDAY)).thenReturn(false);
        ZonedDateTime t = MONDAY.atTime(8, 59).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isFalse();
    }

    @Test
    void operatingWindow_weekday_at_18_00_is_closed() {
        when(holidayRepository.existsByHolidayDate(MONDAY)).thenReturn(false);
        ZonedDateTime t = MONDAY.atTime(18, 0).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isFalse();
    }

    @Test
    void operatingWindow_saturday_is_closed() {
        LocalDate saturday = LocalDate.of(2026, 6, 20);
        ZonedDateTime t = saturday.atTime(10, 0).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isFalse();
        verifyNoInteractions(holidayRepository);
    }

    @Test
    void operatingWindow_sunday_is_closed() {
        LocalDate sunday = LocalDate.of(2026, 6, 21);
        ZonedDateTime t = sunday.atTime(10, 0).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isFalse();
        verifyNoInteractions(holidayRepository);
    }

    @Test
    void operatingWindow_weekday_holiday_is_closed() {
        // 2026-01-01 신정 — Thursday, but registered as holiday
        LocalDate holiday = LocalDate.of(2026, 1, 1);
        when(holidayRepository.existsByHolidayDate(holiday)).thenReturn(true);
        ZonedDateTime t = holiday.atTime(10, 0).atZone(KST);
        assertThat(calendar.isOperatingWindowOpen(t)).isFalse();
    }
}

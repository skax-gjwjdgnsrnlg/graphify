package com.graphify.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * KRX 장 중 판단 서비스.
 * 주말 및 market_holidays 테이블에 등록된 날짜를 비거래일로 처리.
 * Phase 3 평가 엔진에서도 재사용한다.
 */
@Service
public class KrxMarketCalendar {

    private final MarketHolidayRepository holidayRepository;

    public KrxMarketCalendar(MarketHolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * 거래일 여부 반환.
     * @param date KST 기준 날짜
     * @return true if weekday and not in market_holidays
     */
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidayRepository.existsByHolidayDate(date);
    }
}

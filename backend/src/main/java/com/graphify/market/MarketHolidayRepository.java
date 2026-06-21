package com.graphify.market;

import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, Long> {
    boolean existsByHolidayDate(LocalDate date);
}

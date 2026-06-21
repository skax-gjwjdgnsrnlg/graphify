package com.graphify.market;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_holidays")
public class MarketHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "holiday_date", nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(length = 100)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarketHoliday() {}

    public MarketHoliday(LocalDate holidayDate, String description) {
        this.holidayDate = holidayDate;
        this.description = description;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public String getDescription() { return description; }
}

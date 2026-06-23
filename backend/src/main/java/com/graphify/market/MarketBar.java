package com.graphify.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "market_bars")
public class MarketBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(columnDefinition = "numeric(20,4)")
    private Double open;
    @Column(columnDefinition = "numeric(20,4)")
    private Double high;
    @Column(columnDefinition = "numeric(20,4)")
    private Double low;

    @Column(nullable = false, columnDefinition = "numeric(20,4)")
    private Double close;

    private Long volume;

    @Column(nullable = false)
    private String source = "YAHOO";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarketBar() {
    }

    public MarketBar(String symbol, LocalDate tradingDate, Double open, Double high,
                     Double low, double close, Long volume, String source) {
        this.symbol = symbol;
        this.tradingDate = tradingDate;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.source = source;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public LocalDate getTradingDate() {
        return tradingDate;
    }

    public Double getOpen() {
        return open;
    }

    public Double getHigh() {
        return high;
    }

    public Double getLow() {
        return low;
    }

    public Double getClose() {
        return close;
    }

    public Long getVolume() {
        return volume;
    }

    public void update(Double open, Double high, Double low, double close, Long volume) {
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }
}

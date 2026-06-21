package com.graphify.trading.rule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "trading_rules")
public class TradingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String mode = "PAPER";

    @Column(nullable = false)
    private String status = "DRAFT";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String definition;

    @Column(nullable = false)
    private boolean backtested = false;

    @Column(name = "promoted_from")
    private Long promotedFrom;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TradingRule() {
    }

    public TradingRule(Long userId, String name, String mode, String status, String definition) {
        this.userId = userId;
        this.name = name;
        this.mode = mode;
        this.status = status;
        this.definition = definition;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public String getDefinition() {
        return definition;
    }

    public boolean isBacktested() {
        return backtested;
    }

    public void setBacktested(boolean backtested) {
        this.backtested = backtested;
    }

    public Long getPromotedFrom() {
        return promotedFrom;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDefinition(String definition) {
        this.definition = definition;
    }

    public void setPromotedFrom(Long promotedFrom) {
        this.promotedFrom = promotedFrom;
    }
}

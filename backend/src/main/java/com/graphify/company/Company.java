package com.graphify.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String ticker;

    private String industry;

    private String market;

    @Column(name = "data_status", nullable = false)
    private String dataStatus = "FRESH";

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "external_source")
    private String externalSource;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "sync_status", nullable = false)
    private String syncStatus = "FULL";

    @Column(name = "detail_synced_at")
    private Instant detailSyncedAt;

    private Boolean listed;

    @Column(name = "in_kospi200", nullable = false)
    private boolean inKospi200 = false;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTicker() {
        return ticker;
    }

    public String getIndustry() {
        return industry;
    }

    public String getMarket() {
        return market;
    }

    public String getDataStatus() {
        return dataStatus;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getSummary() {
        return summary;
    }

    public String getExternalSource() {
        return externalSource;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public Instant getDetailSyncedAt() {
        return detailSyncedAt;
    }

    public Boolean getListed() {
        return listed;
    }

    public boolean isInKospi200() {
        return inKospi200;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public void setDataStatus(String dataStatus) {
        this.dataStatus = dataStatus;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setExternalSource(String externalSource) {
        this.externalSource = externalSource;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public void setDetailSyncedAt(Instant detailSyncedAt) {
        this.detailSyncedAt = detailSyncedAt;
    }

    public void setListed(Boolean listed) {
        this.listed = listed;
    }

    public void setInKospi200(boolean inKospi200) {
        this.inKospi200 = inKospi200;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean needsSync() {
        return "STUB".equals(syncStatus) || "PARTIAL".equals(syncStatus);
    }
}

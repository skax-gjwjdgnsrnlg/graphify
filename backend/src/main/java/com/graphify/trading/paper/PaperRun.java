package com.graphify.trading.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "paper_runs")
public class PaperRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;  // NULL = 진행중

    @Column(nullable = false, length = 8)
    private String status;  // RUNNING | STOPPED

    @Column(name = "universe_snapshot")
    private String universeSnapshot;  // JSON string, e.g. '["005930","000660"]'

    protected PaperRun() {}

    public PaperRun(Long ruleId, Long userId, Instant startedAt, String universeSnapshot) {
        this.ruleId = ruleId;
        this.userId = userId;
        this.startedAt = startedAt;
        this.status = "RUNNING";
        this.universeSnapshot = universeSnapshot;
    }

    public void stop(Instant endedAt) {
        this.endedAt = endedAt;
        this.status = "STOPPED";
    }

    public Long getId()                  { return id; }
    public Long getRuleId()              { return ruleId; }
    public Long getUserId()              { return userId; }
    public Instant getStartedAt()        { return startedAt; }
    public Instant getEndedAt()          { return endedAt; }
    public String getStatus()            { return status; }
    public String getUniverseSnapshot()  { return universeSnapshot; }
}

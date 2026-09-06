package com.sportssession.platform.matchplan.infrastructure;

import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_plans")
public class MatchPlanEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "session_court_id", nullable = false)
    private UUID sessionCourtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MatchSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MatchPlanStatus status;

    @Column(name = "queue_position")
    private Integer queuePosition;

    @Column(name = "started_match_id")
    private UUID startedMatchId;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MatchPlanEntity() {
    }

    private MatchPlanEntity(MatchPlan plan) {
        this.id = plan.id();
        this.sessionId = plan.sessionId();
        this.sessionCourtId = plan.sessionCourtId();
        this.source = plan.source();
        this.status = plan.status();
        this.queuePosition = plan.queuePosition();
        this.startedMatchId = plan.startedMatchId();
        this.startedAt = plan.startedAt();
        this.cancelledAt = plan.cancelledAt();
        this.version = plan.version();
        this.createdAt = plan.createdAt();
        this.updatedAt = plan.updatedAt();
    }

    public static MatchPlanEntity from(MatchPlan plan) {
        return new MatchPlanEntity(plan);
    }

    public void apply(MatchPlan plan) {
        this.sessionCourtId = plan.sessionCourtId();
        this.status = plan.status();
        this.queuePosition = plan.queuePosition();
        this.startedMatchId = plan.startedMatchId();
        this.startedAt = plan.startedAt();
        this.cancelledAt = plan.cancelledAt();
        this.updatedAt = plan.updatedAt();
    }

    public MatchPlan toDomain() {
        return new MatchPlan(
                id, sessionId, sessionCourtId, source, status, queuePosition,
                startedMatchId, startedAt, cancelledAt, version,
                createdAt, updatedAt
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getSessionCourtId() {
        return sessionCourtId;
    }

    public MatchPlanStatus getStatus() {
        return status;
    }

    public Integer getQueuePosition() {
        return queuePosition;
    }
}

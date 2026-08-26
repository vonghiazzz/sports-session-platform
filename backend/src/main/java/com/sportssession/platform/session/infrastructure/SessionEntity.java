package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.SportCode;
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
@Table(name = "sessions")
public class SessionEntity {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_code", nullable = false, length = 32)
    private SportCode sportCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_format", nullable = false, length = 32)
    private MatchFormat matchFormat;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SessionStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SessionEntity() {
    }

    private SessionEntity(Session session) {
        this.id = session.id();
        this.venueId = session.venueId();
        this.title = session.title();
        this.sportCode = session.sportCode();
        this.matchFormat = session.matchFormat();
        this.plannedStartAt = session.plannedStartAt();
        this.plannedEndAt = session.plannedEndAt();
        this.status = session.status();
        this.startedAt = session.startedAt();
        this.completedAt = session.completedAt();
        this.cancelledAt = session.cancelledAt();
        this.version = session.version();
        this.createdAt = session.createdAt();
        this.updatedAt = session.updatedAt();
    }

    public static SessionEntity from(Session session) {
        return new SessionEntity(session);
    }

    public void applyRuntimeState(Session session) {
        this.status = session.status();
        this.startedAt = session.startedAt();
        this.completedAt = session.completedAt();
        this.cancelledAt = session.cancelledAt();
        this.updatedAt = session.updatedAt();
    }

    public Session toDomain() {
        return new Session(
                id,
                venueId,
                title,
                sportCode,
                matchFormat,
                plannedStartAt,
                plannedEndAt,
                status,
                startedAt,
                completedAt,
                cancelledAt,
                version,
                createdAt,
                updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}

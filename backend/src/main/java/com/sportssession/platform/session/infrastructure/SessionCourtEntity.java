package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
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
@Table(name = "session_courts")
public class SessionCourtEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "court_id", nullable = false)
    private UUID courtId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SessionCourtStatus status;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SessionCourtEntity() {
    }

    private SessionCourtEntity(SessionCourt sessionCourt) {
        this.id = sessionCourt.id();
        this.sessionId = sessionCourt.sessionId();
        this.courtId = sessionCourt.courtId();
        this.status = sessionCourt.status();
        this.addedAt = sessionCourt.addedAt();
        this.version = sessionCourt.version();
        this.createdAt = sessionCourt.createdAt();
        this.updatedAt = sessionCourt.updatedAt();
    }

    public static SessionCourtEntity from(SessionCourt sessionCourt) {
        return new SessionCourtEntity(sessionCourt);
    }

    public void applyRuntimeState(SessionCourt sessionCourt) {
        this.status = sessionCourt.status();
        this.updatedAt = sessionCourt.updatedAt();
    }

    public SessionCourt toDomain() {
        return new SessionCourt(
                id, sessionId, courtId, status, addedAt, version, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getCourtId() {
        return courtId;
    }

    public SessionCourtStatus getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}

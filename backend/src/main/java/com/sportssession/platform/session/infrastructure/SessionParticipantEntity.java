package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
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
@Table(name = "session_participants")
public class SessionParticipantEntity {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ParticipantStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "waiting_since")
    private Instant waitingSince;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "total_paused_seconds", nullable = false)
    private long totalPausedSeconds;

    @Column(name = "left_at")
    private Instant leftAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SessionParticipantEntity() {
    }

    private SessionParticipantEntity(SessionParticipant participant) {
        this.id = participant.id();
        this.sessionId = participant.sessionId();
        this.playerId = participant.playerId();
        this.status = participant.status();
        this.joinedAt = participant.joinedAt();
        this.checkedInAt = participant.checkedInAt();
        this.waitingSince = participant.waitingSince();
        this.pausedAt = participant.pausedAt();
        this.totalPausedSeconds = participant.totalPausedSeconds();
        this.leftAt = participant.leftAt();
        this.version = participant.version();
        this.createdAt = participant.createdAt();
        this.updatedAt = participant.updatedAt();
    }

    public static SessionParticipantEntity from(SessionParticipant participant) {
        return new SessionParticipantEntity(participant);
    }

    public void applyRuntimeState(SessionParticipant participant) {
        this.status = participant.status();
        this.checkedInAt = participant.checkedInAt();
        this.waitingSince = participant.waitingSince();
        this.pausedAt = participant.pausedAt();
        this.totalPausedSeconds = participant.totalPausedSeconds();
        this.leftAt = participant.leftAt();
        this.updatedAt = participant.updatedAt();
    }

    public SessionParticipant toDomain() {
        return new SessionParticipant(
                id,
                sessionId,
                playerId,
                status,
                joinedAt,
                checkedInAt,
                waitingSince,
                pausedAt,
                totalPausedSeconds,
                leftAt,
                version,
                createdAt,
                updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public Instant getWaitingSince() {
        return waitingSince;
    }

    public long getVersion() {
        return version;
    }
}

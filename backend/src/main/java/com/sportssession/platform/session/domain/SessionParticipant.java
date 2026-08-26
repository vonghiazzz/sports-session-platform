package com.sportssession.platform.session.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionParticipant(
        UUID id,
        UUID sessionId,
        UUID playerId,
        ParticipantStatus status,
        Instant joinedAt,
        Instant checkedInAt,
        Instant waitingSince,
        Instant pausedAt,
        long totalPausedSeconds,
        Instant leftAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public SessionParticipant {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(joinedAt, "joinedAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (totalPausedSeconds < 0) {
            throw new IllegalArgumentException("totalPausedSeconds must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        validateStateTimestamps(status, checkedInAt, waitingSince, pausedAt, leftAt);
    }

    public static SessionParticipant register(UUID sessionId, UUID playerId, Instant now) {
        return new SessionParticipant(
                UUID.randomUUID(),
                sessionId,
                playerId,
                ParticipantStatus.REGISTERED,
                now,
                null,
                null,
                null,
                0,
                null,
                0,
                now,
                now);
    }

    public SessionParticipant checkIn(Instant now) {
        requireStatus(ParticipantStatus.REGISTERED, "check in");
        return copy(ParticipantStatus.WAITING, now, now, null,
                totalPausedSeconds, null, now);
    }

    public SessionParticipant pause(Instant now) {
        requireStatus(ParticipantStatus.WAITING, "pause");
        return copy(ParticipantStatus.PAUSED, checkedInAt, null, now,
                totalPausedSeconds, null, now);
    }

    public SessionParticipant startMatch(Instant now) {
        requireStatus(ParticipantStatus.WAITING, "start a Match");
        return copy(ParticipantStatus.PLAYING, checkedInAt, null, null,
                totalPausedSeconds, null, now);
    }

    public SessionParticipant releaseFromMatch(Instant releasedAt) {
        requireStatus(ParticipantStatus.PLAYING, "release from a Match");
        return copy(ParticipantStatus.WAITING, checkedInAt, releasedAt, null,
                totalPausedSeconds, null, releasedAt);
    }

    public SessionParticipant resume(Instant now) {
        requireStatus(ParticipantStatus.PAUSED, "resume");

        long pausedSeconds = calculatePausedSeconds(now);

        return copy(
                ParticipantStatus.WAITING,
                checkedInAt,
                now,
                null,
                totalPausedSeconds + pausedSeconds,
                null,
                now
        );
    }

    public SessionParticipant leave(Instant now) {
        if (status != ParticipantStatus.REGISTERED
                && status != ParticipantStatus.WAITING
                && status != ParticipantStatus.PAUSED) {
            throw new InvalidParticipantStateException(
                    "Participant cannot leave from status " + status
            );
        }

        long updatedTotalPausedSeconds = totalPausedSeconds;

        if (status == ParticipantStatus.PAUSED) {
            updatedTotalPausedSeconds += calculatePausedSeconds(now);
        }

        return copy(
                ParticipantStatus.LEFT,
                checkedInAt,
                null,
                null,
                updatedTotalPausedSeconds,
                now,
                now
        );
    }

    private long calculatePausedSeconds(Instant now) {
        long pausedSeconds = Duration.between(pausedAt, now).getSeconds();

        if (pausedSeconds < 0) {
            throw new IllegalArgumentException(
                    "Time must not be before pausedAt"
            );
        }

        return pausedSeconds;
    }

    private void requireStatus(ParticipantStatus expected, String action) {
        if (status != expected) {
            throw new InvalidParticipantStateException(
                    "Participant cannot " + action + " from status " + status);
        }
    }

    private SessionParticipant copy(
            ParticipantStatus newStatus,
            Instant newCheckedInAt,
            Instant newWaitingSince,
            Instant newPausedAt,
            long newTotalPausedSeconds,
            Instant newLeftAt,
            Instant newUpdatedAt
    ) {
        return new SessionParticipant(
                id,
                sessionId,
                playerId,
                newStatus,
                joinedAt,
                newCheckedInAt,
                newWaitingSince,
                newPausedAt,
                newTotalPausedSeconds,
                newLeftAt,
                version,
                createdAt,
                newUpdatedAt);
    }

    private static void validateStateTimestamps(
            ParticipantStatus status,
            Instant checkedInAt,
            Instant waitingSince,
            Instant pausedAt,
            Instant leftAt
    ) {
        boolean valid = switch (status) {
            case REGISTERED -> checkedInAt == null && waitingSince == null
                    && pausedAt == null && leftAt == null;
            case WAITING -> checkedInAt != null && waitingSince != null
                    && pausedAt == null && leftAt == null;
            case PLAYING -> checkedInAt != null && waitingSince == null
                    && pausedAt == null && leftAt == null;
            case PAUSED -> checkedInAt != null && waitingSince == null
                    && pausedAt != null && leftAt == null;
            case LEFT -> waitingSince == null && pausedAt == null && leftAt != null;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Participant timestamps are inconsistent with status " + status);
        }
    }
}

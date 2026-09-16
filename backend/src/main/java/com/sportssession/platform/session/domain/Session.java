package com.sportssession.platform.session.domain;

import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.shared.domain.MatchFormat;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Session(
        UUID id,
        UUID venueId,
        String title,
        SportCode sportCode,
        MatchFormat matchFormat,
        Instant plannedStartAt,
        Instant plannedEndAt,
        SessionStatus status,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public Session {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(venueId, "venueId is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(plannedStartAt, "plannedStartAt is required");
        Objects.requireNonNull(plannedEndAt, "plannedEndAt is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        title = title.strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 160) {
            throw new IllegalArgumentException("title must not exceed 160 characters");
        }
        if (!plannedEndAt.isAfter(plannedStartAt)) {
            throw new InvalidSessionTimeRangeException();
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        validateStateTimestamps(status, startedAt, completedAt, cancelledAt);
    }

    public static Session create(
            UUID venueId,
            String title,
            SportCode sportCode,
            MatchFormat matchFormat,
            Instant plannedStartAt,
            Instant plannedEndAt,
            Instant now
    ) {
        return new Session(
                UUID.randomUUID(),
                venueId,
                title,
                sportCode,
                matchFormat,
                plannedStartAt,
                plannedEndAt,
                SessionStatus.PLANNED,
                null,
                null,
                null,
                0,
                now,
                now);
    }

    public Session start(Instant now) {
        if (status != SessionStatus.PLANNED) {
            throw new InvalidSessionStateException(
                    "Session cannot start from status " + status);
        }
        return copy(SessionStatus.IN_PROGRESS, now, null, null, now);
    }

    public Session complete(Instant now) {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new InvalidSessionStateException(
                    "Session cannot complete from status " + status);
        }
        return copy(SessionStatus.COMPLETED, startedAt, now, null, now);
    }

    public Session cancel(Instant now) {
        if (status != SessionStatus.PLANNED && status != SessionStatus.IN_PROGRESS) {
            throw new InvalidSessionStateException(
                    "Session cannot cancel from status " + status);
        }
        return copy(SessionStatus.CANCELLED, startedAt, null, now, now);
    }

    public boolean isTerminal() {
        return status == SessionStatus.COMPLETED || status == SessionStatus.CANCELLED;
    }

    private Session copy(
            SessionStatus newStatus,
            Instant newStartedAt,
            Instant newCompletedAt,
            Instant newCancelledAt,
            Instant newUpdatedAt
    ) {
        return new Session(
                id,
                venueId,
                title,
                sportCode,
                matchFormat,
                plannedStartAt,
                plannedEndAt,
                newStatus,
                newStartedAt,
                newCompletedAt,
                newCancelledAt,
                version,
                createdAt,
                newUpdatedAt);
    }

    private static void validateStateTimestamps(
            SessionStatus status,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
        boolean valid = switch (status) {
            case PLANNED -> startedAt == null && completedAt == null && cancelledAt == null;
            case IN_PROGRESS -> startedAt != null
                    && completedAt == null && cancelledAt == null;
            case COMPLETED -> startedAt != null
                    && completedAt != null && cancelledAt == null;
            case CANCELLED -> completedAt == null && cancelledAt != null;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "Session timestamps are inconsistent with status " + status);
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "completedAt must not be before startedAt"
            );
        }

        if (cancelledAt != null
                && startedAt != null
                && cancelledAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "cancelledAt must not be before startedAt"
            );
        }
    }
}

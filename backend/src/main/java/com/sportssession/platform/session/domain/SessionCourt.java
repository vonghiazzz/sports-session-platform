package com.sportssession.platform.session.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessionCourt(
        UUID id,
        UUID sessionId,
        UUID courtId,
        SessionCourtStatus status,
        Instant addedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public SessionCourt {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(courtId, "courtId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(addedAt, "addedAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public static SessionCourt allocate(UUID sessionId, UUID courtId, Instant now) {
        return new SessionCourt(
                UUID.randomUUID(),
                sessionId,
                courtId,
                SessionCourtStatus.AVAILABLE,
                now,
                0,
                now,
                now);
    }

    public SessionCourt disable(Instant now) {
        if (status != SessionCourtStatus.AVAILABLE) {
            throw new InvalidSessionCourtStateException(
                    "Session Court cannot disable from status " + status);
        }
        return copy(SessionCourtStatus.UNAVAILABLE, now);
    }

    public SessionCourt enable(Instant now) {
        if (status != SessionCourtStatus.UNAVAILABLE) {
            throw new InvalidSessionCourtStateException(
                    "Session Court cannot enable from status " + status);
        }
        return copy(SessionCourtStatus.AVAILABLE, now);
    }

    private SessionCourt copy(SessionCourtStatus newStatus, Instant now) {
        return new SessionCourt(
                id, sessionId, courtId, newStatus, addedAt, version, createdAt, now);
    }
}

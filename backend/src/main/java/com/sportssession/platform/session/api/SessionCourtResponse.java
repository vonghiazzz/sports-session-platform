package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;

import java.time.Instant;
import java.util.UUID;

public record SessionCourtResponse(
        UUID id,
        UUID sessionId,
        UUID courtId,
        SessionCourtStatus status,
        Instant addedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    static SessionCourtResponse from(SessionCourt sessionCourt) {
        return new SessionCourtResponse(
                sessionCourt.id(),
                sessionCourt.sessionId(),
                sessionCourt.courtId(),
                sessionCourt.status(),
                sessionCourt.addedAt(),
                sessionCourt.version(),
                sessionCourt.createdAt(),
                sessionCourt.updatedAt());
    }
}

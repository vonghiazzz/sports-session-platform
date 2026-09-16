package com.sportssession.platform.session.api;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID venueId,
        String title,
        SportCode sport,
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
    static SessionResponse from(Session session) {
        return new SessionResponse(
                session.id(),
                session.venueId(),
                session.title(),
                session.sportCode(),
                session.matchFormat(),
                session.plannedStartAt(),
                session.plannedEndAt(),
                session.status(),
                session.startedAt(),
                session.completedAt(),
                session.cancelledAt(),
                session.version(),
                session.createdAt(),
                session.updatedAt());
    }
}

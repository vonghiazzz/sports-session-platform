package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.session.domain.SessionCourtStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GlobalMatchmakingCourtSnapshot(
        UUID sessionCourtId,
        SessionCourtStatus status,
        Instant addedAt
) {
    public GlobalMatchmakingCourtSnapshot {
        Objects.requireNonNull(
                sessionCourtId,
                "sessionCourtId is required"
        );
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(addedAt, "addedAt is required");
    }
}

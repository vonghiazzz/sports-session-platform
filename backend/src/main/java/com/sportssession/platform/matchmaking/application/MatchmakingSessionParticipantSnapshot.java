package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.session.domain.ParticipantStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MatchmakingSessionParticipantSnapshot(
        UUID sessionParticipantId,
        UUID playerId,
        ParticipantStatus participantStatus,
        Instant waitingSince
) {
    public MatchmakingSessionParticipantSnapshot {
        Objects.requireNonNull(
                sessionParticipantId,
                "sessionParticipantId is required"
        );
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(
                participantStatus,
                "participantStatus is required"
        );
    }
}

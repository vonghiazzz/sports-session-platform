package com.sportssession.platform.match.domain;

import java.util.Objects;
import java.util.UUID;

public record MatchParticipant(
        UUID id,
        UUID matchId,
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
    public MatchParticipant {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(matchId, "matchId is required");
        Objects.requireNonNull(
                sessionParticipantId,
                "sessionParticipantId is required"
        );
        Objects.requireNonNull(teamSide, "teamSide is required");
        if (teamSlot != 1 && teamSlot != 2) {
            throw new IllegalArgumentException("teamSlot must be 1 or 2");
        }
    }

    public static MatchParticipant assign(
            UUID matchId,
            UUID sessionParticipantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return new MatchParticipant(
                UUID.randomUUID(),
                matchId,
                sessionParticipantId,
                teamSide,
                teamSlot
        );
    }
}

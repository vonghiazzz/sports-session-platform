package com.sportssession.platform.matchplan.domain;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.Objects;
import java.util.UUID;

public record MatchPlanParticipant(
        UUID id,
        UUID matchPlanId,
        UUID sessionId,
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
    public MatchPlanParticipant {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(matchPlanId, "matchPlanId is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionParticipantId,
                "sessionParticipantId is required");
        Objects.requireNonNull(teamSide, "teamSide is required");
        if (teamSlot != 1 && teamSlot != 2) {
            throw new IllegalArgumentException("teamSlot must be 1 or 2");
        }
    }

    public static MatchPlanParticipant assign(
            UUID matchPlanId,
            UUID sessionId,
            UUID sessionParticipantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return new MatchPlanParticipant(
                UUID.randomUUID(), matchPlanId, sessionId,
                sessionParticipantId, teamSide, teamSlot
        );
    }
}

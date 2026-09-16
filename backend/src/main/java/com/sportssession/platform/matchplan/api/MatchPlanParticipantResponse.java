package com.sportssession.platform.matchplan.api;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;

import java.util.UUID;

public record MatchPlanParticipantResponse(
        UUID id,
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
    static MatchPlanParticipantResponse from(
            MatchPlanParticipant participant
    ) {
        return new MatchPlanParticipantResponse(
                participant.id(), participant.sessionParticipantId(),
                participant.teamSide(), participant.teamSlot()
        );
    }
}

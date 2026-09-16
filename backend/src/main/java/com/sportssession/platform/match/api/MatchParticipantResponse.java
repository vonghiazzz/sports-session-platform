package com.sportssession.platform.match.api;

import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record MatchParticipantResponse(
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
    static MatchParticipantResponse from(MatchParticipant participant) {
        return new MatchParticipantResponse(
                participant.sessionParticipantId(),
                participant.teamSide(),
                participant.teamSlot()
        );
    }
}

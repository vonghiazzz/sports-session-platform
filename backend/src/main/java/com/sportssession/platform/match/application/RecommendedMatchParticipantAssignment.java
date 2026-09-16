package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record RecommendedMatchParticipantAssignment(
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
}

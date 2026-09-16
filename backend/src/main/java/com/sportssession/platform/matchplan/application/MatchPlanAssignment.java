package com.sportssession.platform.matchplan.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record MatchPlanAssignment(
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
}

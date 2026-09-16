package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record SubmittedRecommendationAssignment(
        UUID sessionParticipantId,
        TeamSide teamSide,
        int teamSlot
) {
}

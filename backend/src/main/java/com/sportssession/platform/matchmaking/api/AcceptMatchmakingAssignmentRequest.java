package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.SubmittedRecommendationAssignment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AcceptMatchmakingAssignmentRequest(
        @NotNull(message = "sessionParticipantId is required")
        UUID sessionParticipantId,

        @NotNull(message = "teamSide is required")
        TeamSide teamSide,

        @NotNull(message = "teamSlot is required")
        @Min(value = 1, message = "teamSlot must be 1 or 2")
        @Max(value = 2, message = "teamSlot must be 1 or 2")
        Integer teamSlot
) {
    SubmittedRecommendationAssignment toEvidence() {
        return new SubmittedRecommendationAssignment(
                sessionParticipantId,
                teamSide,
                teamSlot
        );
    }
}

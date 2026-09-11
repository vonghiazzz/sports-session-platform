package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.SubmittedGlobalCourtRecommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GlobalMatchmakingQueueRecommendationRequest(
        @NotNull(message = "sessionCourtId is required")
        UUID sessionCourtId,

        @NotNull(message = "assignments are required")
        @Size(
                min = 4,
                max = 4,
                message = "Recommendation evidence requires exactly 4 assignments"
        )
        List<
                @NotNull(message = "recommendation assignment is required")
                @Valid AcceptMatchmakingAssignmentRequest
                > assignments
) {
    SubmittedGlobalCourtRecommendation toEvidence() {
        return new SubmittedGlobalCourtRecommendation(
                sessionCourtId,
                assignments.stream()
                        .map(AcceptMatchmakingAssignmentRequest::toEvidence)
                        .toList()
        );
    }
}

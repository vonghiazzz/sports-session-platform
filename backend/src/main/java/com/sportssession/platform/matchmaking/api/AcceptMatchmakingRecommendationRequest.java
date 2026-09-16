package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.SubmittedRecommendationEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AcceptMatchmakingRecommendationRequest(
        @NotBlank(message = "algorithmVersion is required")
        String algorithmVersion,

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
    SubmittedRecommendationEvidence toEvidence() {
        return new SubmittedRecommendationEvidence(
                algorithmVersion,
                assignments.stream()
                        .map(AcceptMatchmakingAssignmentRequest::toEvidence)
                        .toList()
        );
    }
}

package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.SubmittedGlobalRecommendationEvidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record GlobalMatchmakingQueueRequest(
        @NotBlank(message = "orchestrationVersion is required")
        String orchestrationVersion,

        @NotBlank(message = "selectionAlgorithmVersion is required")
        String selectionAlgorithmVersion,

        @NotEmpty(message = "targetCourtIds must not be empty")
        List<
                @NotNull(message = "target Court is required")
                UUID
                > targetCourtIds,

        @NotEmpty(message = "recommendations must not be empty")
        List<
                @NotNull(message = "global recommendation is required")
                @Valid GlobalMatchmakingQueueRecommendationRequest
                > recommendations
) {
    SubmittedGlobalRecommendationEvidence toEvidence() {
        return new SubmittedGlobalRecommendationEvidence(
                orchestrationVersion,
                selectionAlgorithmVersion,
                targetCourtIds,
                recommendations.stream()
                        .map(GlobalMatchmakingQueueRecommendationRequest::
                                toEvidence)
                        .toList()
        );
    }
}

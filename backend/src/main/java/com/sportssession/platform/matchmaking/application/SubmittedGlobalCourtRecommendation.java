package com.sportssession.platform.matchmaking.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SubmittedGlobalCourtRecommendation(
        UUID sessionCourtId,
        List<SubmittedRecommendationAssignment> assignments
) {
    public SubmittedGlobalCourtRecommendation {
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(assignments, "assignments are required");
        assignments = List.copyOf(assignments);
    }
}

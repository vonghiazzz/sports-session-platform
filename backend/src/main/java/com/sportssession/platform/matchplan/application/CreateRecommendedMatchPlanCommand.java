package com.sportssession.platform.matchplan.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateRecommendedMatchPlanCommand(
        UUID sessionId,
        UUID sessionCourtId,
        List<MatchPlanAssignment> participants
) {
    public CreateRecommendedMatchPlanCommand {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        participants = List.copyOf(Objects.requireNonNull(
                participants, "participants are required"
        ));
    }
}

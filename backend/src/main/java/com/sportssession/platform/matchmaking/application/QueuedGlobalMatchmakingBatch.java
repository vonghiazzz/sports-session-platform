package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchplan.application.MatchPlanDetails;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record QueuedGlobalMatchmakingBatch(
        UUID sessionId,
        String orchestrationVersion,
        String selectionAlgorithmVersion,
        List<MatchPlanDetails> createdPlans
) {
    public QueuedGlobalMatchmakingBatch {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(
                orchestrationVersion,
                "orchestrationVersion is required"
        );
        Objects.requireNonNull(
                selectionAlgorithmVersion,
                "selectionAlgorithmVersion is required"
        );
        Objects.requireNonNull(createdPlans, "createdPlans are required");
        createdPlans = List.copyOf(createdPlans);
    }
}

package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.QueuedGlobalMatchmakingBatch;
import com.sportssession.platform.matchplan.api.MatchPlanResponse;

import java.util.List;
import java.util.UUID;

public record GlobalMatchmakingQueueResponse(
        UUID sessionId,
        String orchestrationVersion,
        String selectionAlgorithmVersion,
        List<MatchPlanResponse> createdPlans
) {
    static GlobalMatchmakingQueueResponse from(
            QueuedGlobalMatchmakingBatch batch
    ) {
        return new GlobalMatchmakingQueueResponse(
                batch.sessionId(),
                batch.orchestrationVersion(),
                batch.selectionAlgorithmVersion(),
                batch.createdPlans().stream()
                        .map(MatchPlanResponse::from)
                        .toList()
        );
    }
}

package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.GlobalMatchmakingOutcome;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingPreview;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingUnavailableReason;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GlobalMatchmakingGenerationResponse(
        GlobalMatchmakingOutcome outcome,
        String orchestrationVersion,
        String selectionAlgorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        int initialEligiblePlayerCount,
        List<MatchmakingGenerationResponse> courtResults,
        GlobalMatchmakingUnavailableReason reason
) {
    static GlobalMatchmakingGenerationResponse from(
            GlobalMatchmakingPreview preview
    ) {
        return new GlobalMatchmakingGenerationResponse(
                preview.outcome(),
                preview.orchestrationVersion(),
                preview.selectionAlgorithmVersion(),
                preview.evaluationTime(),
                preview.sessionId(),
                preview.initialEligiblePlayerCount(),
                preview.courtResults().stream()
                        .map(MatchmakingGenerationResponse::from)
                        .toList(),
                preview.reason()
        );
    }
}

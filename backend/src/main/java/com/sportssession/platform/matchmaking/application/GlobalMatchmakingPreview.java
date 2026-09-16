package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchmakingResult;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record GlobalMatchmakingPreview(
        GlobalMatchmakingOutcome outcome,
        String orchestrationVersion,
        String selectionAlgorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        int initialEligiblePlayerCount,
        List<MatchmakingResult> courtResults,
        GlobalMatchmakingUnavailableReason reason
) {
    public GlobalMatchmakingPreview {
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(
                orchestrationVersion,
                "orchestrationVersion is required"
        );
        Objects.requireNonNull(
                selectionAlgorithmVersion,
                "selectionAlgorithmVersion is required"
        );
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(courtResults, "courtResults are required");
        courtResults = List.copyOf(courtResults);
        if (initialEligiblePlayerCount < 0) {
            throw new IllegalArgumentException(
                    "initialEligiblePlayerCount must not be negative"
            );
        }
        if ((outcome == GlobalMatchmakingOutcome.UNAVAILABLE)
                != (reason != null)) {
            throw new IllegalArgumentException(
                    "reason must be present exactly for UNAVAILABLE outcome"
            );
        }
    }
}

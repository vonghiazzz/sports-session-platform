package com.sportssession.platform.matchmaking.application;

import java.util.Objects;

public class MatchmakingRecommendationAcceptanceException
        extends RuntimeException {

    private final MatchmakingRecommendationAcceptanceFailureReason reason;

    public MatchmakingRecommendationAcceptanceException(
            MatchmakingRecommendationAcceptanceFailureReason reason,
            String message
    ) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason is required");
    }

    public MatchmakingRecommendationAcceptanceFailureReason reason() {
        return reason;
    }
}

package com.sportssession.platform.matchmaking.application;

import java.util.Objects;

public class MatchmakingRecommendationException extends RuntimeException {

    private final MatchmakingRecommendationFailureReason reason;

    public MatchmakingRecommendationException(
            MatchmakingRecommendationFailureReason reason,
            String message
    ) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason is required");
    }

    public MatchmakingRecommendationFailureReason reason() {
        return reason;
    }
}

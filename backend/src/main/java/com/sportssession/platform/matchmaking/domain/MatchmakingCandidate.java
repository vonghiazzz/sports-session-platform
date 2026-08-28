package com.sportssession.platform.matchmaking.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MatchmakingCandidate(
        UUID sessionParticipantId,
        UUID playerId,
        Instant waitingSince,
        BigDecimal ratingValue,
        BigDecimal uncertainty,
        int ratedMatches,
        RatingBasis ratingBasis
) {
    public MatchmakingCandidate {
        require(sessionParticipantId != null,
                "sessionParticipantId is required");
        require(playerId != null, "playerId is required");
        require(waitingSince != null, "waitingSince is required");
        require(ratingValue != null, "ratingValue is required");
        require(uncertainty != null, "uncertainty is required");
        require(uncertainty.signum() > 0,
                "uncertainty must be greater than zero");
        require(ratedMatches >= 0,
                "ratedMatches must not be negative");
        require(ratingBasis != null, "ratingBasis is required");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidMatchmakingInputException(message);
        }
    }
}

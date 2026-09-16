package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.RatingBasis;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record MatchmakingRatingSnapshot(
        UUID playerId,
        BigDecimal ratingValue,
        BigDecimal uncertainty,
        int ratedMatches,
        RatingBasis ratingBasis
) {
    public MatchmakingRatingSnapshot {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(ratingValue, "ratingValue is required");
        Objects.requireNonNull(uncertainty, "uncertainty is required");
        Objects.requireNonNull(ratingBasis, "ratingBasis is required");
        if (uncertainty.signum() <= 0) {
            throw new IllegalArgumentException(
                    "uncertainty must be greater than zero"
            );
        }
        if (ratedMatches < 0) {
            throw new IllegalArgumentException(
                    "ratedMatches must not be negative"
            );
        }
    }
}

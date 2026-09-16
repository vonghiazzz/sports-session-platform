package com.sportssession.platform.player.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record PlayerManagementRatingSnapshot(
        UUID playerId,
        SportCode sportCode,
        MatchFormat matchFormat,
        BigDecimal ratingValue,
        BigDecimal uncertainty,
        int ratedMatches,
        PlayerManagementRatingBasis ratingBasis,
        String ratingAlgorithmVersion
) {
    public PlayerManagementRatingSnapshot {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
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
        if (ratingBasis == PlayerManagementRatingBasis.PERSISTED
                && (ratingAlgorithmVersion == null
                || ratingAlgorithmVersion.isBlank())) {
            throw new IllegalArgumentException(
                    "persisted Rating requires ratingAlgorithmVersion"
            );
        }
        if (ratingBasis == PlayerManagementRatingBasis.INITIAL_PRIOR) {
            if (ratedMatches != 0) {
                throw new IllegalArgumentException(
                        "initial prior ratedMatches must be zero"
                );
            }
            if (ratingAlgorithmVersion != null) {
                throw new IllegalArgumentException(
                        "initial prior must not have ratingAlgorithmVersion"
                );
            }
        }
    }
}

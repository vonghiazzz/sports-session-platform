package com.sportssession.platform.player.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlayerManagementRatingHistoryItem(
        UUID matchId,
        Instant matchCompletedAt,
        PlayerManagementRatingOutcome outcome,
        BigDecimal beforeRatingValue,
        BigDecimal beforeUncertainty,
        BigDecimal afterRatingValue,
        BigDecimal afterUncertainty,
        int resultVersion,
        String algorithmVersion,
        Instant createdAt
) {
    public PlayerManagementRatingHistoryItem {
        Objects.requireNonNull(matchId, "matchId is required");
        Objects.requireNonNull(
                matchCompletedAt,
                "matchCompletedAt is required"
        );
        Objects.requireNonNull(outcome, "outcome is required");
        Objects.requireNonNull(
                beforeRatingValue,
                "beforeRatingValue is required"
        );
        Objects.requireNonNull(
                beforeUncertainty,
                "beforeUncertainty is required"
        );
        Objects.requireNonNull(
                afterRatingValue,
                "afterRatingValue is required"
        );
        Objects.requireNonNull(
                afterUncertainty,
                "afterUncertainty is required"
        );
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (beforeUncertainty.signum() <= 0
                || afterUncertainty.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Rating uncertainty must be greater than zero"
            );
        }
        if (resultVersion < 1) {
            throw new IllegalArgumentException(
                    "resultVersion must be positive"
            );
        }
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "algorithmVersion must not be blank"
            );
        }
    }
}

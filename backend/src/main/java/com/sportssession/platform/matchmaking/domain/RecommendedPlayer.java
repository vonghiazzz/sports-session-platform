package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.match.domain.TeamSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecommendedPlayer(
        UUID sessionParticipantId,
        UUID playerId,
        TeamSide teamSide,
        int teamSlot,
        Instant waitingSince,
        long waitingSeconds,
        BigDecimal ratingValue,
        BigDecimal uncertainty,
        int ratedMatches,
        RatingBasis ratingBasis
) {
    public RecommendedPlayer {
        Objects.requireNonNull(
                sessionParticipantId,
                "sessionParticipantId is required"
        );
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(teamSide, "teamSide is required");
        Objects.requireNonNull(waitingSince, "waitingSince is required");
        Objects.requireNonNull(ratingValue, "ratingValue is required");
        Objects.requireNonNull(uncertainty, "uncertainty is required");
        Objects.requireNonNull(ratingBasis, "ratingBasis is required");
        if (teamSlot != 1 && teamSlot != 2) {
            throw new IllegalArgumentException("teamSlot must be 1 or 2");
        }
        if (waitingSeconds < 0) {
            throw new IllegalArgumentException(
                    "waitingSeconds must not be negative");
        }
        if (uncertainty.signum() <= 0) {
            throw new IllegalArgumentException(
                    "uncertainty must be greater than zero");
        }
        if (ratedMatches < 0) {
            throw new IllegalArgumentException(
                    "ratedMatches must not be negative");
        }
    }
}

package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MatchRecommendation(
        String algorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        UUID sessionCourtId,
        SportCode sportCode,
        MatchFormat matchFormat,
        int eligiblePlayerCount,
        RecommendedTeam teamA,
        RecommendedTeam teamB,
        BigDecimal teamARatingTotal,
        BigDecimal teamBRatingTotal,
        BigDecimal ratingDifference,
        Instant oldestWaitingSince
) implements MatchmakingResult {
    public MatchRecommendation {
        Objects.requireNonNull(algorithmVersion, "algorithmVersion is required");
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(teamA, "teamA is required");
        Objects.requireNonNull(teamB, "teamB is required");
        Objects.requireNonNull(
                teamARatingTotal,
                "teamARatingTotal is required"
        );
        Objects.requireNonNull(
                teamBRatingTotal,
                "teamBRatingTotal is required"
        );
        Objects.requireNonNull(ratingDifference, "ratingDifference is required");
        Objects.requireNonNull(
                oldestWaitingSince,
                "oldestWaitingSince is required"
        );
        if (eligiblePlayerCount < 4) {
            throw new IllegalArgumentException(
                    "A recommendation requires at least four eligible Players");
        }
        if (teamA.teamSide() != TeamSide.A || teamB.teamSide() != TeamSide.B) {
            throw new IllegalArgumentException(
                    "Recommendation Teams must be canonical A and B");
        }
        if (teamARatingTotal.compareTo(teamA.ratingTotal()) != 0
                || teamBRatingTotal.compareTo(teamB.ratingTotal()) != 0) {
            throw new IllegalArgumentException(
                    "Recommendation Team totals are inconsistent");
        }
        if (ratingDifference.compareTo(
                teamARatingTotal.subtract(teamBRatingTotal).abs()
        ) != 0) {
            throw new IllegalArgumentException(
                    "ratingDifference is inconsistent");
        }

        Set<UUID> playerIds = new HashSet<>();
        playerIds.add(teamA.slot1().playerId());
        playerIds.add(teamA.slot2().playerId());
        playerIds.add(teamB.slot1().playerId());
        playerIds.add(teamB.slot2().playerId());
        if (playerIds.size() != 4) {
            throw new IllegalArgumentException(
                    "Recommendation requires four unique Players");
        }
        Set<RecommendedPlayer> recommendedPlayers = Set.of(
                teamA.slot1(),
                teamA.slot2(),
                teamB.slot1(),
                teamB.slot2()
        );
        boolean containsOldest = recommendedPlayers.stream()
                .anyMatch(player -> player.waitingSince()
                        .equals(oldestWaitingSince));
        if (!containsOldest) {
            throw new IllegalArgumentException(
                    "Recommendation must contain an oldest-waiting Player");
        }
    }
}

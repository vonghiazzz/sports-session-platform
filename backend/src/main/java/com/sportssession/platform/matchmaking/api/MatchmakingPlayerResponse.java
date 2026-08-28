package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MatchmakingPlayerResponse(
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
    static MatchmakingPlayerResponse from(RecommendedPlayer player) {
        return new MatchmakingPlayerResponse(
                player.sessionParticipantId(),
                player.playerId(),
                player.teamSide(),
                player.teamSlot(),
                player.waitingSince(),
                player.waitingSeconds(),
                player.ratingValue(),
                player.uncertainty(),
                player.ratedMatches(),
                player.ratingBasis()
        );
    }
}

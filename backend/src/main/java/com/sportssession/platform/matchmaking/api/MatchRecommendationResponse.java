package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MatchRecommendationResponse(
        MatchmakingGenerationOutcome outcome,
        String algorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        UUID sessionCourtId,
        SportCode sportCode,
        MatchFormat matchFormat,
        int eligiblePlayerCount,
        MatchmakingTeamResponse teamA,
        MatchmakingTeamResponse teamB,
        BigDecimal teamARatingTotal,
        BigDecimal teamBRatingTotal,
        BigDecimal ratingDifference,
        Instant oldestWaitingSince
) implements MatchmakingGenerationResponse {
    static MatchRecommendationResponse from(
            MatchRecommendation recommendation
    ) {
        return new MatchRecommendationResponse(
                MatchmakingGenerationOutcome.RECOMMENDED,
                recommendation.algorithmVersion(),
                recommendation.evaluationTime(),
                recommendation.sessionId(),
                recommendation.sessionCourtId(),
                recommendation.sportCode(),
                recommendation.matchFormat(),
                recommendation.eligiblePlayerCount(),
                MatchmakingTeamResponse.from(recommendation.teamA()),
                MatchmakingTeamResponse.from(recommendation.teamB()),
                recommendation.teamARatingTotal(),
                recommendation.teamBRatingTotal(),
                recommendation.ratingDifference(),
                recommendation.oldestWaitingSince()
        );
    }
}

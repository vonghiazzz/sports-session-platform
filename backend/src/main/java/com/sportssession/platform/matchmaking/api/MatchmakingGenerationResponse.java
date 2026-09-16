package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;

public sealed interface MatchmakingGenerationResponse
        permits MatchRecommendationResponse, MatchmakingUnavailableResponse {

    static MatchmakingGenerationResponse from(MatchmakingResult result) {
        return switch (result) {
            case MatchRecommendation recommendation ->
                    MatchRecommendationResponse.from(recommendation);
            case MatchmakingUnavailable unavailable ->
                    MatchmakingUnavailableResponse.from(unavailable);
        };
    }
}

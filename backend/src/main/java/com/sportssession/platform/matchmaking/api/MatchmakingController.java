package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/sessions/{sessionId}/courts/{sessionCourtId}/match-recommendations"
)
public class MatchmakingController {

    private final MatchmakingRecommendationService recommendationService;

    public MatchmakingController(
            MatchmakingRecommendationService recommendationService
    ) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public MatchmakingGenerationResponse generateRecommendation(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId
    ) {
        MatchmakingResult result = recommendationService.recommend(
                sessionId,
                sessionCourtId
        );
        return MatchmakingGenerationResponse.from(result);
    }
}

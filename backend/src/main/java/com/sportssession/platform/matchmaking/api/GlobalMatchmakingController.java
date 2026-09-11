package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.GlobalMatchmakingPreview;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/match-recommendations")
public class GlobalMatchmakingController {

    private final GlobalMatchmakingRecommendationService recommendationService;

    public GlobalMatchmakingController(
            GlobalMatchmakingRecommendationService recommendationService
    ) {
        this.recommendationService = recommendationService;
    }

    @PostMapping
    public GlobalMatchmakingGenerationResponse generatePreview(
            @PathVariable UUID sessionId
    ) {
        GlobalMatchmakingPreview preview = recommendationService.preview(
                sessionId
        );
        return GlobalMatchmakingGenerationResponse.from(preview);
    }
}

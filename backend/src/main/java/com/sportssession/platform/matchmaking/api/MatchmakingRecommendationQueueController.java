package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationQueueService;
import com.sportssession.platform.matchplan.api.MatchPlanResponse;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/sessions/{sessionId}/courts/{sessionCourtId}/match-recommendations"
)
public class MatchmakingRecommendationQueueController {

    private final MatchmakingRecommendationQueueService queueService;

    public MatchmakingRecommendationQueueController(
            MatchmakingRecommendationQueueService queueService
    ) {
        this.queueService = queueService;
    }

    @PostMapping("/queue")
    public ResponseEntity<MatchPlanResponse> addToQueue(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId,
            @Valid @RequestBody AcceptMatchmakingRecommendationRequest request
    ) {
        MatchPlanDetails plan = queueService.addToQueue(
                sessionId, sessionCourtId, request.toEvidence()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MatchPlanResponse.from(plan));
    }
}

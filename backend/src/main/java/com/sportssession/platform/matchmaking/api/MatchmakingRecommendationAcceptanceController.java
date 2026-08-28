package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.match.api.MatchResponse;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationAcceptanceService;
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
public class MatchmakingRecommendationAcceptanceController {

    private final MatchmakingRecommendationAcceptanceService acceptanceService;

    public MatchmakingRecommendationAcceptanceController(
            MatchmakingRecommendationAcceptanceService acceptanceService
    ) {
        this.acceptanceService = acceptanceService;
    }

    @PostMapping("/accept")
    public ResponseEntity<MatchResponse> acceptAndStart(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId,
            @Valid @RequestBody AcceptMatchmakingRecommendationRequest request
    ) {
        StartedMatch started = acceptanceService.acceptAndStart(
                sessionId,
                sessionCourtId,
                request.toEvidence()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MatchResponse.from(
                        started.match(),
                        started.participants()
                ));
    }
}

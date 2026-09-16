package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationQueueService;
import com.sportssession.platform.matchmaking.application.QueuedGlobalMatchmakingBatch;
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
@RequestMapping("/api/sessions/{sessionId}/match-recommendations")
public class GlobalMatchmakingQueueController {

    private final GlobalMatchmakingRecommendationQueueService queueService;

    public GlobalMatchmakingQueueController(
            GlobalMatchmakingRecommendationQueueService queueService
    ) {
        this.queueService = queueService;
    }

    @PostMapping("/queue")
    public ResponseEntity<GlobalMatchmakingQueueResponse> addAllToQueue(
            @PathVariable UUID sessionId,
            @Valid @RequestBody GlobalMatchmakingQueueRequest request
    ) {
        QueuedGlobalMatchmakingBatch batch = queueService.addAllToQueue(
                sessionId,
                request.toEvidence()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalMatchmakingQueueResponse.from(batch));
    }
}

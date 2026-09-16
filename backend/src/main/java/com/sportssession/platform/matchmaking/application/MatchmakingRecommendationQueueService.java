package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchplan.application.CreateRecommendedMatchPlanCommand;
import com.sportssession.platform.matchplan.application.MatchPlanAssignment;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class MatchmakingRecommendationQueueService {

    private final MatchmakingRecommendationService recommendationService;
    private final MatchPlanService matchPlanService;

    public MatchmakingRecommendationQueueService(
            MatchmakingRecommendationService recommendationService,
            MatchPlanService matchPlanService
    ) {
        this.recommendationService = recommendationService;
        this.matchPlanService = matchPlanService;
    }

    @Transactional
    public MatchPlanDetails addToQueue(
            UUID sessionId,
            UUID sessionCourtId,
            SubmittedRecommendationEvidence submittedEvidence
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(
                submittedEvidence,
                "submittedEvidence is required"
        );

        MatchmakingResult regenerated = recommendationService.recommend(
                sessionId, sessionCourtId
        );
        MatchRecommendation recommendation =
                MatchmakingRecommendationEvidenceVerifier.requireCurrent(
                        regenerated, submittedEvidence
                );

        return matchPlanService.createRecommended(
                new CreateRecommendedMatchPlanCommand(
                        recommendation.sessionId(),
                        recommendation.sessionCourtId(),
                        MatchmakingRecommendationEvidenceVerifier.players(
                                recommendation
                        ).stream().map(player -> new MatchPlanAssignment(
                                player.sessionParticipantId(),
                                player.teamSide(),
                                player.teamSlot()
                        )).toList()
                )
        );
    }
}

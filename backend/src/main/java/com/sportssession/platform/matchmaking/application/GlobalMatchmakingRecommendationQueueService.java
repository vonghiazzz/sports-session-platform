package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchplan.application.CreateRecommendedMatchPlanCommand;
import com.sportssession.platform.matchplan.application.MatchPlanAssignment;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import com.sportssession.platform.session.application.SessionRuntimeLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class GlobalMatchmakingRecommendationQueueService {

    private final SessionRuntimeLookup sessionRuntimeLookup;
    private final GlobalMatchmakingRecommendationService recommendationService;
    private final MatchPlanService matchPlanService;

    public GlobalMatchmakingRecommendationQueueService(
            SessionRuntimeLookup sessionRuntimeLookup,
            GlobalMatchmakingRecommendationService recommendationService,
            MatchPlanService matchPlanService
    ) {
        this.sessionRuntimeLookup = sessionRuntimeLookup;
        this.recommendationService = recommendationService;
        this.matchPlanService = matchPlanService;
    }

    @Transactional
    public QueuedGlobalMatchmakingBatch addAllToQueue(
            UUID sessionId,
            SubmittedGlobalRecommendationEvidence submittedEvidence
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(
                submittedEvidence,
                "submittedEvidence is required"
        );

        sessionRuntimeLookup.requireSessionForUpdate(sessionId);
        GlobalMatchmakingPreview regenerated = recommendationService.preview(
                sessionId
        );
        List<MatchRecommendation> recommendations =
                GlobalMatchmakingRecommendationEvidenceVerifier.requireCurrent(
                        regenerated,
                        submittedEvidence
                );

        List<MatchPlanDetails> createdPlans = new ArrayList<>();
        for (MatchRecommendation recommendation : recommendations) {
            createdPlans.add(matchPlanService.createRecommended(
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
            ));
        }

        return new QueuedGlobalMatchmakingBatch(
                sessionId,
                regenerated.orchestrationVersion(),
                regenerated.selectionAlgorithmVersion(),
                createdPlans
        );
    }
}

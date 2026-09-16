package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.application.CreateAndStartRecommendedMatchCommand;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.RecommendedMatchParticipantAssignment;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MatchmakingRecommendationAcceptanceService {

    private final MatchmakingRecommendationService recommendationService;
    private final MatchService matchService;

    public MatchmakingRecommendationAcceptanceService(
            MatchmakingRecommendationService recommendationService,
            MatchService matchService
    ) {
        this.recommendationService = recommendationService;
        this.matchService = matchService;
    }

    @Transactional
    public StartedMatch acceptAndStart(
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
                sessionId,
                sessionCourtId
        );
        MatchRecommendation recommendation =
                MatchmakingRecommendationEvidenceVerifier.requireCurrent(
                        regenerated, submittedEvidence
                );
        List<RecommendedPlayer> recommendedPlayers =
                MatchmakingRecommendationEvidenceVerifier.players(
                        recommendation
                );

        List<RecommendedMatchParticipantAssignment> assignments =
                recommendedPlayers.stream()
                        .map(player ->
                                new RecommendedMatchParticipantAssignment(
                                        player.sessionParticipantId(),
                                        player.teamSide(),
                                        player.teamSlot()
                                ))
                        .toList();
        return matchService.createAndStartRecommendedMatch(
                new CreateAndStartRecommendedMatchCommand(
                        recommendation.sessionId(),
                        recommendation.sessionCourtId(),
                        assignments
                )
        );
    }

}

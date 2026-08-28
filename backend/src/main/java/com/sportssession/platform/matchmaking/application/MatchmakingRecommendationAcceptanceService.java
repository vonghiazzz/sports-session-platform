package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.application.CreateAndStartRecommendedMatchCommand;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.RecommendedMatchParticipantAssignment;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
        if (!(regenerated instanceof MatchRecommendation recommendation)) {
            throw stale();
        }
        if (!recommendation.algorithmVersion().equals(
                submittedEvidence.algorithmVersion()
        )) {
            throw stale();
        }

        Map<TeamSlot, UUID> submittedComposition = submittedEvidence
                .assignments()
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        assignment -> new TeamSlot(
                                assignment.teamSide(),
                                assignment.teamSlot()
                        ),
                        SubmittedRecommendationAssignment::sessionParticipantId
                ));
        List<RecommendedPlayer> recommendedPlayers = List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        );
        Map<TeamSlot, RecommendedPlayer> regeneratedComposition =
                recommendedPlayers.stream()
                        .collect(Collectors.toUnmodifiableMap(
                                player -> new TeamSlot(
                                        player.teamSide(),
                                        player.teamSlot()
                                ),
                                Function.identity()
                        ));
        boolean sameComposition = regeneratedComposition.entrySet().stream()
                .allMatch(entry -> Objects.equals(
                        submittedComposition.get(entry.getKey()),
                        entry.getValue().sessionParticipantId()
                ));
        if (!sameComposition
                || submittedComposition.size() != regeneratedComposition.size()) {
            throw stale();
        }

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

    private MatchmakingRecommendationAcceptanceException stale() {
        return new MatchmakingRecommendationAcceptanceException(
                MatchmakingRecommendationAcceptanceFailureReason
                        .RECOMMENDATION_STALE,
                "Submitted recommendation is stale"
        );
    }

    private record TeamSlot(TeamSide teamSide, int teamSlot) {
    }
}

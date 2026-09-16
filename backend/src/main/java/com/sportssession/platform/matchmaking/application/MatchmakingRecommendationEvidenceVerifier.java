package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

final class MatchmakingRecommendationEvidenceVerifier {

    private MatchmakingRecommendationEvidenceVerifier() {
    }

    static MatchRecommendation requireCurrent(
            MatchmakingResult regenerated,
            SubmittedRecommendationEvidence submittedEvidence
    ) {
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
        Map<TeamSlot, RecommendedPlayer> regeneratedComposition = players(
                recommendation
        ).stream().collect(Collectors.toUnmodifiableMap(
                player -> new TeamSlot(
                        player.teamSide(), player.teamSlot()
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
        return recommendation;
    }

    static List<RecommendedPlayer> players(MatchRecommendation recommendation) {
        return List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        );
    }

    private static MatchmakingRecommendationAcceptanceException stale() {
        return new MatchmakingRecommendationAcceptanceException(
                MatchmakingRecommendationAcceptanceFailureReason
                        .RECOMMENDATION_STALE,
                "Submitted recommendation is stale"
        );
    }

    private record TeamSlot(TeamSide teamSide, int teamSlot) {
    }
}

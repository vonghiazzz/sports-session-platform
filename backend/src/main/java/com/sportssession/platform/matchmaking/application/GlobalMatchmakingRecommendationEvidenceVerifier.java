package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class GlobalMatchmakingRecommendationEvidenceVerifier {

    private GlobalMatchmakingRecommendationEvidenceVerifier() {
    }

    static List<MatchRecommendation> requireCurrent(
            GlobalMatchmakingPreview regenerated,
            SubmittedGlobalRecommendationEvidence submitted
    ) {
        if (!regenerated.orchestrationVersion().equals(
                submitted.orchestrationVersion()
        ) || !regenerated.selectionAlgorithmVersion().equals(
                submitted.selectionAlgorithmVersion()
        )) {
            throw stale();
        }

        List<UUID> currentTargetCourtIds = regenerated.courtResults()
                .stream()
                .map(MatchmakingResult::sessionCourtId)
                .toList();
        if (!currentTargetCourtIds.equals(submitted.targetCourtIds())) {
            throw stale();
        }

        List<MatchRecommendation> currentRecommendations = regenerated
                .courtResults()
                .stream()
                .filter(MatchRecommendation.class::isInstance)
                .map(MatchRecommendation.class::cast)
                .toList();
        if (currentRecommendations.size()
                != submitted.recommendations().size()) {
            throw stale();
        }

        List<MatchRecommendation> verified = new ArrayList<>();
        for (int index = 0; index < currentRecommendations.size(); index++) {
            MatchRecommendation current = currentRecommendations.get(index);
            SubmittedGlobalCourtRecommendation expected =
                    submitted.recommendations().get(index);
            if (!current.sessionCourtId().equals(expected.sessionCourtId())) {
                throw stale();
            }
            try {
                verified.add(MatchmakingRecommendationEvidenceVerifier
                        .requireCurrent(
                                current,
                                new SubmittedRecommendationEvidence(
                                        submitted.selectionAlgorithmVersion(),
                                        expected.assignments()
                                )
                        ));
            } catch (MatchmakingRecommendationAcceptanceException exception) {
                throw stale();
            }
        }
        return List.copyOf(verified);
    }

    private static MatchmakingRecommendationAcceptanceException stale() {
        return new MatchmakingRecommendationAcceptanceException(
                MatchmakingRecommendationAcceptanceFailureReason
                        .RECOMMENDATION_STALE,
                "Submitted global recommendation is stale"
        );
    }
}

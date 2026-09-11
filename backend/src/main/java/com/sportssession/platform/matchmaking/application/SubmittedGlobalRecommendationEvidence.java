package com.sportssession.platform.matchmaking.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record SubmittedGlobalRecommendationEvidence(
        String orchestrationVersion,
        String selectionAlgorithmVersion,
        List<UUID> targetCourtIds,
        List<SubmittedGlobalCourtRecommendation> recommendations
) {
    public SubmittedGlobalRecommendationEvidence {
        if (orchestrationVersion == null || orchestrationVersion.isBlank()) {
            throw invalid("orchestrationVersion must not be blank");
        }
        if (selectionAlgorithmVersion == null
                || selectionAlgorithmVersion.isBlank()) {
            throw invalid("selectionAlgorithmVersion must not be blank");
        }
        if (targetCourtIds == null || targetCourtIds.isEmpty()) {
            throw invalid("targetCourtIds must not be empty");
        }
        if (recommendations == null || recommendations.isEmpty()) {
            throw invalid(
                    "Global recommendation evidence requires at least one "
                            + "recommended Court"
            );
        }

        orchestrationVersion = orchestrationVersion.strip();
        String normalizedSelectionAlgorithmVersion =
                selectionAlgorithmVersion.strip();
        selectionAlgorithmVersion = normalizedSelectionAlgorithmVersion;

        List<UUID> normalizedTargetCourtIds =
                new ArrayList<>(targetCourtIds.size());
        Map<UUID, Integer> targetPositionByCourtId = new HashMap<>();
        for (int index = 0; index < targetCourtIds.size(); index++) {
            UUID courtId = targetCourtIds.get(index);
            if (courtId == null) {
                throw invalid("targetCourtId must not be null");
            }
            if (targetPositionByCourtId.putIfAbsent(courtId, index) != null) {
                throw invalid("targetCourtIds must be unique");
            }
            normalizedTargetCourtIds.add(courtId);
        }
        targetCourtIds = List.copyOf(normalizedTargetCourtIds);

        Set<UUID> recommendationCourtIds = new HashSet<>();
        Set<UUID> participantIds = new HashSet<>();
        List<SubmittedGlobalCourtRecommendation> normalizedRecommendations =
                new ArrayList<>(recommendations.size());
        int lastTargetPosition = -1;

        for (SubmittedGlobalCourtRecommendation recommendation
                : recommendations) {
            if (recommendation == null) {
                throw invalid("Global recommendation must not be null");
            }

            UUID recommendationCourtId = recommendation.sessionCourtId();
            Integer targetPosition =
                    targetPositionByCourtId.get(recommendationCourtId);
            if (targetPosition == null) {
                throw invalid(
                        "Global recommendation Court must be present in "
                                + "targetCourtIds"
                );
            }
            if (!recommendationCourtIds.add(recommendationCourtId)) {
                throw invalid(
                        "Global recommendations must use unique "
                                + "sessionCourtIds"
                );
            }
            if (targetPosition <= lastTargetPosition) {
                throw invalid(
                        "Global recommendations must follow targetCourtIds "
                                + "order"
                );
            }
            lastTargetPosition = targetPosition;

            SubmittedRecommendationEvidence validated =
                    new SubmittedRecommendationEvidence(
                            normalizedSelectionAlgorithmVersion,
                            recommendation.assignments()
                    );
            for (SubmittedRecommendationAssignment assignment
                    : validated.assignments()) {
                if (!participantIds.add(assignment.sessionParticipantId())) {
                    throw invalid(
                            "A SessionParticipant must not appear in more "
                                    + "than one global recommendation"
                    );
                }
            }

            normalizedRecommendations.add(
                    new SubmittedGlobalCourtRecommendation(
                            recommendationCourtId,
                            validated.assignments()
                    )
            );
        }

        recommendations = List.copyOf(normalizedRecommendations);
    }

    private static InvalidRecommendationAcceptanceRequestException invalid(
            String message
    ) {
        return new InvalidRecommendationAcceptanceRequestException(message);
    }
}

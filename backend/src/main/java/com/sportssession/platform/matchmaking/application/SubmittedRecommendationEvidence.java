package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SubmittedRecommendationEvidence(
        String algorithmVersion,
        List<SubmittedRecommendationAssignment> assignments
) {
    private static final Set<TeamSlot> REQUIRED_TEAM_SLOTS = Set.of(
            new TeamSlot(TeamSide.A, 1),
            new TeamSlot(TeamSide.A, 2),
            new TeamSlot(TeamSide.B, 1),
            new TeamSlot(TeamSide.B, 2)
    );

    public SubmittedRecommendationEvidence {
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw invalid("algorithmVersion must not be blank");
        }
        algorithmVersion = algorithmVersion.strip();
        if (assignments == null || assignments.size() != 4) {
            throw invalid("Recommendation evidence requires exactly 4 assignments");
        }

        Set<UUID> participantIds = new HashSet<>();
        Set<TeamSlot> teamSlots = new HashSet<>();
        for (SubmittedRecommendationAssignment assignment : assignments) {
            if (assignment == null
                    || assignment.sessionParticipantId() == null
                    || assignment.teamSide() == null) {
                throw invalid("Every recommendation assignment must be complete");
            }
            if (assignment.teamSlot() != 1 && assignment.teamSlot() != 2) {
                throw invalid("teamSlot must be 1 or 2");
            }
            if (!participantIds.add(assignment.sessionParticipantId())) {
                throw invalid(
                        "Recommendation assignments must use unique sessionParticipantIds"
                );
            }
            if (!teamSlots.add(new TeamSlot(
                    assignment.teamSide(),
                    assignment.teamSlot()
            ))) {
                throw invalid("Recommendation assignments must not duplicate a team slot");
            }
        }
        if (!teamSlots.equals(REQUIRED_TEAM_SLOTS)) {
            throw invalid("Recommendation must contain exactly team slots A1, A2, B1, B2");
        }
        assignments = assignments.stream()
                .sorted(Comparator
                        .comparing(SubmittedRecommendationAssignment::teamSide)
                        .thenComparingInt(
                                SubmittedRecommendationAssignment::teamSlot
                        ))
                .toList();
    }

    private static InvalidRecommendationAcceptanceRequestException invalid(
            String message
    ) {
        return new InvalidRecommendationAcceptanceRequestException(message);
    }

    private record TeamSlot(TeamSide teamSide, int teamSlot) {
    }
}

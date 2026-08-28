package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.domain.TeamSide;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmittedRecommendationEvidenceTest {

    @Test
    void normalizesAssignmentsByTeamAndSlot() {
        SubmittedRecommendationEvidence evidence =
                new SubmittedRecommendationEvidence(
                        "  algorithm-v1  ",
                        List.of(
                                assignment(4, TeamSide.B, 2),
                                assignment(2, TeamSide.A, 2),
                                assignment(3, TeamSide.B, 1),
                                assignment(1, TeamSide.A, 1)
                        )
                );

        assertThat(evidence.algorithmVersion()).isEqualTo("algorithm-v1");
        assertThat(evidence.assignments())
                .extracting(assignment ->
                        assignment.teamSide().name() + assignment.teamSlot())
                .containsExactly("A1", "A2", "B1", "B2");
    }

    @Test
    void blankAlgorithmVersionIsRejected() {
        assertInvalid(" ", validAssignments(), "algorithmVersion");
    }

    @Test
    void assignmentCountMustBeExactlyFour() {
        assertInvalid(
                "algorithm-v1",
                validAssignments().subList(0, 3),
                "exactly 4"
        );
    }

    @Test
    void participantIdsMustBeUnique() {
        assertInvalid(
                "algorithm-v1",
                List.of(
                        assignment(1, TeamSide.A, 1),
                        assignment(1, TeamSide.A, 2),
                        assignment(3, TeamSide.B, 1),
                        assignment(4, TeamSide.B, 2)
                ),
                "unique sessionParticipantIds"
        );
    }

    @Test
    void teamSlotsMustBeUniqueAndComplete() {
        assertInvalid(
                "algorithm-v1",
                List.of(
                        assignment(1, TeamSide.A, 1),
                        assignment(2, TeamSide.A, 1),
                        assignment(3, TeamSide.B, 1),
                        assignment(4, TeamSide.B, 2)
                ),
                "duplicate a team slot"
        );
    }

    @Test
    void invalidTeamSlotIsRejected() {
        assertInvalid(
                "algorithm-v1",
                List.of(
                        assignment(1, TeamSide.A, 1),
                        assignment(2, TeamSide.A, 2),
                        assignment(3, TeamSide.B, 1),
                        assignment(4, TeamSide.B, 3)
                ),
                "teamSlot must be 1 or 2"
        );
    }

    private void assertInvalid(
            String algorithmVersion,
            List<SubmittedRecommendationAssignment> assignments,
            String message
    ) {
        assertThatThrownBy(() -> new SubmittedRecommendationEvidence(
                algorithmVersion,
                assignments
        ))
                .isInstanceOf(
                        InvalidRecommendationAcceptanceRequestException.class
                )
                .hasMessageContaining(message);
    }

    private static List<SubmittedRecommendationAssignment> validAssignments() {
        return List.of(
                assignment(1, TeamSide.A, 1),
                assignment(2, TeamSide.A, 2),
                assignment(3, TeamSide.B, 1),
                assignment(4, TeamSide.B, 2)
        );
    }

    private static SubmittedRecommendationAssignment assignment(
            int participant,
            TeamSide teamSide,
            int teamSlot
    ) {
        return new SubmittedRecommendationAssignment(
                UUID.fromString(
                        "00000000-0000-0000-0000-%012x".formatted(participant)
                ),
                teamSide,
                teamSlot
        );
    }
}

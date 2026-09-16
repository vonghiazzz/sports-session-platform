package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmittedGlobalRecommendationEvidenceTest {

    @Test
    void validatesAndCanonicalizesEachCourtComposition() {
        SubmittedGlobalRecommendationEvidence evidence = evidence(
                court(10, 100),
                court(11, 200)
        );

        assertThat(evidence.targetCourtIds())
                .containsExactly(uuid(10), uuid(11));
        assertThat(evidence.recommendations()).hasSize(2);
        assertThat(evidence.recommendations().getFirst().assignments())
                .extracting(SubmittedRecommendationAssignment::teamSide)
                .containsExactly(
                        TeamSide.A, TeamSide.A, TeamSide.B, TeamSide.B
                );
    }

    @Test
    void rejectsBlankVersionsEmptyTargetsAndEmptyBatch() {
        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                " ",
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10)),
                List.of(court(10, 1))
        )).isInstanceOf(InvalidRecommendationAcceptanceRequestException.class);

        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                "global-v1",
                " ",
                List.of(uuid(10)),
                List.of(court(10, 1))
        )).isInstanceOf(InvalidRecommendationAcceptanceRequestException.class);

        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                "global-v1",
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(),
                List.of(court(10, 1))
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("targetCourtIds");

        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                "global-v1",
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10)),
                List.of()
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("at least one recommended Court");
    }

    @Test
    void rejectsDuplicateTargetCourts() {
        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10), uuid(10)),
                List.of(court(10, 100))
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("targetCourtIds must be unique");
    }

    @Test
    void rejectsRecommendationCourtOutsideTargets() {
        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10)),
                List.of(court(11, 100))
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("present in targetCourtIds");
    }

    @Test
    void rejectsRecommendationOrderThatDoesNotFollowTargets() {
        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10), uuid(11)),
                List.of(court(11, 200), court(10, 100))
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("follow targetCourtIds order");
    }

    @Test
    void allowsUnavailableTargetCourtWithoutRecommendation() {
        SubmittedGlobalRecommendationEvidence evidence =
                new SubmittedGlobalRecommendationEvidence(
                        GlobalMatchmakingRecommendationService
                                .ORCHESTRATION_VERSION,
                        MatchmakingEngine.ALGORITHM_VERSION,
                        List.of(uuid(10), uuid(11)),
                        List.of(court(10, 100))
                );

        assertThat(evidence.targetCourtIds())
                .containsExactly(uuid(10), uuid(11));
        assertThat(evidence.recommendations())
                .extracting(SubmittedGlobalCourtRecommendation::sessionCourtId)
                .containsExactly(uuid(10));
    }

    @Test
    void rejectsDuplicateRecommendationCourtAndParticipantAcrossBatch() {
        SubmittedGlobalCourtRecommendation first = court(10, 100);

        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10)),
                List.of(first, court(10, 200))
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("unique sessionCourtIds");

        assertThatThrownBy(() -> new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                List.of(uuid(10), uuid(11)),
                List.of(
                        first,
                        new SubmittedGlobalCourtRecommendation(
                                uuid(11),
                                List.of(
                                        assignment(100, TeamSide.A, 1),
                                        assignment(201, TeamSide.A, 2),
                                        assignment(202, TeamSide.B, 1),
                                        assignment(203, TeamSide.B, 2)
                                )
                        )
                )
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("more than one global recommendation");
    }

    @Test
    void rejectsMalformedSingleCourtComposition() {
        assertThatThrownBy(() -> evidence(
                new SubmittedGlobalCourtRecommendation(
                        uuid(10),
                        List.of(
                                assignment(100, TeamSide.A, 1),
                                assignment(101, TeamSide.A, 1),
                                assignment(102, TeamSide.B, 1),
                                assignment(103, TeamSide.B, 2)
                        )
                )
        ))
                .isInstanceOf(InvalidRecommendationAcceptanceRequestException.class)
                .hasMessageContaining("duplicate a team slot");
    }

    private static SubmittedGlobalRecommendationEvidence evidence(
            SubmittedGlobalCourtRecommendation... recommendations
    ) {
        List<SubmittedGlobalCourtRecommendation> recommendationList =
                Arrays.asList(recommendations);
        List<UUID> targetCourtIds = recommendationList.stream()
                .map(SubmittedGlobalCourtRecommendation::sessionCourtId)
                .toList();
        return new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                targetCourtIds,
                recommendationList
        );
    }

    private static SubmittedGlobalCourtRecommendation court(
            int courtId,
            int participantBase
    ) {
        return new SubmittedGlobalCourtRecommendation(
                uuid(courtId),
                List.of(
                        assignment(participantBase + 3, TeamSide.B, 2),
                        assignment(participantBase, TeamSide.A, 1),
                        assignment(participantBase + 2, TeamSide.B, 1),
                        assignment(participantBase + 1, TeamSide.A, 2)
                )
        );
    }

    private static SubmittedRecommendationAssignment assignment(
            int participantId,
            TeamSide side,
            int slot
    ) {
        return new SubmittedRecommendationAssignment(
                uuid(participantId), side, slot
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

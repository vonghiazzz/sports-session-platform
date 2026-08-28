package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.application.CreateAndStartRecommendedMatchCommand;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import com.sportssession.platform.matchmaking.domain.RecommendedTeam;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingRecommendationAcceptanceServiceTest {

    private static final UUID SESSION_ID = uuid(900);
    private static final UUID SESSION_COURT_ID = uuid(901);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    @Test
    void matchingEvidenceRegeneratesOnceAndStartsAuthoritativeComposition() {
        MatchRecommendation recommendation = recommendation(
                EVALUATION_TIME,
                "25.0"
        );
        RecordingRecommendationService recommendationService =
                new RecordingRecommendationService(recommendation);
        RecordingMatchService matchService = new RecordingMatchService();
        MatchmakingRecommendationAcceptanceService service =
                new MatchmakingRecommendationAcceptanceService(
                        recommendationService,
                        matchService
                );

        StartedMatch result = service.acceptAndStart(
                SESSION_ID,
                SESSION_COURT_ID,
                submittedEvidence(recommendation, true)
        );

        assertThat(result).isSameAs(matchService.result());
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
        assertThat(matchService.callCount()).isEqualTo(1);
        CreateAndStartRecommendedMatchCommand command = matchService.command();
        assertThat(command.sessionId()).isEqualTo(SESSION_ID);
        assertThat(command.sessionCourtId()).isEqualTo(SESSION_COURT_ID);
        assertThat(command.participants())
                .extracting(
                        assignment -> assignment.teamSide().name()
                                + assignment.teamSlot()
                                + ":" + assignment.sessionParticipantId()
                )
                .containsExactly(
                        "A1:" + uuid(101),
                        "A2:" + uuid(102),
                        "B1:" + uuid(103),
                        "B2:" + uuid(104)
                );
    }

    @Test
    void changedRatingEvidenceDoesNotMatterWhenCompositionIsUnchanged() {
        MatchRecommendation displayed = recommendation(
                EVALUATION_TIME,
                "25.0"
        );
        MatchRecommendation regenerated = recommendation(
                EVALUATION_TIME.plusSeconds(30),
                "31.25"
        );
        RecordingRecommendationService recommendationService =
                new RecordingRecommendationService(regenerated);
        RecordingMatchService matchService = new RecordingMatchService();

        new MatchmakingRecommendationAcceptanceService(
                recommendationService,
                matchService
        ).acceptAndStart(
                SESSION_ID,
                SESSION_COURT_ID,
                submittedEvidence(displayed, false)
        );

        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
        assertThat(matchService.callCount()).isEqualTo(1);
    }

    @Test
    void unavailableRegenerationIsStaleAndNeverCallsMatchOperation() {
        MatchmakingUnavailable unavailable = new MatchmakingUnavailable(
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                3,
                MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
        );
        assertStale(unavailable, submittedEvidence(
                recommendation(EVALUATION_TIME, "25.0"),
                false
        ));
    }

    @Test
    void differentAlgorithmVersionIsStale() {
        MatchRecommendation recommendation = recommendation(
                EVALUATION_TIME,
                "25.0"
        );
        SubmittedRecommendationEvidence evidence =
                new SubmittedRecommendationEvidence(
                        "old-algorithm",
                        submittedEvidence(recommendation, false).assignments()
                );
        assertStale(recommendation, evidence);
    }

    @Test
    void differentParticipantIsStale() {
        MatchRecommendation recommendation = recommendation(
                EVALUATION_TIME,
                "25.0"
        );
        List<SubmittedRecommendationAssignment> assignments =
                List.copyOf(submittedEvidence(recommendation, false).assignments());
        assignments = List.of(
                new SubmittedRecommendationAssignment(
                        uuid(999), TeamSide.A, 1
                ),
                assignments.get(1),
                assignments.get(2),
                assignments.get(3)
        );
        assertStale(recommendation, new SubmittedRecommendationEvidence(
                recommendation.algorithmVersion(),
                assignments
        ));
    }

    @Test
    void changedTeamOrSlotMappingIsStale() {
        MatchRecommendation recommendation = recommendation(
                EVALUATION_TIME,
                "25.0"
        );
        assertStale(recommendation, new SubmittedRecommendationEvidence(
                recommendation.algorithmVersion(),
                List.of(
                        new SubmittedRecommendationAssignment(
                                uuid(103), TeamSide.A, 1
                        ),
                        new SubmittedRecommendationAssignment(
                                uuid(102), TeamSide.A, 2
                        ),
                        new SubmittedRecommendationAssignment(
                                uuid(101), TeamSide.B, 1
                        ),
                        new SubmittedRecommendationAssignment(
                                uuid(104), TeamSide.B, 2
                        )
                )
        ));
    }

    @Test
    void outerTransactionUsesDefaultWriteSemantics() throws Exception {
        Transactional transactional =
                MatchmakingRecommendationAcceptanceService.class
                        .getMethod(
                                "acceptAndStart",
                                UUID.class,
                                UUID.class,
                                SubmittedRecommendationEvidence.class
                        )
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.isolation()).isEqualTo(Isolation.DEFAULT);
    }

    private void assertStale(
            MatchmakingResult regenerated,
            SubmittedRecommendationEvidence evidence
    ) {
        RecordingRecommendationService recommendationService =
                new RecordingRecommendationService(regenerated);
        RecordingMatchService matchService = new RecordingMatchService();
        MatchmakingRecommendationAcceptanceService service =
                new MatchmakingRecommendationAcceptanceService(
                        recommendationService,
                        matchService
                );

        assertThatThrownBy(() -> service.acceptAndStart(
                SESSION_ID,
                SESSION_COURT_ID,
                evidence
        ))
                .isInstanceOf(
                        MatchmakingRecommendationAcceptanceException.class
                )
                .extracting(exception ->
                        ((MatchmakingRecommendationAcceptanceException)
                                exception).reason())
                .isEqualTo(
                        MatchmakingRecommendationAcceptanceFailureReason
                                .RECOMMENDATION_STALE
                );
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
        assertThat(matchService.callCount()).isZero();
    }

    private static SubmittedRecommendationEvidence submittedEvidence(
            MatchRecommendation recommendation,
            boolean reverseOrder
    ) {
        List<SubmittedRecommendationAssignment> assignments = List.of(
                evidence(recommendation.teamA().slot1()),
                evidence(recommendation.teamA().slot2()),
                evidence(recommendation.teamB().slot1()),
                evidence(recommendation.teamB().slot2())
        );
        if (reverseOrder) {
            assignments = assignments.reversed();
        }
        return new SubmittedRecommendationEvidence(
                recommendation.algorithmVersion(),
                assignments
        );
    }

    private static SubmittedRecommendationAssignment evidence(
            RecommendedPlayer player
    ) {
        return new SubmittedRecommendationAssignment(
                player.sessionParticipantId(),
                player.teamSide(),
                player.teamSlot()
        );
    }

    private static MatchRecommendation recommendation(
            Instant evaluationTime,
            String baseRating
    ) {
        BigDecimal rating = new BigDecimal(baseRating);
        RecommendedPlayer a1 = player(101, 1, TeamSide.A, 1, rating);
        RecommendedPlayer a2 = player(102, 2, TeamSide.A, 2, rating);
        RecommendedPlayer b1 = player(103, 3, TeamSide.B, 1, rating);
        RecommendedPlayer b2 = player(104, 4, TeamSide.B, 2, rating);
        BigDecimal total = rating.add(rating);
        return new MatchRecommendation(
                MatchmakingEngine.ALGORITHM_VERSION,
                evaluationTime,
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                4,
                new RecommendedTeam(TeamSide.A, a1, a2, total),
                new RecommendedTeam(TeamSide.B, b1, b2, total),
                total,
                total,
                BigDecimal.ZERO,
                a1.waitingSince()
        );
    }

    private static RecommendedPlayer player(
            int participant,
            int player,
            TeamSide teamSide,
            int teamSlot,
            BigDecimal rating
    ) {
        return new RecommendedPlayer(
                uuid(participant),
                uuid(player),
                teamSide,
                teamSlot,
                Instant.parse("2026-08-28T09:00:00Z")
                        .plusSeconds(player),
                3600L - player,
                rating,
                new BigDecimal("8.0"),
                1,
                RatingBasis.PERSISTED
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private static final class RecordingRecommendationService
            extends MatchmakingRecommendationService {
        private final MatchmakingResult result;
        private int callCount;
        private UUID sessionId;
        private UUID sessionCourtId;

        private RecordingRecommendationService(MatchmakingResult result) {
            super(null, null, null, null);
            this.result = result;
        }

        @Override
        public MatchmakingResult recommend(
                UUID requestedSessionId,
                UUID requestedSessionCourtId
        ) {
            callCount++;
            sessionId = requestedSessionId;
            sessionCourtId = requestedSessionCourtId;
            return result;
        }

        private void assertCalledOnceWith(
                UUID expectedSessionId,
                UUID expectedSessionCourtId
        ) {
            assertThat(callCount).isEqualTo(1);
            assertThat(sessionId).isEqualTo(expectedSessionId);
            assertThat(sessionCourtId).isEqualTo(expectedSessionCourtId);
        }
    }

    private static final class RecordingMatchService extends MatchService {
        private final StartedMatch result;
        private int callCount;
        private CreateAndStartRecommendedMatchCommand command;

        private RecordingMatchService() {
            super(null, null, null, null);
            Match match = Match.create(
                    SESSION_ID,
                    SESSION_COURT_ID,
                    MatchSource.RECOMMENDATION,
                    EVALUATION_TIME
            ).start(EVALUATION_TIME);
            List<MatchParticipant> participants = List.of(
                    MatchParticipant.assign(
                            match.id(), uuid(101), TeamSide.A, 1
                    ),
                    MatchParticipant.assign(
                            match.id(), uuid(102), TeamSide.A, 2
                    ),
                    MatchParticipant.assign(
                            match.id(), uuid(103), TeamSide.B, 1
                    ),
                    MatchParticipant.assign(
                            match.id(), uuid(104), TeamSide.B, 2
                    )
            );
            result = new StartedMatch(match, participants);
        }

        @Override
        public StartedMatch createAndStartRecommendedMatch(
                CreateAndStartRecommendedMatchCommand requestedCommand
        ) {
            callCount++;
            command = requestedCommand;
            return result;
        }

        private StartedMatch result() {
            return result;
        }

        private int callCount() {
            return callCount;
        }

        private CreateAndStartRecommendedMatchCommand command() {
            return command;
        }
    }
}

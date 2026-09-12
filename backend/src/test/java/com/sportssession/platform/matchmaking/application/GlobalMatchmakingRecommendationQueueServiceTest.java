package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.session.application.SessionRuntimeLookup;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalMatchmakingRecommendationQueueServiceTest {

    private static final UUID SESSION_ID = uuid(1);
    private static final UUID FIRST_COURT_ID = uuid(10);
    private static final UUID SECOND_COURT_ID = uuid(11);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-11T04:00:00Z");

    private SessionRuntimeLookup sessionRuntimeLookup;
    private GlobalMatchmakingRecommendationService recommendationService;
    private MatchPlanService matchPlanService;
    private GlobalMatchmakingRecommendationQueueService service;

    @BeforeEach
    void setUp() {
        sessionRuntimeLookup = mock(SessionRuntimeLookup.class);
        recommendationService = mock(
                GlobalMatchmakingRecommendationService.class
        );
        matchPlanService = mock(MatchPlanService.class);
        service = new GlobalMatchmakingRecommendationQueueService(
                sessionRuntimeLookup,
                recommendationService,
                matchPlanService
        );
    }

    @Test
    void locksSessionRegeneratesOnceAndQueuesEveryRecommendationInOrder() {
        MatchRecommendation first = recommendation(FIRST_COURT_ID, 100);
        MatchRecommendation second = recommendation(SECOND_COURT_ID, 200);
        GlobalMatchmakingPreview preview = preview(List.of(first, second));
        SubmittedGlobalRecommendationEvidence submitted = evidence(
                first, second
        );
        MatchPlanDetails firstPlan = mock(MatchPlanDetails.class);
        MatchPlanDetails secondPlan = mock(MatchPlanDetails.class);
        when(recommendationService.preview(SESSION_ID)).thenReturn(preview);
        when(matchPlanService.createRecommended(any()))
                .thenReturn(firstPlan, secondPlan);

        QueuedGlobalMatchmakingBatch result = service.addAllToQueue(
                SESSION_ID,
                submitted
        );

        var order = inOrder(
                sessionRuntimeLookup,
                recommendationService,
                matchPlanService
        );
        order.verify(sessionRuntimeLookup).requireSessionForUpdate(SESSION_ID);
        order.verify(recommendationService).preview(SESSION_ID);
        order.verify(matchPlanService, times(2)).createRecommended(any());
        assertThat(result.createdPlans()).containsExactly(firstPlan, secondPlan);
        assertThat(result.orchestrationVersion()).isEqualTo(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION
        );
    }

    @Test
    void queuesOnlyRecommendationsWhenLaterCourtIsUnavailable() {
        MatchRecommendation recommendation = recommendation(
                FIRST_COURT_ID, 100
        );
        MatchmakingUnavailable unavailable = new MatchmakingUnavailable(
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                SECOND_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                0,
                MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
        );
        when(recommendationService.preview(SESSION_ID)).thenReturn(
                preview(List.of(recommendation, unavailable))
        );
        when(matchPlanService.createRecommended(any()))
                .thenReturn(mock(MatchPlanDetails.class));

        QueuedGlobalMatchmakingBatch result = service.addAllToQueue(
                SESSION_ID,
                evidenceWithTargets(
                        List.of(FIRST_COURT_ID, SECOND_COURT_ID),
                        recommendation
                )
        );

        assertThat(result.createdPlans()).hasSize(1);
        verify(matchPlanService).createRecommended(any());
    }

    @Test
    void disappearingUnavailableTargetCourtMakesWholeBatchStale() {
        MatchRecommendation current = recommendation(FIRST_COURT_ID, 100);
        SubmittedGlobalRecommendationEvidence submitted =
                evidenceWithTargets(
                        List.of(FIRST_COURT_ID, SECOND_COURT_ID),
                        current
                );

        when(recommendationService.preview(SESSION_ID)).thenReturn(
                preview(List.of(current))
        );

        assertStale(submitted);
        verify(matchPlanService, never()).createRecommended(any());
    }

    @Test
    void changedVersionCourtOrCompositionRejectsWholeBatchBeforeCreation() {
        MatchRecommendation current = recommendation(FIRST_COURT_ID, 100);
        when(recommendationService.preview(SESSION_ID)).thenReturn(
                preview(List.of(current))
        );

        SubmittedGlobalRecommendationEvidence wrongVersion =
                new SubmittedGlobalRecommendationEvidence(
                        "old-global-version",
                        MatchmakingEngine.ALGORITHM_VERSION,
                        List.of(FIRST_COURT_ID),
                        recommendations(current)
                );
        assertStale(wrongVersion);

        SubmittedGlobalRecommendationEvidence wrongSelectionVersion =
                new SubmittedGlobalRecommendationEvidence(
                        GlobalMatchmakingRecommendationService
                                .ORCHESTRATION_VERSION,
                        "old-selection-version",
                        List.of(FIRST_COURT_ID),
                        recommendations(current)
                );
        assertStale(wrongSelectionVersion);

        MatchRecommendation wrongCourt = recommendation(SECOND_COURT_ID, 100);
        assertStale(evidence(wrongCourt));

        MatchRecommendation wrongComposition = recommendation(
                FIRST_COURT_ID, 200
        );
        assertStale(evidence(wrongComposition));

        verify(matchPlanService, never()).createRecommended(any());
    }

    @Test
    void failureCreatingLaterPlanPropagatesForOuterTransactionRollback() {
        MatchRecommendation first = recommendation(FIRST_COURT_ID, 100);
        MatchRecommendation second = recommendation(SECOND_COURT_ID, 200);
        when(recommendationService.preview(SESSION_ID)).thenReturn(
                preview(List.of(first, second))
        );
        when(matchPlanService.createRecommended(any()))
                .thenReturn(mock(MatchPlanDetails.class))
                .thenThrow(new IllegalStateException("forced second failure"));

        assertThatThrownBy(() -> service.addAllToQueue(
                SESSION_ID,
                evidence(first, second)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("forced second failure");
        verify(matchPlanService, times(2)).createRecommended(any());
    }

    @Test
    void addAllToQueueDeclaresOneDefaultWriteTransaction() throws Exception {
        Transactional transactional =
                GlobalMatchmakingRecommendationQueueService.class
                        .getMethod(
                                "addAllToQueue",
                                UUID.class,
                                SubmittedGlobalRecommendationEvidence.class
                        )
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRED);
    }

    private void assertStale(
            SubmittedGlobalRecommendationEvidence evidence
    ) {
        assertThatThrownBy(() -> service.addAllToQueue(SESSION_ID, evidence))
                .isInstanceOf(MatchmakingRecommendationAcceptanceException.class)
                .hasMessage("Submitted global recommendation is stale");
    }

    private static GlobalMatchmakingPreview preview(
            List<MatchmakingResult> results
    ) {
        return new GlobalMatchmakingPreview(
                GlobalMatchmakingOutcome.RECOMMENDED,
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                8,
                results,
                null
        );
    }

    private static SubmittedGlobalRecommendationEvidence evidence(
            MatchRecommendation... recommendations
    ) {
        List<UUID> targetCourtIds = List.of(recommendations).stream()
                .map(MatchRecommendation::sessionCourtId)
                .toList();
        return evidenceWithTargets(targetCourtIds, recommendations);
    }

    private static SubmittedGlobalRecommendationEvidence evidenceWithTargets(
            List<UUID> targetCourtIds,
            MatchRecommendation... recommendations
    ) {
        return new SubmittedGlobalRecommendationEvidence(
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                targetCourtIds,
                recommendations(recommendations)
        );
    }

    private static List<SubmittedGlobalCourtRecommendation> recommendations(
            MatchRecommendation... recommendations
    ) {
        return List.of(recommendations).stream()
                .map(recommendation ->
                        new SubmittedGlobalCourtRecommendation(
                                recommendation.sessionCourtId(),
                                MatchmakingRecommendationEvidenceVerifier
                                        .players(recommendation)
                                        .stream()
                                        .map(player ->
                                                new SubmittedRecommendationAssignment(
                                                        player.sessionParticipantId(),
                                                        player.teamSide(),
                                                        player.teamSlot()
                                                ))
                                        .toList()
                        ))
                .toList();
    }

    private static MatchRecommendation recommendation(
            UUID courtId,
            int participantBase
    ) {
        List<MatchmakingCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            candidates.add(new MatchmakingCandidate(
                    uuid(participantBase + index),
                    uuid(participantBase + 1_000 + index),
                    EVALUATION_TIME.minusSeconds(100 - index),
                    SkillLevel.INTERMEDIATE,
                    0,
                    new BigDecimal("25.0"),
                    new BigDecimal("8.0"),
                    0,
                    RatingBasis.INITIAL_PRIOR
            ));
        }
        return (MatchRecommendation) new MatchmakingEngine().recommend(
                new MatchmakingContext(
                        SESSION_ID,
                        courtId,
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES,
                        EVALUATION_TIME,
                        candidates,
                        MatchmakingSessionPairingHistory.empty(SESSION_ID)
                )
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

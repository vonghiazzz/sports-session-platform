package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.CompletedMatchPairing;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import com.sportssession.platform.matchplan.application.MatchPlanPlanningLookup;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlobalMatchmakingRecommendationServiceTest {

    private static final UUID SESSION_ID = uuid(1);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-11T02:00:00Z");

    private GlobalMatchmakingSessionSnapshotReader snapshotReader;
    private MatchmakingCandidatePreparationService candidatePreparation;
    private MatchPlanPlanningLookup matchPlanPlanningLookup;
    private MatchmakingCreatedMatchCourtReader createdMatchCourtReader;
    private MatchmakingEngine engine;
    private GlobalMatchmakingRecommendationService service;

    @BeforeEach
    void setUp() {
        snapshotReader = mock(GlobalMatchmakingSessionSnapshotReader.class);
        candidatePreparation = mock(
                MatchmakingCandidatePreparationService.class
        );
        matchPlanPlanningLookup = mock(MatchPlanPlanningLookup.class);
        createdMatchCourtReader = mock(
                MatchmakingCreatedMatchCourtReader.class
        );
        engine = spy(new MatchmakingEngine());
        service = new GlobalMatchmakingRecommendationService(
                snapshotReader,
                candidatePreparation,
                matchPlanPlanningLookup,
                createdMatchCourtReader,
                engine,
                Clock.fixed(EVALUATION_TIME, ZoneOffset.UTC)
        );

        when(matchPlanPlanningLookup.queuedCourtIds(SESSION_ID))
                .thenReturn(Set.of());
        when(createdMatchCourtReader.createdMatchCourtIds(SESSION_ID))
                .thenReturn(Set.of());
    }

    @Test
    void eightPlayersAndTwoCourtsProduceTwoDisjointRecommendations() {
        List<MatchmakingCandidate> candidates = candidates(8);
        List<GlobalMatchmakingCourtSnapshot> courts = courts(2);
        stub(candidates, courts);

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        assertThat(preview.outcome())
                .isEqualTo(GlobalMatchmakingOutcome.RECOMMENDED);
        assertThat(preview.initialEligiblePlayerCount()).isEqualTo(8);
        assertThat(preview.courtResults()).hasSize(2)
                .allMatch(MatchRecommendation.class::isInstance);
        assertThat(selectedParticipantIds(preview.courtResults()))
                .hasSize(8);
        assertThat(preview.courtResults())
                .extracting(result ->
                        ((MatchRecommendation) result).eligiblePlayerCount()
                )
                .containsExactly(8, 4);
        verify(engine, times(2)).recommend(any());
    }

    @Test
    void successiveGroupsAnchorAgainstOldestRemainingCandidate() {
        List<MatchmakingCandidate> candidates = candidates(8);
        stub(candidates, courts(2));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        List<MatchRecommendation> recommendations = preview.courtResults()
                .stream()
                .map(MatchRecommendation.class::cast)
                .toList();
        assertThat(recommendations)
                .extracting(MatchRecommendation::oldestWaitingSince)
                .containsExactly(
                        candidates.get(0).waitingSince(),
                        candidates.get(4).waitingSince()
                );
        assertThat(players(recommendations.get(1)))
                .extracting(RecommendedPlayer::sessionParticipantId)
                .contains(candidates.get(4).sessionParticipantId());
    }

    @ParameterizedTest
    @MethodSource("allocationCases")
    void allocatesOnlyCompleteGroups(
            int playerCount,
            int courtCount,
            int recommendationCount,
            int unavailableCount,
            int distinctSelectedCount
    ) {
        stub(candidates(playerCount), courts(courtCount));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        assertThat(preview.courtResults().stream()
                .filter(MatchRecommendation.class::isInstance))
                .hasSize(recommendationCount);
        assertThat(preview.courtResults().stream()
                .filter(MatchmakingUnavailable.class::isInstance))
                .hasSize(unavailableCount);
        assertThat(selectedParticipantIds(preview.courtResults()))
                .hasSize(distinctSelectedCount);
        preview.courtResults().stream()
                .filter(MatchmakingUnavailable.class::isInstance)
                .map(MatchmakingUnavailable.class::cast)
                .forEach(unavailable -> assertThat(unavailable.reason())
                        .isEqualTo(MatchmakingUnavailableReason
                                .INSUFFICIENT_ELIGIBLE_PLAYERS));
    }

    private static Stream<Arguments> allocationCases() {
        return Stream.of(
                Arguments.of(4, 2, 1, 1, 4),
                Arguments.of(6, 2, 1, 1, 4),
                Arguments.of(10, 2, 2, 0, 8),
                Arguments.of(12, 3, 3, 0, 12)
        );
    }

    @Test
    void targetsOnlyAvailableCourtsWithoutQueuedPlanOrCreatedMatch() {
        Instant base = EVALUATION_TIME.minusSeconds(600);
        GlobalMatchmakingCourtSnapshot later = court(
                20, SessionCourtStatus.AVAILABLE, base.plusSeconds(2)
        );
        GlobalMatchmakingCourtSnapshot unavailable = court(
                21, SessionCourtStatus.UNAVAILABLE, base
        );
        GlobalMatchmakingCourtSnapshot playing = court(
                22, SessionCourtStatus.PLAYING, base
        );
        GlobalMatchmakingCourtSnapshot queued = court(
                23, SessionCourtStatus.AVAILABLE, base
        );
        GlobalMatchmakingCourtSnapshot created = court(
                24, SessionCourtStatus.AVAILABLE, base
        );
        GlobalMatchmakingCourtSnapshot earlier = court(
                25, SessionCourtStatus.AVAILABLE, base.plusSeconds(1)
        );
        stub(candidates(8), List.of(
                later, unavailable, playing, queued, created, earlier
        ));
        when(matchPlanPlanningLookup.queuedCourtIds(SESSION_ID))
                .thenReturn(Set.of(queued.sessionCourtId()));
        when(createdMatchCourtReader.createdMatchCourtIds(SESSION_ID))
                .thenReturn(Set.of(created.sessionCourtId()));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        assertThat(preview.courtResults())
                .extracting(MatchmakingResult::sessionCourtId)
                .containsExactly(
                        earlier.sessionCourtId(),
                        later.sessionCourtId()
                );
    }

    @Test
    void equalAddedAtUsesSessionCourtIdAsDeterministicTieBreak() {
        Instant addedAt = EVALUATION_TIME.minusSeconds(300);
        GlobalMatchmakingCourtSnapshot greater = court(
                31, SessionCourtStatus.AVAILABLE, addedAt
        );
        GlobalMatchmakingCourtSnapshot smaller = court(
                30, SessionCourtStatus.AVAILABLE, addedAt
        );
        stub(candidates(8), List.of(greater, smaller));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        assertThat(preview.courtResults())
                .extracting(MatchmakingResult::sessionCourtId)
                .containsExactly(
                        smaller.sessionCourtId(),
                        greater.sessionCourtId()
                );
    }

    @Test
    void zeroTargetCourtsReturnsNormalNoEligibleCourtsOutcome() {
        stub(candidates(8), List.of(court(
                40,
                SessionCourtStatus.PLAYING,
                EVALUATION_TIME.minusSeconds(60)
        )));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        assertThat(preview.outcome())
                .isEqualTo(GlobalMatchmakingOutcome.UNAVAILABLE);
        assertThat(preview.reason())
                .isEqualTo(GlobalMatchmakingUnavailableReason
                        .NO_ELIGIBLE_COURTS);
        assertThat(preview.courtResults()).isEmpty();
        verify(engine, never()).recommend(any());
    }

    @Test
    void allEvidenceIsPreparedOnceAtOneEvaluationTime() {
        List<MatchmakingCandidate> candidates = candidates(8);
        GlobalMatchmakingSessionSnapshot snapshot = snapshot(courts(2));
        stub(candidates, snapshot);

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        verify(snapshotReader, times(1)).load(SESSION_ID);
        verify(candidatePreparation, times(1)).prepare(
                snapshot.sessionEvidence(),
                EVALUATION_TIME
        );
        verify(matchPlanPlanningLookup, times(1)).queuedCourtIds(SESSION_ID);
        verify(createdMatchCourtReader, times(1))
                .createdMatchCourtIds(SESSION_ID);
        assertThat(preview.evaluationTime()).isEqualTo(EVALUATION_TIME);
        assertThat(preview.orchestrationVersion()).isEqualTo(
                "global-greedy-available-unplanned-v1"
        );
        assertThat(preview.selectionAlgorithmVersion()).isEqualTo(
                MatchmakingEngine.ALGORITHM_VERSION
        );
    }

    @Test
    void onePreparedPairingHistoryIsReusedAcrossAllCourtEvaluations() {
        List<MatchmakingCandidate> candidates = candidates(8);
        GlobalMatchmakingSessionSnapshot snapshot = snapshot(courts(2));
        MatchmakingSessionPairingHistory pairingHistory =
                new MatchmakingSessionPairingHistory(
                        SESSION_ID,
                        List.of(new CompletedMatchPairing(
                                uuid(9_001),
                                EVALUATION_TIME.minusSeconds(60),
                                List.of(
                                        candidates.get(0)
                                                .sessionParticipantId(),
                                        candidates.get(1)
                                                .sessionParticipantId()
                                ),
                                List.of(
                                        candidates.get(2)
                                                .sessionParticipantId(),
                                        candidates.get(3)
                                                .sessionParticipantId()
                                )
                        ))
                );
        stub(candidates, snapshot);
        when(candidatePreparation.prepare(
                eq(snapshot.sessionEvidence()),
                eq(EVALUATION_TIME)
        )).thenReturn(new PreparedMatchmakingCandidates(
                SESSION_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                EVALUATION_TIME,
                candidates,
                pairingHistory
        ));

        GlobalMatchmakingPreview preview = service.preview(SESSION_ID);

        ArgumentCaptor<MatchmakingContext> contexts =
                ArgumentCaptor.forClass(MatchmakingContext.class);
        verify(engine, times(2)).recommend(contexts.capture());
        assertThat(contexts.getAllValues())
                .extracting(MatchmakingContext::pairingHistory)
                .allSatisfy(history -> assertThat(history)
                        .isSameAs(pairingHistory));
        MatchRecommendation firstRecommendation =
                (MatchRecommendation) preview.courtResults().getFirst();
        assertThat(firstRecommendation.immediateQuartetRepeat()).isFalse();
        Set<UUID> selected = players(firstRecommendation).stream()
                .map(RecommendedPlayer::sessionParticipantId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(selected)
                .contains(candidates.getFirst().sessionParticipantId())
                .isNotEqualTo(candidates.subList(0, 4).stream()
                        .map(MatchmakingCandidate::sessionParticipantId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @Test
    void nullSessionIdFailsBeforeReaders() {
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sessionId is required");
        verify(snapshotReader, never()).load(any());
    }

    @Test
    void previewDeclaresDefaultReadOnlyTransaction() throws Exception {
        Transactional transactional =
                GlobalMatchmakingRecommendationService.class
                        .getMethod("preview", UUID.class)
                        .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRED);
    }

    private void stub(
            List<MatchmakingCandidate> candidates,
            List<GlobalMatchmakingCourtSnapshot> courts
    ) {
        stub(candidates, snapshot(courts));
    }

    private void stub(
            List<MatchmakingCandidate> candidates,
            GlobalMatchmakingSessionSnapshot snapshot
    ) {
        when(snapshotReader.load(SESSION_ID)).thenReturn(snapshot);
        when(candidatePreparation.prepare(
                eq(snapshot.sessionEvidence()),
                eq(EVALUATION_TIME)
        )).thenReturn(new PreparedMatchmakingCandidates(
                SESSION_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                EVALUATION_TIME,
                candidates,
                MatchmakingSessionPairingHistory.empty(SESSION_ID)
        ));
    }

    private GlobalMatchmakingSessionSnapshot snapshot(
            List<GlobalMatchmakingCourtSnapshot> courts
    ) {
        return new GlobalMatchmakingSessionSnapshot(
                SESSION_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SessionStatus.IN_PROGRESS,
                courts,
                List.of()
        );
    }

    private static List<GlobalMatchmakingCourtSnapshot> courts(int count) {
        return Stream.iterate(0, index -> index + 1)
                .limit(count)
                .map(index -> court(
                        100 + index,
                        SessionCourtStatus.AVAILABLE,
                        EVALUATION_TIME.minusSeconds(100 - index)
                ))
                .toList();
    }

    private static GlobalMatchmakingCourtSnapshot court(
            int id,
            SessionCourtStatus status,
            Instant addedAt
    ) {
        return new GlobalMatchmakingCourtSnapshot(uuid(id), status, addedAt);
    }

    private static List<MatchmakingCandidate> candidates(int count) {
        return Stream.iterate(0, index -> index + 1)
                .limit(count)
                .map(index -> new MatchmakingCandidate(
                        uuid(1_000 + index),
                        uuid(2_000 + index),
                        EVALUATION_TIME.minusSeconds(1_000 - index),
                        SkillLevel.INTERMEDIATE,
                        0,
                        new BigDecimal("25.0"),
                        new BigDecimal("8.0"),
                        0,
                        RatingBasis.INITIAL_PRIOR
                ))
                .toList();
    }

    private static Set<UUID> selectedParticipantIds(
            Collection<MatchmakingResult> results
    ) {
        Set<UUID> selectedIds = new HashSet<>();
        results.stream()
                .filter(MatchRecommendation.class::isInstance)
                .map(MatchRecommendation.class::cast)
                .map(GlobalMatchmakingRecommendationServiceTest::players)
                .flatMap(Collection::stream)
                .map(RecommendedPlayer::sessionParticipantId)
                .forEach(selectedIds::add);
        return Set.copyOf(selectedIds);
    }

    private static List<RecommendedPlayer> players(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        ).stream()
                .sorted((left, right) -> {
                    int side = left.teamSide().compareTo(right.teamSide());
                    return side != 0
                            ? side
                            : Integer.compare(
                                    left.teamSlot(), right.teamSlot()
                            );
                })
                .toList();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

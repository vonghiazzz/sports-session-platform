package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchmakingRecommendationServiceTest {

    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T05:00:00Z");
    private static final UUID SESSION_ID = uuid(100);
    private static final UUID SESSION_COURT_ID = uuid(200);

    private MatchmakingSessionSnapshotReader sessionReader;
    private MatchmakingRatingReader ratingReader;
    private MatchmakingEngine engine;
    private CountingClock clock;
    private MatchmakingRecommendationService service;

    @BeforeEach
    void setUp() {
        sessionReader = mock(MatchmakingSessionSnapshotReader.class);
        ratingReader = mock(MatchmakingRatingReader.class);
        engine = mock(MatchmakingEngine.class);
        clock = new CountingClock(EVALUATION_TIME);
        service = new MatchmakingRecommendationService(
                sessionReader,
                ratingReader,
                engine,
                clock
        );
        MatchmakingEngine realEngine = new MatchmakingEngine();
        when(engine.recommend(any(MatchmakingContext.class)))
                .thenAnswer(invocation -> realEngine.recommend(
                        invocation.getArgument(0)
                ));
    }

    @Test
    void fourWaitingParticipantsProduceRecommendation() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        stubValidPipeline(participants, ratingsFor(participants));

        MatchmakingResult result = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result).isInstanceOf(MatchRecommendation.class);
        MatchRecommendation recommendation = (MatchRecommendation) result;
        assertThat(recommendation.eligiblePlayerCount()).isEqualTo(4);
        assertThat(recommendation.algorithmVersion()).isEqualTo(
                MatchmakingEngine.ALGORITHM_VERSION
        );
        verify(engine, times(1)).recommend(any(MatchmakingContext.class));
    }

    @ParameterizedTest
    @EnumSource(
            value = SessionStatus.class,
            names = {"PLANNED", "COMPLETED", "CANCELLED"}
    )
    void nonRunningSessionFailsBeforeRatingAndEngine(SessionStatus status) {
        stubSnapshot(snapshot(
                status,
                SessionCourtStatus.AVAILABLE,
                waitingParticipants(4)
        ));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason.SESSION_NOT_IN_PROGRESS
        );
        verify(sessionReader, times(1)).load(SESSION_ID, SESSION_COURT_ID);
        verifyNoInteractions(ratingReader, engine);
    }

    @ParameterizedTest
    @EnumSource(
            value = SessionCourtStatus.class,
            names = {"PLAYING", "UNAVAILABLE"}
    )
    void nonAvailableCourtFailsBeforeRatingAndEngine(
            SessionCourtStatus status
    ) {
        stubSnapshot(snapshot(
                SessionStatus.IN_PROGRESS,
                status,
                waitingParticipants(4)
        ));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason
                        .SESSION_COURT_NOT_AVAILABLE
        );
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void mixedParticipantStatesSendOnlyWaitingPlayersToRating() {
        List<MatchmakingSessionParticipantSnapshot> participants = List.of(
                participant(1, ParticipantStatus.REGISTERED, null),
                participant(2, ParticipantStatus.WAITING, secondsBefore(50)),
                participant(3, ParticipantStatus.PLAYING, null),
                participant(4, ParticipantStatus.PAUSED, null),
                participant(5, ParticipantStatus.LEFT, null),
                participant(6, ParticipantStatus.WAITING, secondsBefore(40)),
                participant(7, ParticipantStatus.WAITING, secondsBefore(30)),
                participant(8, ParticipantStatus.WAITING, secondsBefore(20))
        );
        List<MatchmakingSessionParticipantSnapshot> waiting = participants
                .stream()
                .filter(participant -> participant.participantStatus()
                        == ParticipantStatus.WAITING)
                .toList();
        stubValidPipeline(participants, ratingsFor(waiting));

        MatchRecommendation result = (MatchRecommendation) service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result.eligiblePlayerCount()).isEqualTo(4);
        verify(ratingReader).readEffectiveRatings(
                List.of(uuid(2), uuid(6), uuid(7), uuid(8)),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
        assertThat(recommendedPlayerIds(result))
                .containsExactlyInAnyOrder(uuid(2), uuid(6), uuid(7), uuid(8));
    }

    @Test
    void waitingParticipantWithoutWaitingSinceFailsExplicitly() {
        stubSnapshot(validSnapshot(List.of(
                participant(1, ParticipantStatus.WAITING, null)
        )));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason
                        .WAITING_PARTICIPANT_MISSING_WAITING_SINCE
        );
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void waitingParticipantAfterEvaluationTimeFailsExplicitly() {
        stubSnapshot(validSnapshot(List.of(
                participant(
                        1,
                        ParticipantStatus.WAITING,
                        EVALUATION_TIME.plusSeconds(1)
                )
        )));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason
                        .WAITING_PARTICIPANT_WAITING_SINCE_AFTER_EVALUATION_TIME
        );
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void nonWaitingParticipantWithNullWaitingSinceDoesNotFail() {
        List<MatchmakingSessionParticipantSnapshot> participants = List.of(
                participant(1, ParticipantStatus.REGISTERED, null),
                participant(2, ParticipantStatus.PLAYING, null),
                participant(3, ParticipantStatus.PAUSED, null),
                participant(4, ParticipantStatus.LEFT, null)
        );
        stubValidPipeline(participants, Map.of());

        MatchmakingResult result = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result).isInstanceOf(MatchmakingUnavailable.class);
        verify(ratingReader).readEffectiveRatings(
                List.of(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
    }

    @Test
    void persistedRatingMapsExactlyToCandidate() {
        MatchmakingSessionParticipantSnapshot participant = waiting(1, 90);
        MatchmakingRatingSnapshot rating = rating(
                participant.playerId(),
                "19.123456789",
                "7.987654321",
                17,
                RatingBasis.PERSISTED
        );
        stubValidPipeline(List.of(participant), Map.of(
                participant.playerId(),
                rating
        ));

        service.recommend(SESSION_ID, SESSION_COURT_ID);

        MatchmakingCandidate candidate = capturedContext()
                .candidates()
                .getFirst();
        assertThat(candidate.sessionParticipantId())
                .isEqualTo(participant.sessionParticipantId());
        assertThat(candidate.playerId()).isEqualTo(participant.playerId());
        assertThat(candidate.waitingSince()).isEqualTo(participant.waitingSince());
        assertThat(candidate.ratingValue())
                .isEqualByComparingTo("19.123456789");
        assertThat(candidate.uncertainty())
                .isEqualByComparingTo("7.987654321");
        assertThat(candidate.ratedMatches()).isEqualTo(17);
        assertThat(candidate.ratingBasis()).isEqualTo(RatingBasis.PERSISTED);
    }

    @Test
    void initialPriorMapsExactlyToCandidate() {
        MatchmakingSessionParticipantSnapshot participant = waiting(1, 90);
        MatchmakingRatingSnapshot rating = rating(
                participant.playerId(),
                "35.000000000",
                "8.333333333",
                0,
                RatingBasis.INITIAL_PRIOR
        );
        stubValidPipeline(List.of(participant), Map.of(
                participant.playerId(),
                rating
        ));

        service.recommend(SESSION_ID, SESSION_COURT_ID);

        MatchmakingCandidate candidate = capturedContext()
                .candidates()
                .getFirst();
        assertThat(candidate.ratingValue())
                .isEqualByComparingTo("35.000000000");
        assertThat(candidate.uncertainty())
                .isEqualByComparingTo("8.333333333");
        assertThat(candidate.ratedMatches()).isZero();
        assertThat(candidate.ratingBasis())
                .isEqualTo(RatingBasis.INITIAL_PRIOR);
    }

    @Test
    void ratingReaderReceivesSnapshotSportAndFormatExactlyOnce() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        stubValidPipeline(participants, ratingsFor(participants));

        service.recommend(SESSION_ID, SESSION_COURT_ID);

        verify(ratingReader, times(1)).readEffectiveRatings(
                participants.stream()
                        .map(MatchmakingSessionParticipantSnapshot::playerId)
                        .toList(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
    }

    @Test
    void sessionReaderAndClockAreEachUsedExactlyOnce() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        stubValidPipeline(participants, ratingsFor(participants));

        service.recommend(SESSION_ID, SESSION_COURT_ID);

        verify(sessionReader, times(1)).load(SESSION_ID, SESSION_COURT_ID);
        assertThat(clock.invocations()).isEqualTo(1);
    }

    @Test
    void evaluationTimeIsSharedByValidationContextAndResult() {
        List<MatchmakingSessionParticipantSnapshot> participants = List.of(
                participant(
                        1,
                        ParticipantStatus.WAITING,
                        EVALUATION_TIME
                ),
                waiting(2, 3),
                waiting(3, 2),
                waiting(4, 1)
        );
        stubValidPipeline(participants, ratingsFor(participants));

        MatchRecommendation result = (MatchRecommendation) service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result.evaluationTime()).isEqualTo(EVALUATION_TIME);
        assertThat(result.teamA().slot1().waitingSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.teamA().slot2().waitingSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.teamB().slot1().waitingSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(result.teamB().slot2().waitingSeconds()).isGreaterThanOrEqualTo(0);
        assertThat(capturedContext().evaluationTime()).isEqualTo(EVALUATION_TIME);
        assertThat(clock.invocations()).isEqualTo(1);
    }

    @Test
    void missingRatingFailsAsIncompleteBatch() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        Map<UUID, MatchmakingRatingSnapshot> incomplete = new LinkedHashMap<>(
                ratingsFor(participants)
        );
        incomplete.remove(participants.getLast().playerId());
        stubValidPipeline(participants, Map.copyOf(incomplete));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason.RATING_BATCH_INCOMPLETE
        );
        verify(engine, never()).recommend(any());
    }

    @Test
    void unexpectedExtraRatingFailsAsIncompleteBatch() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        Map<UUID, MatchmakingRatingSnapshot> extra = new LinkedHashMap<>(
                ratingsFor(participants)
        );
        extra.put(uuid(99), rating(
                uuid(99),
                "27.0",
                "8.0",
                0,
                RatingBasis.INITIAL_PRIOR
        ));
        stubValidPipeline(participants, Map.copyOf(extra));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason.RATING_BATCH_INCOMPLETE
        );
        verify(engine, never()).recommend(any());
    }

    @Test
    void mismatchedRatingSnapshotIdentityFailsAsIncompleteBatch() {
        MatchmakingSessionParticipantSnapshot participant = waiting(1, 10);
        stubValidPipeline(List.of(participant), Map.of(
                participant.playerId(),
                rating(
                        uuid(99),
                        "27.0",
                        "8.0",
                        0,
                        RatingBasis.INITIAL_PRIOR
                )
        ));

        assertRecommendationFailure(
                MatchmakingRecommendationFailureReason.RATING_BATCH_INCOMPLETE
        );
    }

    @Test
    void zeroWaitingUsesEngineUnavailableResult() {
        stubValidPipeline(List.of(), Map.of());

        MatchmakingResult result = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result).isInstanceOfSatisfying(
                MatchmakingUnavailable.class,
                unavailable -> {
                    assertThat(unavailable.eligiblePlayerCount()).isZero();
                    assertThat(unavailable.reason()).isEqualTo(
                            MatchmakingUnavailableReason
                                    .INSUFFICIENT_ELIGIBLE_PLAYERS
                    );
                }
        );
        verify(ratingReader, times(1)).readEffectiveRatings(
                List.of(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
        verify(engine, times(1)).recommend(any(MatchmakingContext.class));
    }

    @Test
    void threeWaitingUsesEngineUnavailableResult() {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(3);
        stubValidPipeline(participants, ratingsFor(participants));

        MatchmakingResult result = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(result).isInstanceOfSatisfying(
                MatchmakingUnavailable.class,
                unavailable -> {
                    assertThat(unavailable.eligiblePlayerCount()).isEqualTo(3);
                    assertThat(unavailable.reason()).isEqualTo(
                            MatchmakingUnavailableReason
                                    .INSUFFICIENT_ELIGIBLE_PLAYERS
                    );
                }
        );
    }

    @Test
    void participantProviderOrderingDoesNotChangeRecommendation() {
        List<MatchmakingSessionParticipantSnapshot> ordered =
                waitingParticipants(5);
        List<MatchmakingSessionParticipantSnapshot> reversed = new ArrayList<>(
                ordered
        );
        java.util.Collections.reverse(reversed);
        when(sessionReader.load(SESSION_ID, SESSION_COURT_ID))
                .thenReturn(validSnapshot(ordered), validSnapshot(reversed));
        when(ratingReader.readEffectiveRatings(
                org.mockito.ArgumentMatchers.<UUID>anyCollection(),
                any(SportCode.class),
                any(MatchFormat.class)
        )).thenReturn(ratingsFor(ordered));

        MatchmakingResult first = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );
        MatchmakingResult second = service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        );

        assertThat(second).isEqualTo(first);
    }

    @Test
    void sessionNotFoundExceptionPropagatesUnchanged() {
        MatchmakingSessionSnapshotException expected =
                new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_NOT_FOUND,
                        SESSION_ID,
                        SESSION_COURT_ID
                );
        when(sessionReader.load(SESSION_ID, SESSION_COURT_ID))
                .thenThrow(expected);

        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isSameAs(expected);
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void sessionCourtNotFoundExceptionPropagatesUnchanged() {
        MatchmakingSessionSnapshotException expected =
                new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_COURT_NOT_FOUND_FOR_SESSION,
                        SESSION_ID,
                        SESSION_COURT_ID
                );
        when(sessionReader.load(SESSION_ID, SESSION_COURT_ID))
                .thenThrow(expected);

        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isSameAs(expected);
    }

    @ParameterizedTest
    @EnumSource(MatchmakingRatingResolutionFailureReason.class)
    void ratingResolutionExceptionPropagatesUnchanged(
            MatchmakingRatingResolutionFailureReason reason
    ) {
        List<MatchmakingSessionParticipantSnapshot> participants =
                waitingParticipants(4);
        MatchmakingRatingResolutionException expected =
                new MatchmakingRatingResolutionException(
                        reason,
                        List.of(participants.getFirst().playerId()),
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES,
                        "Rating resolution failed"
                );
        stubSnapshot(validSnapshot(participants));
        when(ratingReader.readEffectiveRatings(
                participants.stream()
                        .map(MatchmakingSessionParticipantSnapshot::playerId)
                        .toList(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenThrow(expected);

        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isSameAs(expected);
        verify(engine, never()).recommend(any());
    }

    @Test
    void duplicateWaitingPlayerIdentityFailsWithoutDeduplication() {
        MatchmakingSessionParticipantSnapshot first = waiting(1, 10);
        MatchmakingSessionParticipantSnapshot duplicatePlayer =
                new MatchmakingSessionParticipantSnapshot(
                        uuid(1002),
                        first.playerId(),
                        ParticipantStatus.WAITING,
                        secondsBefore(9)
                );
        stubSnapshot(validSnapshot(List.of(first, duplicatePlayer)));

        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("playerId must be unique");
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void duplicateWaitingParticipantIdentityFailsWithoutDeduplication() {
        MatchmakingSessionParticipantSnapshot first = waiting(1, 10);
        MatchmakingSessionParticipantSnapshot duplicateParticipant =
                new MatchmakingSessionParticipantSnapshot(
                        first.sessionParticipantId(),
                        uuid(2),
                        ParticipantStatus.WAITING,
                        secondsBefore(9)
                );
        stubSnapshot(validSnapshot(List.of(first, duplicateParticipant)));

        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("sessionParticipantId must be unique");
        verifyNoInteractions(ratingReader, engine);
    }

    @Test
    void nullSessionIdFailsBeforeClockOrReaders() {
        assertThatThrownBy(() -> service.recommend(null, SESSION_COURT_ID))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sessionId is required");
        assertThat(clock.invocations()).isZero();
        verifyNoInteractions(sessionReader, ratingReader, engine);
    }

    @Test
    void nullSessionCourtIdFailsBeforeClockOrReaders() {
        assertThatThrownBy(() -> service.recommend(SESSION_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sessionCourtId is required");
        assertThat(clock.invocations()).isZero();
        verifyNoInteractions(sessionReader, ratingReader, engine);
    }

    @Test
    void recommendDeclaresDefaultReadOnlyTransaction() throws Exception {
        Transactional transactional = MatchmakingRecommendationService.class
                .getMethod("recommend", UUID.class, UUID.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.isolation()).isEqualTo(Isolation.DEFAULT);
    }

    private void assertRecommendationFailure(
            MatchmakingRecommendationFailureReason reason
    ) {
        assertThatThrownBy(() -> service.recommend(
                SESSION_ID,
                SESSION_COURT_ID
        )).isInstanceOfSatisfying(
                MatchmakingRecommendationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason)
        );
    }

    private void stubValidPipeline(
            List<MatchmakingSessionParticipantSnapshot> participants,
            Map<UUID, MatchmakingRatingSnapshot> ratings
    ) {
        stubSnapshot(validSnapshot(participants));
        List<UUID> waitingPlayerIds = participants.stream()
                .filter(participant -> participant.participantStatus()
                        == ParticipantStatus.WAITING)
                .map(MatchmakingSessionParticipantSnapshot::playerId)
                .toList();
        when(ratingReader.readEffectiveRatings(
                waitingPlayerIds,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(ratings);
    }

    private void stubSnapshot(MatchmakingSessionSnapshot snapshot) {
        when(sessionReader.load(SESSION_ID, SESSION_COURT_ID))
                .thenReturn(snapshot);
    }

    private MatchmakingSessionSnapshot validSnapshot(
            List<MatchmakingSessionParticipantSnapshot> participants
    ) {
        return snapshot(
                SessionStatus.IN_PROGRESS,
                SessionCourtStatus.AVAILABLE,
                participants
        );
    }

    private MatchmakingSessionSnapshot snapshot(
            SessionStatus sessionStatus,
            SessionCourtStatus courtStatus,
            List<MatchmakingSessionParticipantSnapshot> participants
    ) {
        return new MatchmakingSessionSnapshot(
                SESSION_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                sessionStatus,
                SESSION_COURT_ID,
                courtStatus,
                participants
        );
    }

    private List<MatchmakingSessionParticipantSnapshot> waitingParticipants(
            int count
    ) {
        List<MatchmakingSessionParticipantSnapshot> participants =
                new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            participants.add(waiting(index, 100 - index));
        }
        return List.copyOf(participants);
    }

    private MatchmakingSessionParticipantSnapshot waiting(
            int number,
            long secondsBeforeEvaluation
    ) {
        return participant(
                number,
                ParticipantStatus.WAITING,
                secondsBefore(secondsBeforeEvaluation)
        );
    }

    private MatchmakingSessionParticipantSnapshot participant(
            int number,
            ParticipantStatus status,
            Instant waitingSince
    ) {
        return new MatchmakingSessionParticipantSnapshot(
                uuid(1000 + number),
                uuid(number),
                status,
                waitingSince
        );
    }

    private Map<UUID, MatchmakingRatingSnapshot> ratingsFor(
            List<MatchmakingSessionParticipantSnapshot> participants
    ) {
        Map<UUID, MatchmakingRatingSnapshot> ratings = new LinkedHashMap<>();
        for (int index = 0; index < participants.size(); index++) {
            UUID playerId = participants.get(index).playerId();
            ratings.put(playerId, rating(
                    playerId,
                    Integer.toString(20 + index),
                    Integer.toString(8 + index),
                    index,
                    index % 2 == 0
                            ? RatingBasis.PERSISTED
                            : RatingBasis.INITIAL_PRIOR
            ));
        }
        return Map.copyOf(ratings);
    }

    private MatchmakingRatingSnapshot rating(
            UUID playerId,
            String ratingValue,
            String uncertainty,
            int ratedMatches,
            RatingBasis ratingBasis
    ) {
        return new MatchmakingRatingSnapshot(
                playerId,
                new BigDecimal(ratingValue),
                new BigDecimal(uncertainty),
                ratedMatches,
                ratingBasis
        );
    }

    private MatchmakingContext capturedContext() {
        ArgumentCaptor<MatchmakingContext> captor =
                ArgumentCaptor.forClass(MatchmakingContext.class);
        verify(engine, times(1)).recommend(captor.capture());
        return captor.getValue();
    }

    private List<UUID> recommendedPlayerIds(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1().playerId(),
                recommendation.teamA().slot2().playerId(),
                recommendation.teamB().slot1().playerId(),
                recommendation.teamB().slot2().playerId()
        );
    }

    private static Instant secondsBefore(long seconds) {
        return EVALUATION_TIME.minusSeconds(seconds);
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private static final class CountingClock extends Clock {

        private final Instant instant;
        private int invocations;

        private CountingClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            invocations++;
            return instant;
        }

        private int invocations() {
            return invocations;
        }
    }
}

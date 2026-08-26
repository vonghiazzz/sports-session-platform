package com.sportssession.platform.match.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.infrastructure.SessionCourtEntity;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.domain.Court;
import com.sportssession.platform.venue.domain.Venue;
import com.sportssession.platform.venue.infrastructure.CourtEntity;
import com.sportssession.platform.venue.infrastructure.CourtRepository;
import com.sportssession.platform.venue.infrastructure.VenueEntity;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ResolveMatchApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionParticipantRepository sessionParticipantRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @BeforeEach
    void cleanDatabase() {
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @ParameterizedTest(name = "complete accepts {0}")
    @MethodSource("validResults")
    void completePlayingMatchPersistsResultAndAtomicallyReleasesResources(
            String description,
            ResultBody resultBody
    ) throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        Match playing = match(fixture.matchId());
        List<Instant> checkedInBefore = fixture.participantIds().stream()
                .map(this::participant)
                .map(SessionParticipant::checkedInAt)
                .toList();
        List<MatchParticipant> compositionBefore = composition(fixture.matchId());

        ResultActions response = complete(fixture.matchId(), resultBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.winnerTeam")
                        .value(resultBody.winnerTeam().name()))
                .andExpect(jsonPath("$.resultVersion").value(1))
                .andExpect(jsonPath("$.participants.length()").value(4));
        if (resultBody.teamAScore() == null) {
            response.andExpect(jsonPath("$.teamAScore").doesNotExist())
                    .andExpect(jsonPath("$.teamBScore").doesNotExist());
        } else {
            response.andExpect(jsonPath("$.teamAScore")
                            .value(resultBody.teamAScore()))
                    .andExpect(jsonPath("$.teamBScore")
                            .value(resultBody.teamBScore()));
        }

        Match completed = match(fixture.matchId());
        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.startedAt()).isEqualTo(playing.startedAt());
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.cancelledAt()).isNull();
        assertThat(completed.result().winnerTeam())
                .isEqualTo(resultBody.winnerTeam());
        assertThat(completed.result().teamAScore())
                .isEqualTo(resultBody.teamAScore());
        assertThat(completed.result().teamBScore())
                .isEqualTo(resultBody.teamBScore());
        assertThat(completed.resultVersion()).isEqualTo(1);
        assertThat(composition(fixture.matchId()))
                .containsExactlyElementsOf(compositionBefore);

        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        List<SessionParticipant> waitingParticipants = fixture.participantIds()
                .stream()
                .map(this::participant)
                .toList();
        assertThat(waitingParticipants)
                .allSatisfy(participant -> {
                    assertThat(participant.status())
                            .isEqualTo(ParticipantStatus.WAITING);
                    assertThat(participant.waitingSince())
                            .isEqualTo(completed.completedAt());
                    assertThat(participant.pausedAt()).isNull();
                    assertThat(participant.leftAt()).isNull();
                });
        assertThat(waitingParticipants.stream()
                .map(SessionParticipant::checkedInAt)
                .toList()).containsExactlyElementsOf(checkedInBefore);
    }

    static Stream<Arguments> validResults() {
        return Stream.of(
                Arguments.of("winner A without score", body(TeamSide.A, null, null)),
                Arguments.of("winner B without score", body(TeamSide.B, null, null)),
                Arguments.of("winner A with score", body(TeamSide.A, 21, 18)),
                Arguments.of("winner B with score", body(TeamSide.B, 17, 21))
        );
    }

    @ParameterizedTest(name = "invalid result: {0}")
    @MethodSource("invalidResults")
    void invalidResultReturnsBadRequestWithoutMutatingPlayingRuntime(
            String description,
            ResultBody resultBody
    ) throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());

        complete(fixture.matchId(), resultBody)
                .andExpect(status().isBadRequest());

        assertPlayingRuntime(fixture);
    }

    static Stream<Arguments> invalidResults() {
        return Stream.of(
                Arguments.of("missing winner", body(null, null, null)),
                Arguments.of("negative A score", body(TeamSide.A, -1, 0)),
                Arguments.of("negative B score", body(TeamSide.B, 0, -1)),
                Arguments.of("only A score", body(TeamSide.A, 21, null)),
                Arguments.of("only B score", body(TeamSide.B, null, 21)),
                Arguments.of("tied score", body(TeamSide.A, 21, 21)),
                Arguments.of("winner A has lower score", body(TeamSide.A, 18, 21)),
                Arguments.of("winner B has lower score", body(TeamSide.B, 21, 18))
        );
    }

    @Test
    void unsupportedWinnerTeamReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());

        mockMvc.perform(post(
                        "/api/matches/{matchId}/complete",
                        fixture.matchId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"winnerTeam\":\"C\"}"))
                .andExpect(status().isBadRequest());

        assertPlayingRuntime(fixture);
    }

    @Test
    void unknownMatchCannotComplete() throws Exception {
        complete(UUID.randomUUID(), body(TeamSide.A, null, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void createdMatchCannotComplete() throws Exception {
        RuntimeFixture fixture = createFixture();

        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isConflict());

        assertCreatedRuntime(fixture);
    }

    @Test
    void completedMatchCannotCompleteAgain() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isOk());

        complete(fixture.matchId(), body(TeamSide.B, null, null))
                .andExpect(status().isConflict());

        Match completed = match(fixture.matchId());
        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.result().winnerTeam()).isEqualTo(TeamSide.A);
        assertThat(completed.resultVersion()).isEqualTo(1);
    }

    @Test
    void cancelledMatchCannotComplete() throws Exception {
        RuntimeFixture fixture = createFixture();
        cancel(fixture.matchId()).andExpect(status().isOk());

        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status())
                .isEqualTo(MatchStatus.CANCELLED);
        assertCreatedResources(fixture);
    }

    @Test
    void completeRejectsPlayingMatchWhoseCourtIsNotPlaying() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        releaseCourtFixture(fixture.sessionCourtId());

        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(match(fixture.matchId()).result()).isNull();
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.PLAYING);
    }

    @Test
    void completeRejectsStaleParticipantAndRollsBackAllOtherResources()
            throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        UUID staleParticipantId = fixture.participantIds().getLast();
        releaseParticipantFixture(staleParticipantId);

        complete(fixture.matchId(), body(TeamSide.A, 21, 18))
                .andExpect(status().isConflict());

        Match playing = match(fixture.matchId());
        assertThat(playing.status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(playing.result()).isNull();
        assertThat(playing.resultVersion()).isZero();
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertParticipantStatuses(
                fixture.participantIds().subList(0, 3),
                ParticipantStatus.PLAYING
        );
        assertThat(participant(staleParticipantId).status())
                .isEqualTo(ParticipantStatus.WAITING);
    }

    @Test
    void completeRejectsStalePersistedComposition() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        MatchParticipantEntity removed = matchParticipantRepository
                .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(fixture.matchId())
                .getFirst();
        matchParticipantRepository.deleteById(removed.getId());

        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isConflict());

        assertPlayingRuntime(fixture);
    }

    @Test
    void cancelCreatedMatchDoesNotTouchUnreservedResources() throws Exception {
        RuntimeFixture fixture = createFixture();

        cancel(fixture.matchId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.winnerTeam").doesNotExist())
                .andExpect(jsonPath("$.resultVersion").value(0));

        Match cancelled = match(fixture.matchId());
        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(cancelled.startedAt()).isNull();
        assertThat(cancelled.result()).isNull();
        assertThat(cancelled.resultVersion()).isZero();
        assertCreatedResources(fixture);
    }

    @Test
    void cancelPlayingMatchAtomicallyReleasesOwnedResources() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        Instant startedAt = match(fixture.matchId()).startedAt();
        List<Instant> checkedInBefore = fixture.participantIds().stream()
                .map(this::participant)
                .map(SessionParticipant::checkedInAt)
                .toList();

        cancel(fixture.matchId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.winnerTeam").doesNotExist())
                .andExpect(jsonPath("$.resultVersion").value(0));

        Match cancelled = match(fixture.matchId());
        assertThat(cancelled.startedAt()).isEqualTo(startedAt);
        assertThat(cancelled.result()).isNull();
        assertThat(cancelled.resultVersion()).isZero();
        assertReleasedResources(
                fixture,
                cancelled.cancelledAt(),
                checkedInBefore
        );
    }

    @Test
    void unknownMatchCannotCancel() throws Exception {
        cancel(UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void completedMatchCannotCancel() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        complete(fixture.matchId(), body(TeamSide.A, null, null))
                .andExpect(status().isOk());

        cancel(fixture.matchId()).andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status())
                .isEqualTo(MatchStatus.COMPLETED);
        assertThat(match(fixture.matchId()).resultVersion()).isEqualTo(1);
    }

    @Test
    void cancelledMatchCannotCancelAgain() throws Exception {
        RuntimeFixture fixture = createFixture();
        cancel(fixture.matchId()).andExpect(status().isOk());

        cancel(fixture.matchId()).andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status())
                .isEqualTo(MatchStatus.CANCELLED);
        assertCreatedResources(fixture);
    }

    @Test
    void cancelRejectsPlayingMatchWhoseCourtIsNotPlaying() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        releaseCourtFixture(fixture.sessionCourtId());

        cancel(fixture.matchId()).andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.PLAYING);
    }

    @Test
    void cancelRejectsStaleParticipantWithoutPartialRelease() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        UUID staleParticipantId = fixture.participantIds().getLast();
        releaseParticipantFixture(staleParticipantId);

        cancel(fixture.matchId()).andExpect(status().isConflict());

        assertThat(match(fixture.matchId()).status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertParticipantStatuses(
                fixture.participantIds().subList(0, 3),
                ParticipantStatus.PLAYING
        );
        assertThat(participant(staleParticipantId).status())
                .isEqualTo(ParticipantStatus.WAITING);
    }

    @Test
    void playingMatchCanStillCancelAfterSessionIsCancelled() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        mockMvc.perform(post(
                        "/api/sessions/{sessionId}/cancel",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk());

        cancel(fixture.matchId()).andExpect(status().isOk());

        Match cancelled = match(fixture.matchId());
        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertReleasedResources(fixture, cancelled.cancelledAt(), null);
    }

    @Test
    void concurrentDoubleCompleteHasExactlyOneWinner() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        ResultBody result = body(TeamSide.A, 21, 18);

        List<Integer> statuses = runConcurrently(
                () -> completeStatus(fixture.matchId(), result),
                () -> completeStatus(fixture.matchId(), result)
        );

        assertOneOkAndOneConflict(statuses);
        Match completed = match(fixture.matchId());
        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.resultVersion()).isEqualTo(1);
        assertThat(completed.result().winnerTeam()).isEqualTo(TeamSide.A);
        assertReleasedResources(fixture, completed.completedAt(), null);
    }

    @Test
    void concurrentDoubleCancelHasExactlyOneWinner() throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());

        List<Integer> statuses = runConcurrently(
                () -> cancelStatus(fixture.matchId()),
                () -> cancelStatus(fixture.matchId())
        );

        assertOneOkAndOneConflict(statuses);
        Match cancelled = match(fixture.matchId());
        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(cancelled.result()).isNull();
        assertThat(cancelled.resultVersion()).isZero();
        assertReleasedResources(fixture, cancelled.cancelledAt(), null);
    }

    @Test
    void concurrentCompleteVersusCancelProducesOneConsistentTerminalState()
            throws Exception {
        RuntimeFixture fixture = createFixture();
        start(fixture.matchId()).andExpect(status().isOk());
        ResultBody result = body(TeamSide.B, 17, 21);

        List<Integer> statuses = runConcurrently(
                () -> completeStatus(fixture.matchId(), result),
                () -> cancelStatus(fixture.matchId())
        );

        assertOneOkAndOneConflict(statuses);
        Match resolved = match(fixture.matchId());
        if (resolved.status() == MatchStatus.COMPLETED) {
            assertThat(resolved.result()).isNotNull();
            assertThat(resolved.result().winnerTeam()).isEqualTo(TeamSide.B);
            assertThat(resolved.resultVersion()).isEqualTo(1);
            assertThat(resolved.cancelledAt()).isNull();
            assertReleasedResources(fixture, resolved.completedAt(), null);
        } else {
            assertThat(resolved.status()).isEqualTo(MatchStatus.CANCELLED);
            assertThat(resolved.result()).isNull();
            assertThat(resolved.resultVersion()).isZero();
            assertThat(resolved.completedAt()).isNull();
            assertReleasedResources(fixture, resolved.cancelledAt(), null);
        }
    }

    private RuntimeFixture createFixture() {
        Instant now = Instant.now().minusSeconds(10);
        Venue venue = Venue.create("Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();
        Court court = Court.create(
                venueId,
                "Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository
                .saveAndFlush(CourtEntity.from(court))
                .getId();
        Session session = Session.create(
                venueId,
                "Resolve Match Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        ).start(now.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                now.plusSeconds(2)
        );
        UUID sessionCourtId = sessionCourtRepository
                .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                .getId();

        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Player player = Player.create("Player " + UUID.randomUUID(), now);
            UUID playerId = playerRepository
                    .saveAndFlush(PlayerEntity.from(player))
                    .getId();
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    now
            ).checkIn(now.plusSeconds(2));
            participantIds.add(sessionParticipantRepository
                    .saveAndFlush(SessionParticipantEntity.from(participant))
                    .getId());
        }

        Match match = Match.create(
                sessionId,
                sessionCourtId,
                MatchSource.MANUAL,
                now.plusSeconds(3)
        );
        matchRepository.saveAndFlush(MatchEntity.from(match));
        matchParticipantRepository.saveAllAndFlush(List.of(
                assignment(match.id(), participantIds.get(0), TeamSide.A, 1),
                assignment(match.id(), participantIds.get(1), TeamSide.A, 2),
                assignment(match.id(), participantIds.get(2), TeamSide.B, 1),
                assignment(match.id(), participantIds.get(3), TeamSide.B, 2)
        ));

        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                participantIds,
                match.id()
        );
    }

    private MatchParticipantEntity assignment(
            UUID matchId,
            UUID participantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return MatchParticipantEntity.from(MatchParticipant.assign(
                matchId,
                participantId,
                teamSide,
                teamSlot
        ));
    }

    private void releaseCourtFixture(UUID sessionCourtId) {
        SessionCourtEntity entity = sessionCourtRepository
                .findById(sessionCourtId)
                .orElseThrow();
        entity.applyRuntimeState(
                entity.toDomain().releaseFromMatch(transitionTime())
        );
        sessionCourtRepository.saveAndFlush(entity);
    }

    private void releaseParticipantFixture(UUID participantId) {
        SessionParticipantEntity entity = sessionParticipantRepository
                .findById(participantId)
                .orElseThrow();
        entity.applyRuntimeState(
                entity.toDomain().releaseFromMatch(transitionTime())
        );
        sessionParticipantRepository.saveAndFlush(entity);
    }

    private void assertPlayingRuntime(RuntimeFixture fixture) {
        Match playing = match(fixture.matchId());
        assertThat(playing.status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(playing.result()).isNull();
        assertThat(playing.resultVersion()).isZero();
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.PLAYING);
    }

    private void assertCreatedRuntime(RuntimeFixture fixture) {
        assertThat(match(fixture.matchId()).status()).isEqualTo(MatchStatus.CREATED);
        assertCreatedResources(fixture);
    }

    private void assertCreatedResources(RuntimeFixture fixture) {
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.WAITING);
    }

    private void assertReleasedResources(
            RuntimeFixture fixture,
            Instant releasedAt,
            List<Instant> checkedInBefore
    ) {
        assertThat(releasedAt).isNotNull();
        assertThat(court(fixture.sessionCourtId()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        List<SessionParticipant> participants = fixture.participantIds().stream()
                .map(this::participant)
                .toList();
        assertThat(participants).allSatisfy(participant -> {
            assertThat(participant.status()).isEqualTo(ParticipantStatus.WAITING);
            assertThat(participant.waitingSince()).isEqualTo(releasedAt);
            assertThat(participant.pausedAt()).isNull();
            assertThat(participant.leftAt()).isNull();
        });
        if (checkedInBefore != null) {
            assertThat(participants.stream()
                    .map(SessionParticipant::checkedInAt)
                    .toList()).containsExactlyElementsOf(checkedInBefore);
        }
    }

    private void assertParticipantStatuses(
            List<UUID> participantIds,
            ParticipantStatus expectedStatus
    ) {
        participantIds.forEach(participantId -> assertThat(
                participant(participantId).status()
        ).isEqualTo(expectedStatus));
    }

    private List<Integer> runConcurrently(
            ThrowingStatusAction first,
            ThrowingStatusAction second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstFuture = executor.submit(
                    () -> runAfterGate(first, ready, startGate)
            );
            Future<Integer> secondFuture = executor.submit(
                    () -> runAfterGate(second, ready, startGate)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();
            return List.of(
                    firstFuture.get(15, TimeUnit.SECONDS),
                    secondFuture.get(15, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private int runAfterGate(
            ThrowingStatusAction action,
            CountDownLatch ready,
            CountDownLatch startGate
    ) throws Exception {
        ready.countDown();
        if (!startGate.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent resolution gate timed out");
        }
        return action.execute();
    }

    private void assertOneOkAndOneConflict(List<Integer> statuses) {
        assertThat(statuses).containsExactlyInAnyOrder(
                HttpStatus.OK.value(),
                HttpStatus.CONFLICT.value()
        );
    }

    private int completeStatus(UUID matchId, ResultBody body) throws Exception {
        return complete(matchId, body).andReturn().getResponse().getStatus();
    }

    private int cancelStatus(UUID matchId) throws Exception {
        return cancel(matchId).andReturn().getResponse().getStatus();
    }

    private ResultActions start(UUID matchId) throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/start", matchId));
    }

    private ResultActions complete(UUID matchId, ResultBody body) throws Exception {
        return mockMvc.perform(post(
                        "/api/matches/{matchId}/complete",
                        matchId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions cancel(UUID matchId) throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/cancel", matchId));
    }

    private Match match(UUID matchId) {
        return matchRepository.findById(matchId).orElseThrow().toDomain();
    }

    private SessionCourt court(UUID sessionCourtId) {
        return sessionCourtRepository.findById(sessionCourtId)
                .orElseThrow()
                .toDomain();
    }

    private SessionParticipant participant(UUID participantId) {
        return sessionParticipantRepository.findById(participantId)
                .orElseThrow()
                .toDomain();
    }

    private List<MatchParticipant> composition(UUID matchId) {
        return matchParticipantRepository
                .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(matchId)
                .stream()
                .map(MatchParticipantEntity::toDomain)
                .toList();
    }

    private Instant transitionTime() {
        return Instant.now().plusSeconds(60);
    }

    private static ResultBody body(
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) {
        return new ResultBody(winnerTeam, teamAScore, teamBScore);
    }

    private record ResultBody(
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) {
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds,
            UUID matchId
    ) {
        private RuntimeFixture {
            participantIds = List.copyOf(participantIds);
        }
    }

    @FunctionalInterface
    private interface ThrowingStatusAction {
        int execute() throws Exception;
    }
}

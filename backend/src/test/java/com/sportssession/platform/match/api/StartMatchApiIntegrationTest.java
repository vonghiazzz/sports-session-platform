package com.sportssession.platform.match.api;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchResult;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class StartMatchApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    void validCreatedMatchStartsAllResourcesWithOneTimestamp() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatch(
                fixture.sessionId(),
                fixture.courtIds().getFirst(),
                fixture.participantIds(),
                false
        );
        List<MatchParticipant> compositionBefore = composition(matchId);
        List<Instant> checkedInBefore = fixture.participantIds().stream()
                .map(this::participant)
                .map(SessionParticipant::checkedInAt)
                .toList();

        start(matchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(matchId.toString()))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.winnerTeam").doesNotExist())
                .andExpect(jsonPath("$.teamAScore").doesNotExist())
                .andExpect(jsonPath("$.teamBScore").doesNotExist())
                .andExpect(jsonPath("$.resultVersion").value(0))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andExpect(jsonPath("$.participants[0].teamSide").value("A"))
                .andExpect(jsonPath("$.participants[0].teamSlot").value(1))
                .andExpect(jsonPath("$.participants[1].teamSide").value("A"))
                .andExpect(jsonPath("$.participants[1].teamSlot").value(2))
                .andExpect(jsonPath("$.participants[2].teamSide").value("B"))
                .andExpect(jsonPath("$.participants[2].teamSlot").value(1))
                .andExpect(jsonPath("$.participants[3].teamSide").value("B"))
                .andExpect(jsonPath("$.participants[3].teamSlot").value(2));

        Match started = match(matchId);
        assertThat(started.status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(started.startedAt()).isNotNull();
        assertThat(started.result()).isNull();
        assertThat(started.resultVersion()).isZero();
        assertThat(composition(matchId)).containsExactlyElementsOf(compositionBefore);

        SessionCourt playingCourt = court(fixture.courtIds().getFirst());
        assertThat(playingCourt.status()).isEqualTo(SessionCourtStatus.PLAYING);
        assertThat(playingCourt.updatedAt()).isEqualTo(started.startedAt());

        List<SessionParticipant> playingParticipants = fixture.participantIds()
                .stream()
                .map(this::participant)
                .toList();
        assertThat(playingParticipants)
                .allSatisfy(participant -> {
                    assertThat(participant.status())
                            .isEqualTo(ParticipantStatus.PLAYING);
                    assertThat(participant.waitingSince()).isNull();
                    assertThat(participant.pausedAt()).isNull();
                    assertThat(participant.leftAt()).isNull();
                    assertThat(participant.updatedAt()).isEqualTo(started.startedAt());
                });
        assertThat(playingParticipants.stream()
                .map(SessionParticipant::checkedInAt)
                .toList()).containsExactlyElementsOf(checkedInBefore);
    }

    @Test
    void unknownMatchReturnsNotFound() throws Exception {
        start(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.startsWith("Match not found:")));
    }

    @Test
    void startingSameMatchTwiceReturnsConflict() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());

        start(matchId).andExpect(status().isOk());
        start(matchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Match must be CREATED to Start: " + matchId));
    }

    @Test
    void cancelledMatchCannotStart() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        updateMatch(matchId, match(matchId).cancel(transitionTime()));

        start(matchId).andExpect(status().isConflict());

        assertThat(match(matchId).status()).isEqualTo(MatchStatus.CANCELLED);
        assertResourcesAvailable(fixture.courtIds().getFirst(), fixture.participantIds());
    }

    @Test
    void completedMatchCannotStart() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        Instant startedAt = transitionTime();
        Match completed = match(matchId)
                .start(startedAt)
                .complete(
                        MatchResult.winnerOnly(TeamSide.A),
                        startedAt.plusSeconds(1)
                );
        updateMatch(matchId, completed);

        start(matchId).andExpect(status().isConflict());

        assertThat(match(matchId).status()).isEqualTo(MatchStatus.COMPLETED);
        assertResourcesAvailable(fixture.courtIds().getFirst(), fixture.participantIds());
    }

    @Test
    void sessionThatIsNotInProgressRejectsStart() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        SessionEntity entity = sessionRepository.findById(fixture.sessionId())
                .orElseThrow();
        entity.applyRuntimeState(entity.toDomain().complete(transitionTime()));
        sessionRepository.saveAndFlush(entity);

        start(matchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Match can Start only while Session is IN_PROGRESS"));

        assertMatchAndResourcesUnchanged(matchId, fixture, ParticipantStatus.WAITING);
    }

    @Test
    void unavailableCourtAfterCreationRejectsStart() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        SessionCourtEntity entity = sessionCourtRepository
                .findById(fixture.courtIds().getFirst())
                .orElseThrow();
        entity.applyRuntimeState(entity.toDomain().disable(transitionTime()));
        sessionCourtRepository.saveAndFlush(entity);

        start(matchId).andExpect(status().isConflict());

        assertThat(match(matchId).status()).isEqualTo(MatchStatus.CREATED);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.UNAVAILABLE);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.WAITING);
    }

    @Test
    void pausedParticipantAfterCreationRejectsStart() throws Exception {
        assertParticipantConflict(ParticipantStatus.PAUSED);
    }

    @Test
    void leftParticipantAfterCreationRejectsStart() throws Exception {
        assertParticipantConflict(ParticipantStatus.LEFT);
    }

    @Test
    void alreadyPlayingParticipantAfterCreationRejectsStart() throws Exception {
        assertParticipantConflict(ParticipantStatus.PLAYING);
    }

    @Test
    void stalePersistedCompositionReturnsConflict() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        MatchParticipantEntity removed = matchParticipantRepository
                .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(matchId)
                .getFirst();
        matchParticipantRepository.deleteById(removed.getId());

        start(matchId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Persisted Match composition is not startable"));

        assertMatchAndResourcesUnchanged(matchId, fixture, ParticipantStatus.WAITING);
    }

    @Test
    void failedStartRollsBackEveryOtherwiseValidTransition() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        UUID invalidParticipantId = fixture.participantIds().getLast();
        moveParticipantTo(invalidParticipantId, ParticipantStatus.PAUSED);

        start(matchId).andExpect(status().isConflict());

        assertThat(match(matchId).status()).isEqualTo(MatchStatus.CREATED);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(
                fixture.participantIds().subList(0, 3),
                ParticipantStatus.WAITING
        );
        assertThat(participant(invalidParticipantId).status())
                .isEqualTo(ParticipantStatus.PAUSED);
    }

    @Test
    void concurrentStartsOfSameMatchHaveExactlyOneWinner() throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());

        List<Integer> statuses = runConcurrently(
                () -> startStatus(matchId),
                () -> startStatus(matchId)
        );

        assertOneOkAndOneConflict(statuses);
        assertThat(match(matchId).status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertParticipantStatuses(fixture.participantIds(), ParticipantStatus.PLAYING);
    }

    @Test
    void concurrentStartsSharingCourtLeaveLosingMatchAndParticipantsUntouched()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 8);
        List<UUID> firstParticipants = fixture.participantIds().subList(0, 4);
        List<UUID> secondParticipants = fixture.participantIds().subList(4, 8);
        UUID courtId = fixture.courtIds().getFirst();
        UUID firstMatchId = createMatch(
                fixture.sessionId(), courtId, firstParticipants, false
        );
        UUID secondMatchId = createMatch(
                fixture.sessionId(), courtId, secondParticipants, true
        );

        List<Integer> statuses = runConcurrently(
                () -> startStatus(firstMatchId),
                () -> startStatus(secondMatchId)
        );

        assertOneOkAndOneConflict(statuses);
        assertThat(court(courtId).status()).isEqualTo(SessionCourtStatus.PLAYING);
        assertWinnerAndLoserResources(
                firstMatchId,
                firstParticipants,
                secondMatchId,
                secondParticipants
        );
    }

    @Test
    void concurrentStartsSharingParticipantUseDeterministicOrderWithoutPartialState()
            throws Exception {
        RuntimeFixture fixture = createFixture(2, 7);
        UUID sharedParticipant = fixture.participantIds().getFirst();
        List<UUID> firstParticipants = List.of(
                sharedParticipant,
                fixture.participantIds().get(1),
                fixture.participantIds().get(2),
                fixture.participantIds().get(3)
        );
        List<UUID> secondParticipants = List.of(
                sharedParticipant,
                fixture.participantIds().get(4),
                fixture.participantIds().get(5),
                fixture.participantIds().get(6)
        );
        UUID firstMatchId = createMatch(
                fixture.sessionId(),
                fixture.courtIds().get(0),
                firstParticipants,
                false
        );
        UUID secondMatchId = createMatch(
                fixture.sessionId(),
                fixture.courtIds().get(1),
                secondParticipants,
                true
        );

        List<Integer> statuses = runConcurrently(
                () -> startStatus(firstMatchId),
                () -> startStatus(secondMatchId)
        );

        assertOneOkAndOneConflict(statuses);
        assertThat(participant(sharedParticipant).status())
                .isEqualTo(ParticipantStatus.PLAYING);
        assertWinnerAndLoserResources(
                firstMatchId,
                firstParticipants,
                secondMatchId,
                secondParticipants
        );

        UUID winningCourt = match(firstMatchId).status() == MatchStatus.PLAYING
                ? fixture.courtIds().get(0)
                : fixture.courtIds().get(1);
        UUID losingCourt = winningCourt.equals(fixture.courtIds().get(0))
                ? fixture.courtIds().get(1)
                : fixture.courtIds().get(0);
        assertThat(court(winningCourt).status()).isEqualTo(SessionCourtStatus.PLAYING);
        assertThat(court(losingCourt).status()).isEqualTo(SessionCourtStatus.AVAILABLE);
    }

    @Test
    void pauseVersusStartNeverProducesPlayingMatchWithPausedParticipant()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        UUID participantId = fixture.participantIds().getFirst();

        List<Integer> statuses = runConcurrently(
                () -> startStatus(matchId),
                () -> pauseStatus(fixture.sessionId(), participantId)
        );

        assertOneOkAndOneConflict(statuses);
        MatchStatus matchStatus = match(matchId).status();
        ParticipantStatus participantStatus = participant(participantId).status();
        SessionCourtStatus courtStatus = court(
                fixture.courtIds().getFirst()
        ).status();

        if (matchStatus == MatchStatus.PLAYING) {
            assertThat(participantStatus).isEqualTo(ParticipantStatus.PLAYING);
            assertThat(courtStatus).isEqualTo(SessionCourtStatus.PLAYING);
            assertParticipantStatuses(
                    fixture.participantIds(),
                    ParticipantStatus.PLAYING
            );
        } else {
            assertThat(matchStatus).isEqualTo(MatchStatus.CREATED);
            assertThat(participantStatus).isEqualTo(ParticipantStatus.PAUSED);
            assertThat(courtStatus).isEqualTo(SessionCourtStatus.AVAILABLE);
            assertParticipantStatuses(
                    fixture.participantIds().subList(1, 4),
                    ParticipantStatus.WAITING
            );
        }
    }

    private void assertParticipantConflict(ParticipantStatus staleStatus)
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        UUID matchId = createMatchFor(fixture, 0, fixture.participantIds());
        UUID staleParticipant = fixture.participantIds().getLast();
        moveParticipantTo(staleParticipant, staleStatus);

        start(matchId).andExpect(status().isConflict());

        assertThat(match(matchId).status()).isEqualTo(MatchStatus.CREATED);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(
                fixture.participantIds().subList(0, 3),
                ParticipantStatus.WAITING
        );
        assertThat(participant(staleParticipant).status()).isEqualTo(staleStatus);
    }

    private void assertMatchAndResourcesUnchanged(
            UUID matchId,
            RuntimeFixture fixture,
            ParticipantStatus participantStatus
    ) {
        assertThat(match(matchId).status()).isEqualTo(MatchStatus.CREATED);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(fixture.participantIds(), participantStatus);
    }

    private void assertResourcesAvailable(
            UUID courtId,
            List<UUID> participantIds
    ) {
        assertThat(court(courtId).status()).isEqualTo(SessionCourtStatus.AVAILABLE);
        assertParticipantStatuses(participantIds, ParticipantStatus.WAITING);
    }

    private void assertWinnerAndLoserResources(
            UUID firstMatchId,
            List<UUID> firstParticipants,
            UUID secondMatchId,
            List<UUID> secondParticipants
    ) {
        MatchStatus firstStatus = match(firstMatchId).status();
        MatchStatus secondStatus = match(secondMatchId).status();
        assertThat(List.of(firstStatus, secondStatus))
                .containsExactlyInAnyOrder(MatchStatus.PLAYING, MatchStatus.CREATED);

        List<UUID> winningParticipants = firstStatus == MatchStatus.PLAYING
                ? firstParticipants
                : secondParticipants;
        List<UUID> losingParticipants = firstStatus == MatchStatus.CREATED
                ? firstParticipants
                : secondParticipants;

        assertParticipantStatuses(winningParticipants, ParticipantStatus.PLAYING);
        losingParticipants.stream()
                .filter(participantId -> !winningParticipants.contains(participantId))
                .forEach(participantId -> assertThat(participant(participantId).status())
                        .isEqualTo(ParticipantStatus.WAITING));
    }

    private void assertParticipantStatuses(
            List<UUID> participantIds,
            ParticipantStatus expectedStatus
    ) {
        participantIds.forEach(participantId -> assertThat(
                participant(participantId).status()
        ).isEqualTo(expectedStatus));
    }

    private void assertOneOkAndOneConflict(List<Integer> statuses) {
        assertThat(statuses).containsExactlyInAnyOrder(
                HttpStatus.OK.value(),
                HttpStatus.CONFLICT.value()
        );
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
            throw new IllegalStateException("Concurrent start gate timed out");
        }
        return action.execute();
    }

    private int startStatus(UUID matchId) throws Exception {
        return start(matchId).andReturn().getResponse().getStatus();
    }

    private int pauseStatus(UUID sessionId, UUID participantId) throws Exception {
        return mockMvc.perform(post(
                        "/api/sessions/{sessionId}/participants/{participantId}/pause",
                        sessionId,
                        participantId
                ))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions start(UUID matchId)
            throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/start", matchId));
    }

    private RuntimeFixture createFixture(int courtCount, int participantCount) {
        Instant now = Instant.now();
        Venue venue = Venue.create("Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();

        Session session = Session.create(
                venueId,
                "Start Match Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        ).start(now.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();

        List<UUID> sessionCourtIds = new ArrayList<>();
        for (int index = 0; index < courtCount; index++) {
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
            SessionCourt sessionCourt = SessionCourt.allocate(
                    sessionId,
                    courtId,
                    now.plusSeconds(2)
            );
            sessionCourtIds.add(sessionCourtRepository
                    .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                    .getId());
        }

        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < participantCount; index++) {
            Player player = Player.create("Player " + UUID.randomUUID(), now);
            UUID playerId = playerRepository
                    .saveAndFlush(PlayerEntity.from(player))
                    .getId();
            SessionParticipant waiting = SessionParticipant.register(
                    sessionId,
                    playerId,
                    now
            ).checkIn(now.plusSeconds(2));
            participantIds.add(sessionParticipantRepository
                    .saveAndFlush(SessionParticipantEntity.from(waiting))
                    .getId());
        }

        return new RuntimeFixture(sessionId, sessionCourtIds, participantIds);
    }

    private UUID createMatchFor(
            RuntimeFixture fixture,
            int courtIndex,
            List<UUID> participantIds
    ) {
        return createMatch(
                fixture.sessionId(),
                fixture.courtIds().get(courtIndex),
                participantIds,
                false
        );
    }

    private UUID createMatch(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds,
            boolean reversePersistenceOrder
    ) {
        Match match = Match.create(
                sessionId,
                sessionCourtId,
                MatchSource.MANUAL,
                Instant.now()
        );
        matchRepository.saveAndFlush(MatchEntity.from(match));

        List<MatchParticipantEntity> assignments = new ArrayList<>(List.of(
                participantEntity(match.id(), participantIds.get(0), TeamSide.A, 1),
                participantEntity(match.id(), participantIds.get(1), TeamSide.A, 2),
                participantEntity(match.id(), participantIds.get(2), TeamSide.B, 1),
                participantEntity(match.id(), participantIds.get(3), TeamSide.B, 2)
        ));
        if (reversePersistenceOrder) {
            Collections.reverse(assignments);
        }
        matchParticipantRepository.saveAllAndFlush(assignments);

        return match.id();
    }

    private MatchParticipantEntity participantEntity(
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

    private void moveParticipantTo(
            UUID participantId,
            ParticipantStatus targetStatus
    ) {
        SessionParticipantEntity entity = sessionParticipantRepository
                .findById(participantId)
                .orElseThrow();
        SessionParticipant waiting = entity.toDomain();
        SessionParticipant updated = switch (targetStatus) {
            case PAUSED -> waiting.pause(transitionTime());
            case LEFT -> waiting.leave(transitionTime());
            case PLAYING -> waiting.startMatch(transitionTime());
            default -> throw new IllegalArgumentException(
                    "Unsupported stale participant status: " + targetStatus
            );
        };
        entity.applyRuntimeState(updated);
        sessionParticipantRepository.saveAndFlush(entity);
    }

    private void updateMatch(UUID matchId, Match updated) {
        MatchEntity entity = matchRepository.findById(matchId).orElseThrow();
        entity.applyRuntimeState(updated);
        matchRepository.saveAndFlush(entity);
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

    @FunctionalInterface
    private interface ThrowingStatusAction {
        int execute() throws Exception;
    }

    private record RuntimeFixture(
            UUID sessionId,
            List<UUID> courtIds,
            List<UUID> participantIds
    ) {
        private RuntimeFixture {
            courtIds = List.copyOf(courtIds);
            participantIds = List.copyOf(participantIds);
        }
    }
}

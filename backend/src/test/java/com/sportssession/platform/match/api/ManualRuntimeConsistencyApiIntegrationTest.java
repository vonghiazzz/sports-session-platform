package com.sportssession.platform.match.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.infrastructure.CourtRepository;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
class ManualRuntimeConsistencyApiIntegrationTest extends PostgreSqlIntegrationTest {

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
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

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
    void fullManualSessionLoopRejectsEarlyCompletionThenCompletesCleanly()
            throws Exception {
        ApiFixture fixture = createApiFixture();

        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);

        UUID matchId = createMatch(fixture);
        assertMatchStatus(matchId, MatchStatus.CREATED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);

        startMatch(matchId).andExpect(status().isOk());
        assertMatchStatus(matchId, MatchStatus.PLAYING);
        assertResources(fixture, SessionCourtStatus.PLAYING, ParticipantStatus.PLAYING);

        completeSession(fixture.sessionId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Session cannot complete while a Match is PLAYING"
                ));
        assertSessionStatus(fixture.sessionId(), SessionStatus.IN_PROGRESS);
        assertMatchStatus(matchId, MatchStatus.PLAYING);
        assertResources(fixture, SessionCourtStatus.PLAYING, ParticipantStatus.PLAYING);

        completeMatch(matchId).andExpect(status().isOk());

        Match completed = matchRepository.findById(matchId)
                .orElseThrow()
                .toDomain();
        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.result()).isNotNull();
        assertThat(completed.resultVersion()).isEqualTo(1);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);
        fixture.participantIds().forEach(participantId -> assertThat(
                sessionParticipantRepository.findById(participantId)
                        .orElseThrow()
                        .toDomain()
                        .waitingSince()
        ).isEqualTo(completed.completedAt()));

        completeSession(fixture.sessionId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertSessionStatus(fixture.sessionId(), SessionStatus.COMPLETED);
        assertMatchStatus(matchId, MatchStatus.COMPLETED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);
        assertThat(matchRepository.existsBySessionIdAndStatus(
                fixture.sessionId(),
                MatchStatus.PLAYING
        )).isFalse();
    }

    @Test
    void completeSessionAfterPlayingMatchCancellationSucceeds()
            throws Exception {
        ApiFixture fixture = createApiFixture();
        UUID matchId = createMatch(fixture);
        startMatch(matchId).andExpect(status().isOk());

        cancelMatch(matchId).andExpect(status().isOk());
        assertMatchStatus(matchId, MatchStatus.CANCELLED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);

        completeSession(fixture.sessionId()).andExpect(status().isOk());
        assertSessionStatus(fixture.sessionId(), SessionStatus.COMPLETED);
    }

    @Test
    void createdMatchDoesNotBlockSessionCompletionAndCannotStartAfterward()
            throws Exception {
        ApiFixture fixture = createApiFixture();
        UUID matchId = createMatch(fixture);

        completeSession(fixture.sessionId()).andExpect(status().isOk());

        assertSessionStatus(fixture.sessionId(), SessionStatus.COMPLETED);
        assertMatchStatus(matchId, MatchStatus.CREATED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);

        startMatch(matchId).andExpect(status().isConflict());
        assertMatchStatus(matchId, MatchStatus.CREATED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);
    }

    @Test
    void historicalMatchesDoNotBlockAndReleasedResourcesCanBeReused()
            throws Exception {
        ApiFixture fixture = createApiFixture();

        UUID completedMatchId = createMatch(fixture);
        startMatch(completedMatchId).andExpect(status().isOk());
        completeMatch(completedMatchId).andExpect(status().isOk());

        UUID firstCancelledMatchId = createMatch(fixture);
        startMatch(firstCancelledMatchId).andExpect(status().isOk());
        cancelMatch(firstCancelledMatchId).andExpect(status().isOk());

        UUID secondCancelledMatchId = createMatch(fixture);
        startMatch(secondCancelledMatchId).andExpect(status().isOk());
        cancelMatch(secondCancelledMatchId).andExpect(status().isOk());

        UUID createdMatchId = createMatch(fixture);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);

        completeSession(fixture.sessionId()).andExpect(status().isOk());

        assertSessionStatus(fixture.sessionId(), SessionStatus.COMPLETED);
        assertMatchStatus(completedMatchId, MatchStatus.COMPLETED);
        assertMatchStatus(firstCancelledMatchId, MatchStatus.CANCELLED);
        assertMatchStatus(secondCancelledMatchId, MatchStatus.CANCELLED);
        assertMatchStatus(createdMatchId, MatchStatus.CREATED);
        assertThat(matchRepository.existsBySessionIdAndStatus(
                fixture.sessionId(),
                MatchStatus.PLAYING
        )).isFalse();
    }

    @Test
    void startMatchVersusCompleteSessionSerializesOnSessionRow()
            throws Exception {
        ApiFixture fixture = createApiFixture();
        UUID matchId = createMatch(fixture);

        List<Integer> statuses = runConcurrently(
                () -> startStatus(matchId),
                () -> completeSessionStatus(fixture.sessionId())
        );

        assertThat(statuses).containsExactlyInAnyOrder(
                HttpStatus.OK.value(),
                HttpStatus.CONFLICT.value()
        );

        SessionStatus sessionStatus = sessionStatus(fixture.sessionId());
        MatchStatus matchStatus = matchStatus(matchId);

        if (matchStatus == MatchStatus.PLAYING) {
            assertThat(sessionStatus).isEqualTo(SessionStatus.IN_PROGRESS);
            assertResources(
                    fixture,
                    SessionCourtStatus.PLAYING,
                    ParticipantStatus.PLAYING
            );
        } else {
            assertThat(matchStatus).isEqualTo(MatchStatus.CREATED);
            assertThat(sessionStatus).isEqualTo(SessionStatus.COMPLETED);
            assertResources(
                    fixture,
                    SessionCourtStatus.AVAILABLE,
                    ParticipantStatus.WAITING
            );
        }

        assertThat(sessionStatus == SessionStatus.COMPLETED
                && matchStatus == MatchStatus.PLAYING).isFalse();
    }

    @Test
    void completeMatchVersusCompleteSessionNeverLeavesPlayingMatchInCompletedSession()
            throws Exception {
        ApiFixture fixture = createApiFixture();
        UUID matchId = createMatch(fixture);
        startMatch(matchId).andExpect(status().isOk());

        List<Integer> statuses = runConcurrently(
                () -> completeMatchStatus(matchId),
                () -> completeSessionStatus(fixture.sessionId())
        );

        assertThat(statuses.getFirst()).isEqualTo(HttpStatus.OK.value());
        assertThat(statuses.getLast()).isIn(
                HttpStatus.OK.value(),
                HttpStatus.CONFLICT.value()
        );
        assertMatchStatus(matchId, MatchStatus.COMPLETED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);
        assertThat(sessionStatus(fixture.sessionId())).isIn(
                SessionStatus.IN_PROGRESS,
                SessionStatus.COMPLETED
        );
    }

    @Test
    void cancelMatchVersusCompleteSessionNeverLeavesPlayingMatchInCompletedSession()
            throws Exception {
        ApiFixture fixture = createApiFixture();
        UUID matchId = createMatch(fixture);
        startMatch(matchId).andExpect(status().isOk());

        List<Integer> statuses = runConcurrently(
                () -> cancelMatchStatus(matchId),
                () -> completeSessionStatus(fixture.sessionId())
        );

        assertThat(statuses.getFirst()).isEqualTo(HttpStatus.OK.value());
        assertThat(statuses.getLast()).isIn(
                HttpStatus.OK.value(),
                HttpStatus.CONFLICT.value()
        );
        assertMatchStatus(matchId, MatchStatus.CANCELLED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE, ParticipantStatus.WAITING);
        assertThat(sessionStatus(fixture.sessionId())).isIn(
                SessionStatus.IN_PROGRESS,
                SessionStatus.COMPLETED
        );
    }

    private ApiFixture createApiFixture() throws Exception {
        List<UUID> playerIds = List.of(
                createPlayer("Player A"),
                createPlayer("Player B"),
                createPlayer("Player C"),
                createPlayer("Player D")
        );
        UUID venueId = createdId(
                "/api/venues",
                Map.of(
                        "name", "Venue " + UUID.randomUUID(),
                        "active", true
                )
        );
        UUID courtId = createdId(
                "/api/venues/{venueId}/courts",
                Map.of(
                        "name", "Court " + UUID.randomUUID(),
                        "sport", "BADMINTON",
                        "active", true
                ),
                venueId
        );

        Instant now = Instant.now();
        UUID sessionId = createdId(
                "/api/sessions",
                Map.of(
                        "venueId", venueId,
                        "title", "Manual Runtime " + UUID.randomUUID(),
                        "sport", "BADMINTON",
                        "matchFormat", "DOUBLES",
                        "plannedStartAt", now.plus(1, ChronoUnit.HOURS),
                        "plannedEndAt", now.plus(3, ChronoUnit.HOURS)
                )
        );
        UUID sessionCourtId = createdId(
                "/api/sessions/{sessionId}/courts",
                Map.of("courtId", courtId),
                sessionId
        );
        List<UUID> participantIds = playerIds.stream()
                .map(playerId -> {
                    try {
                        return createdId(
                                "/api/sessions/{sessionId}/participants",
                                Map.of("playerId", playerId),
                                sessionId
                        );
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                })
                .toList();

        mockMvc.perform(post("/api/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isOk());
        for (UUID participantId : participantIds) {
            mockMvc.perform(post(
                            "/api/sessions/{sessionId}/participants/{participantId}/check-in",
                            sessionId,
                            participantId
                    ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITING"));
        }

        return new ApiFixture(sessionId, sessionCourtId, participantIds);
    }

    private UUID createPlayer(String displayName) throws Exception {
        return createdId(
                "/api/players",
                Map.of(
                        "displayName", displayName + " " + UUID.randomUUID(),
                        "sport", "BADMINTON",
                        "skillLevel", "INTERMEDIATE"
                )
        );
    }

    private UUID createMatch(ApiFixture fixture) throws Exception {
        List<Map<String, Object>> participants = List.of(
                assignment(fixture.participantIds().get(0), "A", 1),
                assignment(fixture.participantIds().get(1), "A", 2),
                assignment(fixture.participantIds().get(2), "B", 1),
                assignment(fixture.participantIds().get(3), "B", 2)
        );
        return createdId(
                "/api/sessions/{sessionId}/matches",
                Map.of(
                        "sessionCourtId", fixture.sessionCourtId(),
                        "participants", participants
                ),
                fixture.sessionId()
        );
    }

    private Map<String, Object> assignment(
            UUID participantId,
            String teamSide,
            int teamSlot
    ) {
        return Map.of(
                "sessionParticipantId", participantId,
                "teamSide", teamSide,
                "teamSlot", teamSlot
        );
    }

    private UUID createdId(
            String path,
            Object body,
            Object... pathVariables
    ) throws Exception {
        MvcResult result = mockMvc.perform(post(path, pathVariables)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
        return UUID.fromString(response.get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions startMatch(UUID matchId)
            throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/start", matchId));
    }

    private org.springframework.test.web.servlet.ResultActions completeMatch(UUID matchId)
            throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/complete", matchId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "winnerTeam", "A",
                        "teamAScore", 21,
                        "teamBScore", 17
                ))));
    }

    private org.springframework.test.web.servlet.ResultActions cancelMatch(UUID matchId)
            throws Exception {
        return mockMvc.perform(post("/api/matches/{matchId}/cancel", matchId));
    }

    private org.springframework.test.web.servlet.ResultActions completeSession(UUID sessionId)
            throws Exception {
        return mockMvc.perform(post(
                "/api/sessions/{sessionId}/complete",
                sessionId
        ));
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
        return startMatch(matchId).andReturn().getResponse().getStatus();
    }

    private int completeMatchStatus(UUID matchId) throws Exception {
        return completeMatch(matchId).andReturn().getResponse().getStatus();
    }

    private int cancelMatchStatus(UUID matchId) throws Exception {
        return cancelMatch(matchId).andReturn().getResponse().getStatus();
    }

    private int completeSessionStatus(UUID sessionId) throws Exception {
        return completeSession(sessionId).andReturn().getResponse().getStatus();
    }

    private void assertSessionStatus(UUID sessionId, SessionStatus expected) {
        assertThat(sessionStatus(sessionId)).isEqualTo(expected);
    }

    private SessionStatus sessionStatus(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow()
                .getStatus();
    }

    private void assertMatchStatus(UUID matchId, MatchStatus expected) {
        assertThat(matchStatus(matchId)).isEqualTo(expected);
    }

    private MatchStatus matchStatus(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow()
                .getStatus();
    }

    private void assertResources(
            ApiFixture fixture,
            SessionCourtStatus courtStatus,
            ParticipantStatus participantStatus
    ) {
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow()
                .getStatus()).isEqualTo(courtStatus);
        fixture.participantIds().forEach(participantId -> assertThat(
                sessionParticipantRepository.findById(participantId)
                        .orElseThrow()
                        .getStatus()
        ).isEqualTo(participantStatus));
    }

    private record ApiFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
    }

    @FunctionalInterface
    private interface ThrowingStatusAction {
        int execute() throws Exception;
    }
}

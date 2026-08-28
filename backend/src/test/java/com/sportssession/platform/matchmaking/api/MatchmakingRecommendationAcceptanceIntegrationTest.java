package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.application.CreateAndStartRecommendedMatchCommand;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.RecommendedMatchParticipantAssignment;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.session.infrastructure.SessionCourtEntity;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MatchmakingRecommendationAcceptanceIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final String GENERATE_ENDPOINT =
            "/api/sessions/{sessionId}/courts/{sessionCourtId}"
                    + "/match-recommendations";
    private static final String ACCEPT_ENDPOINT =
            GENERATE_ENDPOINT + "/accept";
    private static final Instant BASE_TIME =
            Instant.parse("2026-08-28T09:00:00Z");
    private static final Instant OPERATION_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    @TestBean(name = "clock", enforceOverride = true)
    private Clock clock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchService matchService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    private static Clock clock() {
        return Clock.fixed(OPERATION_TIME, ZoneOffset.UTC);
    }

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void realGenerateThenAcceptStartsRecommendedMatchAtomically()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        JsonNode recommendation = generate(fixture, 0);
        Map<String, Object> body = acceptBody(recommendation);

        MvcResult accepted = accept(fixture, 0, body)
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.source").value("RECOMMENDATION"))
                .andExpect(jsonPath("$.sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(fixture.sessionCourtIds().getFirst().toString()))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                accepted.getResponse().getContentAsString()
        );
        assertThat(response.get("createdAt").asText())
                .isEqualTo(OPERATION_TIME.toString());
        assertThat(response.get("startedAt").asText())
                .isEqualTo(OPERATION_TIME.toString());
        assertThat(matchRepository.findAll())
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.getStatus()).isEqualTo(MatchStatus.PLAYING);
                    assertThat(match.toDomain().source())
                            .isEqualTo(MatchSource.RECOMMENDATION);
                    assertThat(match.toDomain().startedAt())
                            .isEqualTo(OPERATION_TIME);
                });
        assertThat(matchParticipantRepository.count()).isEqualTo(4);
        assertThat(sessionCourt(fixture, 0).getStatus())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertThat(fixture.participantIds())
                .allSatisfy(participantId -> {
                    SessionParticipantEntity participant = participantRepository
                            .findById(participantId)
                            .orElseThrow();
                    assertThat(participant.getStatus())
                            .isEqualTo(ParticipantStatus.PLAYING);
                    assertThat(participant.getWaitingSince()).isNull();
                    assertThat(participant.toDomain().updatedAt())
                            .isEqualTo(OPERATION_TIME);
                });
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void staleAlgorithmAndCompositionReturnConflictWithoutWrites()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        JsonNode recommendation = generate(fixture, 0);
        Map<String, Object> staleAlgorithm = new java.util.LinkedHashMap<>(
                acceptBody(recommendation)
        );
        staleAlgorithm.put("algorithmVersion", "old-algorithm");

        accept(fixture, 0, staleAlgorithm)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Submitted recommendation is stale"));
        assertNoAcceptedWrites();

        List<Map<String, Object>> assignments = new ArrayList<>(
                assignments(recommendation)
        );
        Map<String, Object> first = new java.util.LinkedHashMap<>(
                assignments.getFirst()
        );
        first.put("sessionParticipantId", UUID.randomUUID());
        assignments.set(0, first);
        accept(fixture, 0, Map.of(
                "algorithmVersion", MatchmakingEngine.ALGORITHM_VERSION,
                "assignments", assignments
        )).andExpect(status().isConflict());
        assertNoAcceptedWrites();
    }

    @Test
    void unavailableRegenerationReturnsConflictWithoutWrites()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 3);
        List<Map<String, Object>> submitted = List.of(
                assignment(fixture.participantIds().get(0), "A", 1),
                assignment(fixture.participantIds().get(1), "A", 2),
                assignment(fixture.participantIds().get(2), "B", 1),
                assignment(UUID.randomUUID(), "B", 2)
        );

        accept(fixture, 0, Map.of(
                "algorithmVersion", MatchmakingEngine.ALGORITHM_VERSION,
                "assignments", submitted
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Submitted recommendation is stale"));
        assertNoAcceptedWrites();
    }

    @Test
    void newOldestWaitingPlayerMakesDisplayedCompositionStale()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        JsonNode displayed = generate(fixture, 0);
        UUID playerId = createPlayerWithProfile(SkillLevel.GOOD);
        createParticipant(
                fixture.sessionId(),
                playerId,
                BASE_TIME.plusSeconds(2),
                BASE_TIME.plusSeconds(3)
        );

        accept(fixture, 0, acceptBody(displayed))
                .andExpect(status().isConflict());
        assertNoAcceptedWrites();
    }

    @Test
    void changedRatingsMayAcceptWhenStableCompositionRemainsTheSame()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        JsonNode displayed = generate(fixture, 0);
        fixture.playerIds().forEach(this::createEqualPersistedRating);

        accept(fixture, 0, acceptBody(displayed))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLAYING"));

        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(playerRatingRepository.count()).isEqualTo(4);
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void generateAfterAcceptUsesCurrentPlayingCourtConflict()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        JsonNode recommendation = generate(fixture, 0);
        accept(fixture, 0, acceptBody(recommendation))
                .andExpect(status().isCreated());

        mockMvc.perform(post(
                        GENERATE_ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtIds().getFirst()
                ))
                .andExpect(status().isConflict());
    }

    @Test
    void staleSessionCourtAndParticipantFailWithoutMatchWrites()
            throws Exception {
        RuntimeFixture sessionFixture = createFixture(1, 4);
        completeSession(sessionFixture.sessionId());
        assertDirectConflict(command(
                sessionFixture,
                0,
                sessionFixture.participantIds()
        ));

        cleanDatabase();
        RuntimeFixture courtFixture = createFixture(1, 4);
        SessionCourtEntity court = sessionCourt(courtFixture, 0);
        court.applyRuntimeState(court.toDomain().disable(OPERATION_TIME));
        sessionCourtRepository.saveAndFlush(court);
        assertDirectConflict(command(
                courtFixture,
                0,
                courtFixture.participantIds()
        ));

        cleanDatabase();
        RuntimeFixture participantFixture = createFixture(1, 4);
        SessionParticipantEntity participant = participantRepository.findById(
                participantFixture.participantIds().getFirst()
        ).orElseThrow();
        participant.applyRuntimeState(
                participant.toDomain().pause(OPERATION_TIME)
        );
        participantRepository.saveAndFlush(participant);
        assertDirectConflict(command(
                participantFixture,
                0,
                participantFixture.participantIds()
        ));
    }

    @Test
    void concurrentIdenticalAcceptsHaveOneWinnerAndNoOrphanMatch()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        Map<String, Object> body = acceptBody(generate(fixture, 0));

        List<Integer> statuses = runConcurrently(
                () -> acceptStatus(fixture, 0, body),
                () -> acceptStatus(fixture, 0, body)
        );

        assertOneCreatedAndOneConflict(statuses);
        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(matchRepository.findAll().getFirst().getStatus())
                .isEqualTo(MatchStatus.PLAYING);
        assertThat(matchParticipantRepository.count()).isEqualTo(4);
        assertThat(sessionCourt(fixture, 0).getStatus())
                .isEqualTo(SessionCourtStatus.PLAYING);
    }

    @Test
    void samePlayersDifferentCourtsHaveOneAcceptedWinner()
            throws Exception {
        RuntimeFixture fixture = createFixture(2, 4);
        Map<String, Object> first = acceptBody(generate(fixture, 0));
        Map<String, Object> second = acceptBody(generate(fixture, 1));

        List<Integer> statuses = runConcurrently(
                () -> acceptStatus(fixture, 0, first),
                () -> acceptStatus(fixture, 1, second)
        );

        assertOneCreatedAndOneConflict(statuses);
        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(matchParticipantRepository.count()).isEqualTo(4);
        assertThat(fixture.sessionCourtIds().stream()
                .map(id -> sessionCourtRepository.findById(id)
                        .orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(
                        SessionCourtStatus.PLAYING,
                        SessionCourtStatus.AVAILABLE
                );
    }

    @Test
    void sameCourtDifferentPlayersHaveOneMatchAndLosingPlayersWaiting()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 8);
        List<UUID> firstPlayers = fixture.participantIds().subList(0, 4);
        List<UUID> secondPlayers = fixture.participantIds().subList(4, 8);

        List<Integer> results = runConcurrently(
                () -> directStartStatus(command(fixture, 0, firstPlayers)),
                () -> directStartStatus(command(fixture, 0, secondPlayers))
        );

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(matchParticipantRepository.count()).isEqualTo(4);
        List<UUID> persistedParticipantIds = matchParticipantRepository.findAll()
                .stream()
                .map(entity -> entity.getSessionParticipantId())
                .toList();
        fixture.participantIds().forEach(participantId -> {
            ParticipantStatus expected = persistedParticipantIds.contains(
                    participantId
            ) ? ParticipantStatus.PLAYING : ParticipantStatus.WAITING;
            assertThat(participantRepository.findById(participantId)
                    .orElseThrow().getStatus()).isEqualTo(expected);
        });
    }

    @Test
    void partialPlayerOverlapAcrossCourtsHasOneWinnerWithoutDeadlock()
            throws Exception {
        RuntimeFixture fixture = createFixture(2, 6);
        List<UUID> firstPlayers = fixture.participantIds().subList(0, 4);
        List<UUID> secondPlayers = List.of(
                fixture.participantIds().get(2),
                fixture.participantIds().get(3),
                fixture.participantIds().get(4),
                fixture.participantIds().get(5)
        );

        List<Integer> results = runConcurrently(
                () -> directStartStatus(command(fixture, 0, firstPlayers)),
                () -> directStartStatus(command(fixture, 1, secondPlayers))
        );

        assertThat(results).containsExactlyInAnyOrder(1, 0);
        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(matchParticipantRepository.count()).isEqualTo(4);
        assertThat(fixture.participantIds().get(2))
                .satisfies(participantId -> assertThat(
                        participantRepository.findById(participantId)
                                .orElseThrow().getStatus()
                ).isEqualTo(ParticipantStatus.PLAYING));
    }

    @Test
    void acceptVersusSessionCompleteNeverLeavesCompletedSessionPlayingMatch()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        Map<String, Object> body = acceptBody(generate(fixture, 0));

        List<Integer> statuses = runConcurrently(
                () -> acceptStatus(fixture, 0, body),
                () -> completeSessionStatus(fixture.sessionId())
        );

        assertThat(statuses).isIn(
                List.of(201, 409),
                List.of(409, 200)
        );
        SessionStatus sessionStatus = sessionRepository.findById(
                fixture.sessionId()
        ).orElseThrow().getStatus();
        boolean playingMatch = matchRepository.existsBySessionIdAndStatus(
                fixture.sessionId(),
                MatchStatus.PLAYING
        );
        assertThat(sessionStatus == SessionStatus.COMPLETED && playingMatch)
                .isFalse();
    }

    @Test
    void acceptThenSessionCancelPreservesExistingCancellationPolicy()
            throws Exception {
        RuntimeFixture fixture = createFixture(1, 4);
        Map<String, Object> body = acceptBody(generate(fixture, 0));

        assertThat(acceptStatus(fixture, 0, body)).isEqualTo(201);
        assertThat(cancelSessionStatus(fixture.sessionId())).isEqualTo(200);

        assertThat(sessionRepository.findById(fixture.sessionId())
                .orElseThrow().getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(matchRepository.existsBySessionIdAndStatus(
                fixture.sessionId(),
                MatchStatus.PLAYING
        )).isTrue();
    }

    private RuntimeFixture createFixture(int courtCount, int playerCount) {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId);
        List<UUID> sessionCourtIds = new ArrayList<>();
        for (int index = 0; index < courtCount; index++) {
            UUID courtId = createCourt(venueId);
            sessionCourtIds.add(createSessionCourt(sessionId, courtId));
        }
        List<UUID> playerIds = new ArrayList<>();
        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            UUID playerId = createPlayerWithProfile(SkillLevel.GOOD);
            playerIds.add(playerId);
            participantIds.add(createParticipant(
                    sessionId,
                    playerId,
                    BASE_TIME.plusSeconds(10L + index),
                    BASE_TIME.plusSeconds(20L + index)
            ));
        }
        return new RuntimeFixture(
                sessionId,
                List.copyOf(sessionCourtIds),
                List.copyOf(playerIds),
                List.copyOf(participantIds)
        );
    }

    private UUID createVenue() {
        Venue venue = Venue.create(
                "Step 5 Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId) {
        Court court = Court.create(
                venueId,
                "Step 5 Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createSession(UUID venueId) {
        Session session = Session.create(
                venueId,
                "Step 5 Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plus(1, ChronoUnit.HOURS),
                BASE_TIME.plus(3, ChronoUnit.HOURS),
                BASE_TIME
        ).start(BASE_TIME.plusSeconds(1));
        return sessionRepository.saveAndFlush(SessionEntity.from(session)).getId();
    }

    private UUID createSessionCourt(UUID sessionId, UUID courtId) {
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                BASE_TIME.plusSeconds(2)
        );
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private UUID createPlayerWithProfile(SkillLevel skillLevel) {
        Player player = Player.create(
                "Step 5 Player " + UUID.randomUUID(),
                BASE_TIME
        );
        UUID playerId = playerRepository.saveAndFlush(
                PlayerEntity.from(player)
        ).getId();
        PlayerSportProfile profile = PlayerSportProfile.create(
                playerId,
                SportCode.BADMINTON,
                skillLevel,
                BASE_TIME
        );
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(profile));
        return playerId;
    }

    private UUID createParticipant(
            UUID sessionId,
            UUID playerId,
            Instant joinedAt,
            Instant waitingSince
    ) {
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                joinedAt
        ).checkIn(waitingSince);
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private JsonNode generate(RuntimeFixture fixture, int courtIndex)
            throws Exception {
        MvcResult result = mockMvc.perform(post(
                        GENERATE_ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtIds().get(courtIndex)
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions accept(
            RuntimeFixture fixture,
            int courtIndex,
            Object body
    ) throws Exception {
        return mockMvc.perform(post(
                        ACCEPT_ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtIds().get(courtIndex)
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> acceptBody(JsonNode recommendation) {
        return Map.of(
                "algorithmVersion",
                recommendation.get("algorithmVersion").asText(),
                "assignments",
                assignments(recommendation)
        );
    }

    private List<Map<String, Object>> assignments(JsonNode recommendation) {
        return List.of(
                assignment(recommendation.at("/teamA/slot1")),
                assignment(recommendation.at("/teamA/slot2")),
                assignment(recommendation.at("/teamB/slot1")),
                assignment(recommendation.at("/teamB/slot2"))
        );
    }

    private Map<String, Object> assignment(JsonNode player) {
        return assignment(
                UUID.fromString(player.get("sessionParticipantId").asText()),
                player.get("teamSide").asText(),
                player.get("teamSlot").asInt()
        );
    }

    private static Map<String, Object> assignment(
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

    private CreateAndStartRecommendedMatchCommand command(
            RuntimeFixture fixture,
            int courtIndex,
            List<UUID> participantIds
    ) {
        return new CreateAndStartRecommendedMatchCommand(
                fixture.sessionId(),
                fixture.sessionCourtIds().get(courtIndex),
                List.of(
                        recommended(participantIds.get(0), TeamSide.A, 1),
                        recommended(participantIds.get(1), TeamSide.A, 2),
                        recommended(participantIds.get(2), TeamSide.B, 1),
                        recommended(participantIds.get(3), TeamSide.B, 2)
                )
        );
    }

    private static RecommendedMatchParticipantAssignment recommended(
            UUID participantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return new RecommendedMatchParticipantAssignment(
                participantId,
                teamSide,
                teamSlot
        );
    }

    private void createEqualPersistedRating(UUID playerId) {
        playerRatingRepository.saveAndFlush(PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.GOOD,
                new RatingState(35.0, 5.0),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                BASE_TIME
        ));
    }

    private SessionCourtEntity sessionCourt(
            RuntimeFixture fixture,
            int courtIndex
    ) {
        return sessionCourtRepository.findById(
                fixture.sessionCourtIds().get(courtIndex)
        ).orElseThrow();
    }

    private void completeSession(UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow();
        session.applyRuntimeState(session.toDomain().complete(OPERATION_TIME));
        sessionRepository.saveAndFlush(session);
    }

    private int completeSessionStatus(UUID sessionId) throws Exception {
        return mockMvc.perform(post(
                        "/api/sessions/{sessionId}/complete",
                        sessionId
                ))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int cancelSessionStatus(UUID sessionId) throws Exception {
        return mockMvc.perform(post(
                        "/api/sessions/{sessionId}/cancel",
                        sessionId
                ))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int acceptStatus(
            RuntimeFixture fixture,
            int courtIndex,
            Object body
    ) throws Exception {
        return accept(fixture, courtIndex, body)
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int directStartStatus(
            CreateAndStartRecommendedMatchCommand command
    ) {
        try {
            matchService.createAndStartRecommendedMatch(command);
            return 1;
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    private void assertDirectConflict(
            CreateAndStartRecommendedMatchCommand command
    ) {
        assertThat(directStartStatus(command)).isZero();
        assertNoAcceptedWrites();
    }

    private void assertNoAcceptedWrites() {
        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
    }

    private void assertOneCreatedAndOneConflict(List<Integer> statuses) {
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }

    private <T> List<T> runConcurrently(
            Callable<T> first,
            Callable<T> second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<T> firstFuture = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            Future<T> secondFuture = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private record RuntimeFixture(
            UUID sessionId,
            List<UUID> sessionCourtIds,
            List<UUID> playerIds,
            List<UUID> participantIds
    ) {
    }
}

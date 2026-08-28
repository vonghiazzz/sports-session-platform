package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MatchmakingApiIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String ENDPOINT =
            "/api/sessions/{sessionId}/courts/{sessionCourtId}"
                    + "/match-recommendations";
    private static final Instant BASE_TIME =
            Instant.parse("2026-08-28T09:00:00Z");
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    @TestBean(name = "clock", enforceOverride = true)
    private Clock clock;

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
        return Clock.fixed(EVALUATION_TIME, ZoneOffset.UTC);
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
    void realHttpPipelineReturnsMixedBasisRecommendationWithoutWrites()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.IN_PROGRESS,
                SessionCourtStatus.AVAILABLE,
                4,
                true
        );
        UUID persistedPlayerId = fixture.waitingPlayerIds().getFirst();
        createPersistedRating(persistedPlayerId);
        PersistedState before = persistedState(fixture);

        MvcResult result = mockMvc.perform(post(
                        ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.algorithmVersion")
                        .value(MatchmakingEngine.ALGORITHM_VERSION))
                .andExpect(jsonPath("$.evaluationTime")
                        .value(EVALUATION_TIME.toString()))
                .andExpect(jsonPath("$.sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(fixture.sessionCourtId().toString()))
                .andExpect(jsonPath("$.sportCode").value("BADMINTON"))
                .andExpect(jsonPath("$.matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$.eligiblePlayerCount").value(4))
                .andExpect(jsonPath("$.teamA.slot1.teamSide").value("A"))
                .andExpect(jsonPath("$.teamA.slot1.teamSlot").value(1))
                .andExpect(jsonPath("$.teamA.slot2.teamSide").value("A"))
                .andExpect(jsonPath("$.teamA.slot2.teamSlot").value(2))
                .andExpect(jsonPath("$.teamB.slot1.teamSide").value("B"))
                .andExpect(jsonPath("$.teamB.slot1.teamSlot").value(1))
                .andExpect(jsonPath("$.teamB.slot2.teamSide").value("B"))
                .andExpect(jsonPath("$.teamB.slot2.teamSlot").value(2))
                .andReturn();

        JsonNode json = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        List<JsonNode> players = recommendedPlayers(json);
        assertThat(players).hasSize(4);
        assertThat(players.stream()
                .map(player -> UUID.fromString(player.get("sessionParticipantId")
                        .asText())))
                .containsExactlyInAnyOrderElementsOf(
                        fixture.waitingParticipantIds()
                )
                .doesNotHaveDuplicates()
                .doesNotContain(fixture.nonWaitingParticipantId());
        assertThat(players.stream()
                .map(player -> player.get("ratingBasis").asText()))
                .contains("PERSISTED", "INITIAL_PRIOR");
        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(playerRatingRepository.findAll())
                .singleElement()
                .satisfies(rating -> assertThat(rating.getPlayerId())
                        .isEqualTo(persistedPlayerId));
        assertNoOperationalWrites();
    }

    @Test
    void fewerThanFourWaitingPlayersReturnUnavailableWithoutWrites()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.IN_PROGRESS,
                SessionCourtStatus.AVAILABLE,
                3,
                false
        );
        PersistedState before = persistedState(fixture);

        mockMvc.perform(post(
                        ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.reason")
                        .value("INSUFFICIENT_ELIGIBLE_PLAYERS"))
                .andExpect(jsonPath("$.eligiblePlayerCount").value(3))
                .andExpect(jsonPath("$.teamA").doesNotExist())
                .andExpect(jsonPath("$.teamB").doesNotExist());

        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(playerRatingRepository.count()).isZero();
        assertNoOperationalWrites();
    }

    @Test
    void plannedSessionReturnsConflictWithoutWrites() throws Exception {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.PLANNED,
                SessionCourtStatus.AVAILABLE,
                4,
                false
        );
        PersistedState before = persistedState(fixture);

        mockMvc.perform(post(
                        ENDPOINT,
                        fixture.sessionId(),
                        fixture.sessionCourtId()
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Matchmaking requires an IN_PROGRESS Session: "
                                + fixture.sessionId()
                ));

        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(playerRatingRepository.count()).isZero();
        assertNoOperationalWrites();
    }

    @Test
    void unknownSessionReturnsNotFoundWithoutWrites() throws Exception {
        UUID missingSessionId = UUID.randomUUID();
        UUID requestedSessionCourtId = UUID.randomUUID();

        mockMvc.perform(post(
                        ENDPOINT,
                        missingSessionId,
                        requestedSessionCourtId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(
                        "Matchmaking Session not found: " + missingSessionId
                ));

        assertThat(sessionRepository.count()).isZero();
        assertThat(sessionCourtRepository.count()).isZero();
        assertThat(participantRepository.count()).isZero();
        assertThat(playerRatingRepository.count()).isZero();
        assertNoOperationalWrites();
    }

    private RuntimeFixture createRuntimeFixture(
            SessionStatus sessionStatus,
            SessionCourtStatus courtStatus,
            int waitingPlayerCount,
            boolean includeNonWaitingParticipant
    ) {
        UUID venueId = createVenue();
        UUID courtId = createCourt(venueId);
        UUID sessionId = createSession(venueId, sessionStatus);
        UUID sessionCourtId = createSessionCourt(
                sessionId,
                courtId,
                courtStatus
        );
        List<UUID> waitingPlayerIds = new ArrayList<>();
        List<UUID> waitingParticipantIds = new ArrayList<>();
        for (int index = 1; index <= waitingPlayerCount; index++) {
            UUID playerId = createPlayerWithProfile(index, SkillLevel.GOOD);
            waitingPlayerIds.add(playerId);
            waitingParticipantIds.add(createParticipant(
                    sessionId,
                    playerId,
                    ParticipantStatus.WAITING,
                    index
            ));
        }
        UUID nonWaitingParticipantId = null;
        if (includeNonWaitingParticipant) {
            int identity = waitingPlayerCount + 1;
            UUID playerId = createPlayerWithProfile(identity, SkillLevel.WEAK);
            nonWaitingParticipantId = createParticipant(
                    sessionId,
                    playerId,
                    ParticipantStatus.REGISTERED,
                    identity
            );
        }
        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                courtId,
                List.copyOf(waitingPlayerIds),
                List.copyOf(waitingParticipantIds),
                nonWaitingParticipantId
        );
    }

    private UUID createVenue() {
        Venue venue = Venue.create(
                "Step 4 Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId) {
        Court court = Court.create(
                venueId,
                "Step 4 Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createSession(UUID venueId, SessionStatus status) {
        Session session = Session.create(
                venueId,
                "Step 4 Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plus(1, ChronoUnit.HOURS),
                BASE_TIME.plus(3, ChronoUnit.HOURS),
                BASE_TIME
        );
        session = switch (status) {
            case PLANNED -> session;
            case IN_PROGRESS -> session.start(BASE_TIME.plusSeconds(1));
            case COMPLETED -> session.start(BASE_TIME.plusSeconds(1))
                    .complete(BASE_TIME.plusSeconds(2));
            case CANCELLED -> session.cancel(BASE_TIME.plusSeconds(1));
        };
        return sessionRepository.saveAndFlush(SessionEntity.from(session)).getId();
    }

    private UUID createSessionCourt(
            UUID sessionId,
            UUID courtId,
            SessionCourtStatus status
    ) {
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                BASE_TIME.plusSeconds(3)
        );
        sessionCourt = switch (status) {
            case AVAILABLE -> sessionCourt;
            case PLAYING -> sessionCourt.startMatch(BASE_TIME.plusSeconds(4));
            case UNAVAILABLE -> sessionCourt.disable(BASE_TIME.plusSeconds(4));
        };
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private UUID createPlayerWithProfile(int number, SkillLevel skillLevel) {
        UUID playerId = uuid(number);
        Player player = new Player(
                playerId,
                "Step 4 Player " + number,
                BASE_TIME,
                BASE_TIME
        );
        playerRepository.saveAndFlush(PlayerEntity.from(player));
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
            ParticipantStatus status,
            int order
    ) {
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                BASE_TIME.plusSeconds(10L + order)
        );
        participant = switch (status) {
            case REGISTERED -> participant;
            case WAITING -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            );
            case PLAYING -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            ).startMatch(BASE_TIME.plusSeconds(30L + order));
            case PAUSED -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            ).pause(BASE_TIME.plusSeconds(30L + order));
            case LEFT -> participant.leave(BASE_TIME.plusSeconds(20L + order));
        };
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private void createPersistedRating(UUID playerId) {
        playerRatingRepository.saveAndFlush(PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.INTERMEDIATE_PLUS,
                new RatingState(31.123456789, 6.987654321),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                BASE_TIME
        ));
    }

    private PersistedState persistedState(RuntimeFixture fixture) {
        SessionEntity session = sessionRepository.findById(fixture.sessionId())
                .orElseThrow();
        SessionCourtEntity sessionCourt = sessionCourtRepository.findById(
                fixture.sessionCourtId()
        ).orElseThrow();
        CourtEntity court = courtRepository.findById(fixture.courtId())
                .orElseThrow();
        List<ParticipantState> participants = participantRepository
                .findAllBySessionIdOrderByPlayerIdAscIdAsc(fixture.sessionId())
                .stream()
                .map(entity -> new ParticipantState(
                        entity.getId(),
                        entity.getStatus(),
                        entity.getWaitingSince(),
                        entity.toDomain().updatedAt(),
                        entity.getVersion()
                ))
                .toList();
        List<RatingStateSnapshot> ratings = playerRatingRepository.findAll()
                .stream()
                .map(entity -> new RatingStateSnapshot(
                        entity.getId(),
                        entity.getPlayerId(),
                        entity.getRatingValue(),
                        entity.getUncertainty(),
                        entity.getRatedMatches(),
                        entity.getUpdatedAt(),
                        entity.getVersion()
                ))
                .toList();
        return new PersistedState(
                session.getStatus(),
                session.toDomain().updatedAt(),
                session.getVersion(),
                sessionCourt.getStatus(),
                sessionCourt.toDomain().updatedAt(),
                sessionCourt.getVersion(),
                court.getUpdatedAt(),
                participants,
                ratings,
                matchRepository.count(),
                matchParticipantRepository.count(),
                ratingEventRepository.count()
        );
    }

    private List<JsonNode> recommendedPlayers(JsonNode response) {
        return List.of(
                response.at("/teamA/slot1"),
                response.at("/teamA/slot2"),
                response.at("/teamB/slot1"),
                response.at("/teamB/slot2")
        );
    }

    private void assertNoOperationalWrites() {
        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            UUID courtId,
            List<UUID> waitingPlayerIds,
            List<UUID> waitingParticipantIds,
            UUID nonWaitingParticipantId
    ) {
    }

    private record ParticipantState(
            UUID id,
            ParticipantStatus status,
            Instant waitingSince,
            Instant updatedAt,
            long version
    ) {
    }

    private record RatingStateSnapshot(
            UUID id,
            UUID playerId,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            Instant updatedAt,
            long version
    ) {
    }

    private record PersistedState(
            SessionStatus sessionStatus,
            Instant sessionUpdatedAt,
            long sessionVersion,
            SessionCourtStatus sessionCourtStatus,
            Instant sessionCourtUpdatedAt,
            long sessionCourtVersion,
            Instant courtUpdatedAt,
            List<ParticipantState> participants,
            List<RatingStateSnapshot> ratings,
            long matches,
            long matchParticipants,
            long ratingEvents
    ) {
    }
}

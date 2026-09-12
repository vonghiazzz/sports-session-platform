package com.sportssession.platform.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.application.RatingProcessingResult;
import com.sportssession.platform.rating.application.RatingProcessingService;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventEntity;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionParticipant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlayerRatingHistoryIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-09-01T01:00:00Z");
    private static final List<SkillLevel> SKILL_LEVELS = List.of(
            SkillLevel.INTERMEDIATE,
            SkillLevel.WEAK,
            SkillLevel.GOOD,
            SkillLevel.INTERMEDIATE_PLUS
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RatingProcessingService ratingProcessingService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void validUnratedPlayerReturnsEmptyHistoryWithoutCreatingRatingState()
            throws Exception {
        UUID playerId = createPlayer(true, SkillLevel.INTERMEDIATE);

        mockMvc.perform(historyRequest(playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(playerId.toString()))
                .andExpect(jsonPath("$.sport").value("BADMINTON"))
                .andExpect(jsonPath("$.matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$.events").isEmpty());

        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void ratedMatchReturnsPersistedEventFactsAndReadDoesNotMutateState()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        Instant completedAt = BASE_TIME.plus(10, ChronoUnit.MINUTES);
        UUID matchId = createCompletedMatch(fixture, completedAt, TeamSide.A);
        assertThat(ratingProcessingService.processRating(matchId))
                .isEqualTo(RatingProcessingResult.APPLIED);

        UUID playerId = fixture.playerIds().getFirst();
        PlayerRatingEntity rating = ratingFor(playerId);
        RatingEventEntity event = eventFor(matchId, rating.getId());
        RatingSnapshot ratingBefore = RatingSnapshot.from(rating);
        long eventCountBefore = ratingEventRepository.count();

        MvcResult result = mockMvc.perform(historyRequest(playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].matchId")
                        .value(matchId.toString()))
                .andExpect(jsonPath("$.events[0].matchCompletedAt")
                        .value(completedAt.toString()))
                .andExpect(jsonPath("$.events[0].outcome").value("WIN"))
                .andExpect(jsonPath("$.events[0].resultVersion").value(1))
                .andExpect(jsonPath("$.events[0].algorithmVersion")
                        .value(event.getAlgorithmVersion()))
                .andExpect(jsonPath("$.events[0].createdAt")
                        .value(event.getCreatedAt().toString()))
                .andReturn();

        JsonNode responseEvent = objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).path("events").get(0);
        assertDecimal(responseEvent, "beforeRatingValue", event.getBeforeRating());
        assertDecimal(responseEvent, "beforeUncertainty", event.getBeforeUncertainty());
        assertDecimal(responseEvent, "afterRatingValue", event.getAfterRating());
        assertDecimal(responseEvent, "afterUncertainty", event.getAfterUncertainty());
        assertThat(RatingSnapshot.from(ratingFor(playerId)))
                .isEqualTo(ratingBefore);
        assertThat(ratingEventRepository.count()).isEqualTo(eventCountBefore);
    }

    @Test
    void historyOrdersByMatchCompletedAtBeforeMatchIdAndEventTimestamp()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        Instant initialCompletedAt = BASE_TIME.plus(20, ChronoUnit.MINUTES);
        UUID firstCreated = createCompletedMatch(
                fixture,
                initialCompletedAt,
                TeamSide.A
        );
        UUID secondCreated = createCompletedMatch(
                fixture,
                initialCompletedAt,
                TeamSide.B
        );
        List<UUID> ascending = List.of(firstCreated, secondCreated).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        UUID smallerMatchId = ascending.get(0);
        UUID largerMatchId = ascending.get(1);
        assertThat(ratingProcessingService.processRating(smallerMatchId))
                .isEqualTo(RatingProcessingResult.APPLIED);
        assertThat(ratingProcessingService.processRating(largerMatchId))
                .isEqualTo(RatingProcessingResult.APPLIED);

        Instant olderCompletedAt = initialCompletedAt.plus(
                1,
                ChronoUnit.MINUTES
        );
        Instant newerCompletedAt = initialCompletedAt.plus(
                2,
                ChronoUnit.MINUTES
        );
        jdbcTemplate.update(
                "UPDATE matches SET completed_at = ? WHERE id = ?",
                Timestamp.from(olderCompletedAt),
                largerMatchId
        );
        jdbcTemplate.update(
                "UPDATE matches SET completed_at = ? WHERE id = ?",
                Timestamp.from(newerCompletedAt),
                smallerMatchId
        );
        jdbcTemplate.update(
                "UPDATE rating_events SET created_at = ? WHERE match_id = ?",
                Timestamp.from(BASE_TIME.plus(2, ChronoUnit.DAYS)),
                largerMatchId
        );
        jdbcTemplate.update(
                "UPDATE rating_events SET created_at = ? WHERE match_id = ?",
                Timestamp.from(BASE_TIME.plus(1, ChronoUnit.DAYS)),
                smallerMatchId
        );

        JsonNode events = historyJson(fixture.playerIds().getFirst())
                .path("events");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).path("matchId").asText())
                .isEqualTo(smallerMatchId.toString());
        assertThat(events.get(0).path("matchCompletedAt").asText())
                .isEqualTo(newerCompletedAt.toString());
        assertThat(events.get(1).path("matchId").asText())
                .isEqualTo(largerMatchId.toString());
        assertThat(events.get(1).path("matchCompletedAt").asText())
                .isEqualTo(olderCompletedAt.toString());
        assertThat(events.get(0).path("createdAt").asText())
                .isLessThan(events.get(1).path("createdAt").asText());
    }

    @Test
    void historyUsesMatchIdDescendingAsTieBreakNotEventTimestamp()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        Instant completedAt = BASE_TIME.plus(20, ChronoUnit.MINUTES);
        UUID firstCreated = createCompletedMatch(
                fixture,
                completedAt,
                TeamSide.A
        );
        UUID secondCreated = createCompletedMatch(
                fixture,
                completedAt,
                TeamSide.B
        );
        List<UUID> ascending = List.of(firstCreated, secondCreated).stream()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
        ratingProcessingService.processRating(ascending.get(0));
        ratingProcessingService.processRating(ascending.get(1));

        jdbcTemplate.update(
                "UPDATE rating_events SET created_at = ? WHERE match_id = ?",
                Timestamp.from(BASE_TIME.plus(2, ChronoUnit.DAYS)),
                ascending.get(0)
        );
        jdbcTemplate.update(
                "UPDATE rating_events SET created_at = ? WHERE match_id = ?",
                Timestamp.from(BASE_TIME.plus(1, ChronoUnit.DAYS)),
                ascending.get(1)
        );

        JsonNode events = historyJson(fixture.playerIds().getFirst())
                .path("events");
        assertThat(events).hasSize(2);
        assertThat(events.get(0).path("matchId").asText())
                .isEqualTo(ascending.get(1).toString());
        assertThat(events.get(1).path("matchId").asText())
                .isEqualTo(ascending.get(0).toString());
        assertThat(events.get(0).path("createdAt").asText())
                .isLessThan(events.get(1).path("createdAt").asText());
    }

    @Test
    void historyIsIsolatedToTheRequestedPlayerWithinTheRatingContext()
            throws Exception {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createCompletedMatch(
                fixture,
                BASE_TIME.plus(30, ChronoUnit.MINUTES),
                TeamSide.A
        );
        ratingProcessingService.processRating(matchId);
        assertThat(ratingEventRepository.count()).isEqualTo(4);

        JsonNode winningPlayerEvents = historyJson(
                fixture.playerIds().getFirst()
        ).path("events");
        JsonNode losingPlayerEvents = historyJson(
                fixture.playerIds().get(2)
        ).path("events");

        assertThat(winningPlayerEvents).hasSize(1);
        assertThat(winningPlayerEvents.get(0).path("outcome").asText())
                .isEqualTo("WIN");
        assertThat(losingPlayerEvents).hasSize(1);
        assertThat(losingPlayerEvents.get(0).path("outcome").asText())
                .isEqualTo("LOSS");
    }

    @Test
    void unknownPlayerReturnsNotFound() throws Exception {
        UUID playerId = UUID.randomUUID();

        mockMvc.perform(historyRequest(playerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Player not found: " + playerId));
    }

    @Test
    void missingRequestedSportProfileReturnsNotFound() throws Exception {
        UUID playerId = createPlayer(false, SkillLevel.INTERMEDIATE);

        mockMvc.perform(historyRequest(playerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Player sport profile not found: "
                                + playerId
                                + " / BADMINTON"
                ));
    }

    @Test
    void invalidSportOrMatchFormatPathValueReturnsBadRequest()
            throws Exception {
        UUID playerId = createPlayer(true, SkillLevel.INTERMEDIATE);

        mockMvc.perform(get(
                        "/api/players/{playerId}/sports/TENNIS/ratings/DOUBLES/history",
                        playerId
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for sportCode"));
        mockMvc.perform(get(
                        "/api/players/{playerId}/sports/BADMINTON/ratings/SINGLES/history",
                        playerId
                ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for matchFormat"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
    historyRequest(UUID playerId) {
        return get(
                "/api/players/{playerId}/sports/BADMINTON/ratings/DOUBLES/history",
                playerId
        );
    }

    private JsonNode historyJson(UUID playerId) throws Exception {
        String response = mockMvc.perform(historyRequest(playerId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private UUID createPlayer(boolean withProfile, SkillLevel skillLevel) {
        Player player = Player.create(
                "Player " + UUID.randomUUID(),
                BASE_TIME
        );
        UUID playerId = playerRepository
                .saveAndFlush(PlayerEntity.from(player))
                .getId();
        if (withProfile) {
            profileRepository.saveAndFlush(PlayerSportProfileEntity.from(
                    PlayerSportProfile.create(
                            playerId,
                            SportCode.BADMINTON,
                            skillLevel,
                            BASE_TIME
                    )
            ));
        }
        return playerId;
    }

    private RuntimeFixture createRuntimeFixture() {
        Venue venue = Venue.create(
                "Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();
        Court court = Court.create(
                venueId,
                "Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        UUID courtId = courtRepository
                .saveAndFlush(CourtEntity.from(court))
                .getId();
        Session session = Session.create(
                venueId,
                "Rating History Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plus(1, ChronoUnit.HOURS),
                BASE_TIME.plus(3, ChronoUnit.HOURS),
                BASE_TIME
        ).start(BASE_TIME.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();
        UUID sessionCourtId = sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(SessionCourt.allocate(
                        sessionId,
                        courtId,
                        BASE_TIME.plusSeconds(2)
                ))
        ).getId();

        List<UUID> playerIds = new ArrayList<>();
        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < SKILL_LEVELS.size(); index++) {
            UUID playerId = createPlayer(true, SKILL_LEVELS.get(index));
            playerIds.add(playerId);
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    BASE_TIME.plusSeconds(3)
            ).checkIn(BASE_TIME.plusSeconds(4));
            participantIds.add(participantRepository.saveAndFlush(
                    SessionParticipantEntity.from(participant)
            ).getId());
        }
        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                List.copyOf(playerIds),
                List.copyOf(participantIds)
        );
    }

    private UUID createCompletedMatch(
            RuntimeFixture fixture,
            Instant completedAt,
            TeamSide winnerTeam
    ) {
        Match match = Match.create(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                MatchSource.MANUAL,
                completedAt.minusSeconds(2)
        ).start(completedAt.minusSeconds(1)).complete(
                MatchResult.winnerOnly(winnerTeam),
                completedAt
        );
        matchRepository.saveAndFlush(MatchEntity.from(match));
        matchParticipantRepository.saveAllAndFlush(List.of(
                assignment(match.id(), fixture.participantIds().get(0), TeamSide.A, 1),
                assignment(match.id(), fixture.participantIds().get(1), TeamSide.A, 2),
                assignment(match.id(), fixture.participantIds().get(2), TeamSide.B, 1),
                assignment(match.id(), fixture.participantIds().get(3), TeamSide.B, 2)
        ));
        return match.id();
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

    private PlayerRatingEntity ratingFor(UUID playerId) {
        return playerRatingRepository.findContextRatings(
                List.of(playerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        ).getFirst();
    }

    private RatingEventEntity eventFor(UUID matchId, UUID ratingId) {
        return ratingEventRepository
                .findAllByMatchIdAndResultVersionOrderByPlayerRatingId(
                        matchId,
                        1
                )
                .stream()
                .filter(event -> event.getPlayerRatingId().equals(ratingId))
                .findFirst()
                .orElseThrow();
    }

    private void assertDecimal(
            JsonNode node,
            String field,
            BigDecimal expected
    ) {
        assertThat(node.path(field).decimalValue())
                .isEqualByComparingTo(expected);
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> playerIds,
            List<UUID> participantIds
    ) {
    }

    private record RatingSnapshot(
            UUID id,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            SkillLevel initialSkillLevel,
            String algorithmVersion,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        static RatingSnapshot from(PlayerRatingEntity entity) {
            return new RatingSnapshot(
                    entity.getId(),
                    entity.getRatingValue(),
                    entity.getUncertainty(),
                    entity.getRatedMatches(),
                    entity.getInitialSkillLevel(),
                    entity.getAlgorithmVersion(),
                    entity.getVersion(),
                    entity.getCreatedAt(),
                    entity.getUpdatedAt()
            );
        }
    }
}

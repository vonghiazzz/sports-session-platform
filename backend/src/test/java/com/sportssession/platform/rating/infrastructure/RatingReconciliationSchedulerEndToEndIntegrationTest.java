package com.sportssession.platform.rating.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.application.MissingPlayerRatingPriorException;
import com.sportssession.platform.rating.application.RatingReconciliationService;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionCourtStatus;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RatingReconciliationSchedulerEndToEndIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final List<SkillLevel> GOLDEN_SKILLS = List.of(
            SkillLevel.GOOD,
            SkillLevel.INTERMEDIATE_PLUS,
            SkillLevel.WEAK_PLUS,
            SkillLevel.WEAK
    );

    @Autowired
    private RatingReconciliationService reconciliationService;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private RatingReconciliationScheduler scheduler;

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        sessionParticipantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
        scheduler = new RatingReconciliationScheduler(reconciliationService);
    }

    @Test
    void winnerOnlyPublicFlowRatesAfterOperationalCompletionAndIsIdempotent()
            throws Exception {
        ApiFixture fixture = createApiFixture(GOLDEN_SKILLS);
        UUID matchId = completePublicMatch(fixture, "A", null, null);

        assertOperationalCompletion(fixture, matchId);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();

        scheduler.reconcileOnePendingMatch();

        assertGoldenFirstRatings(fixture);
        assertThat(events(matchId)).hasSize(4);
        assertOperationalCompletion(fixture, matchId);
        Map<UUID, RatingSnapshot> afterFirstTick = ratingSnapshots();

        scheduler.reconcileOnePendingMatch();

        assertThat(ratingSnapshots()).isEqualTo(afterFirstTick);
        assertThat(events(matchId)).hasSize(4);
    }

    @Test
    void scoredAndWinnerOnlyPublicMatchesProduceEquivalentRatings()
            throws Exception {
        ApiFixture winnerOnly = createApiFixture(GOLDEN_SKILLS);
        ApiFixture scored = createApiFixture(GOLDEN_SKILLS);
        UUID winnerOnlyMatch = completePublicMatch(
                winnerOnly, "A", null, null);
        UUID scoredMatch = completePublicMatch(scored, "A", 21, 17);

        scheduler.reconcileOnePendingMatch();
        scheduler.reconcileOnePendingMatch();

        Map<UUID, PlayerRatingEntity> ratings = ratingsByPlayer();
        for (int index = 0; index < 4; index++) {
            PlayerRatingEntity first = ratings.get(
                    winnerOnly.playerIds().get(index));
            PlayerRatingEntity second = ratings.get(
                    scored.playerIds().get(index));
            assertThat(second.getRatingValue())
                    .isEqualByComparingTo(first.getRatingValue());
            assertThat(second.getUncertainty())
                    .isEqualByComparingTo(first.getUncertainty());
        }
        assertThat(events(winnerOnlyMatch)).hasSize(4);
        assertThat(events(scoredMatch)).hasSize(4);
    }

    @Test
    void successiveTicksDrainSharedPlayerBacklogOneMatchAtATimeInDatabaseOrder()
            throws Exception {
        ApiFixture fixture = createApiFixture(GOLDEN_SKILLS);
        List<UUID> createdMatches = List.of(
                completePublicMatch(fixture, "A", null, null),
                completePublicMatch(fixture, "B", 18, 21),
                completePublicMatch(fixture, "A", null, null)
        );
        List<UUID> databaseOrder = jdbcTemplate.query(
                "SELECT id FROM matches ORDER BY completed_at ASC, id ASC",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );
        assertThat(databaseOrder)
                .containsExactlyInAnyOrderElementsOf(createdMatches);

        for (int tick = 0; tick < databaseOrder.size(); tick++) {
            scheduler.reconcileOnePendingMatch();
            for (int index = 0; index < databaseOrder.size(); index++) {
                if (index <= tick) {
                    assertThat(events(databaseOrder.get(index))).hasSize(4);
                } else {
                    assertThat(events(databaseOrder.get(index))).isEmpty();
                }
            }
        }

        assertThat(playerRatingRepository.findAll()).allSatisfy(rating -> {
            assertThat(rating.getRatedMatches()).isEqualTo(3);
            assertThat(rating.getInitialSkillLevel()).isEqualTo(
                    GOLDEN_SKILLS.get(fixture.playerIds().indexOf(
                            rating.getPlayerId()
                    ))
            );
        });
        for (int index = 1; index < databaseOrder.size(); index++) {
            Map<UUID, RatingEventEntity> previous = eventsByRating(
                    databaseOrder.get(index - 1));
            Map<UUID, RatingEventEntity> current = eventsByRating(
                    databaseOrder.get(index));
            previous.forEach((ratingId, event) -> {
                assertThat(current.get(ratingId).getBeforeRating())
                        .isEqualByComparingTo(event.getAfterRating());
                assertThat(current.get(ratingId).getBeforeUncertainty())
                        .isEqualByComparingTo(event.getAfterUncertainty());
            });
        }
    }

    @Test
    void busyTickDoesNothingAndFutureTickProcessesAfterDatabaseLockRelease()
            throws Exception {
        ApiFixture fixture = createApiFixture(GOLDEN_SKILLS);
        UUID matchId = completePublicMatch(fixture, "A", null, null);

        try (Connection owner = dataSource.getConnection()) {
            owner.setAutoCommit(false);
            assertThat(tryAcquireRatingLock(owner)).isTrue();

            scheduler.reconcileOnePendingMatch();

            assertThat(playerRatingRepository.count()).isZero();
            assertThat(ratingEventRepository.count()).isZero();
            owner.rollback();
        }

        scheduler.reconcileOnePendingMatch();

        assertThat(playerRatingRepository.count()).isEqualTo(4);
        assertThat(events(matchId)).hasSize(4);
    }

    @Test
    void poisonMatchFailureIsRetriedAndNeverSkipsLaterDurableWork()
            throws Exception {
        ApiFixture firstFixture = createApiFixture(GOLDEN_SKILLS);
        ApiFixture secondFixture = createApiFixture(GOLDEN_SKILLS);
        UUID firstMatch = completePublicMatch(
                firstFixture, "A", null, null);
        UUID secondMatch = completePublicMatch(
                secondFixture, "A", null, null);
        List<UUID> order = jdbcTemplate.query(
                "SELECT id FROM matches ORDER BY completed_at ASC, id ASC",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class)
        );
        UUID poisonMatch = order.getFirst();
        UUID laterMatch = order.getLast();
        ApiFixture poisonFixture = poisonMatch.equals(firstMatch)
                ? firstFixture
                : secondFixture;
        jdbcTemplate.update(
                "DELETE FROM player_sport_profiles WHERE player_id = ?",
                poisonFixture.playerIds().getLast()
        );

        assertThatThrownBy(scheduler::reconcileOnePendingMatch)
                .isInstanceOf(MissingPlayerRatingPriorException.class);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();

        assertThatThrownBy(scheduler::reconcileOnePendingMatch)
                .isInstanceOf(MissingPlayerRatingPriorException.class);
        assertThat(events(poisonMatch)).isEmpty();
        assertThat(events(laterMatch)).isEmpty();
    }

    private ApiFixture createApiFixture(List<SkillLevel> skillLevels)
            throws Exception {
        List<UUID> playerIds = new ArrayList<>();
        for (int index = 0; index < skillLevels.size(); index++) {
            playerIds.add(createdId(
                    "/api/players",
                    Map.of(
                            "displayName", "Rating E2E Player " + UUID.randomUUID(),
                            "sport", "BADMINTON",
                            "skillLevel", skillLevels.get(index).name()
                    )
            ));
        }
        UUID venueId = createdId(
                "/api/venues",
                Map.of(
                        "name", "Rating E2E Venue " + UUID.randomUUID(),
                        "active", true
                )
        );
        UUID courtId = createdId(
                "/api/venues/{venueId}/courts",
                Map.of(
                        "name", "Rating E2E Court " + UUID.randomUUID(),
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
                        "title", "Rating E2E Session " + UUID.randomUUID(),
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
        List<UUID> participantIds = new ArrayList<>();
        for (UUID playerId : playerIds) {
            participantIds.add(createdId(
                    "/api/sessions/{sessionId}/participants",
                    Map.of("playerId", playerId),
                    sessionId
            ));
        }
        mockMvc.perform(post("/api/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isOk());
        for (UUID participantId : participantIds) {
            mockMvc.perform(post(
                            "/api/sessions/{sessionId}/participants/"
                                    + "{participantId}/check-in",
                            sessionId,
                            participantId
                    ))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITING"));
        }
        return new ApiFixture(
                sessionId,
                sessionCourtId,
                playerIds,
                participantIds
        );
    }

    private UUID completePublicMatch(
            ApiFixture fixture,
            String winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) throws Exception {
        List<Map<String, Object>> assignments = List.of(
                assignment(fixture.participantIds().get(0), "A", 1),
                assignment(fixture.participantIds().get(1), "A", 2),
                assignment(fixture.participantIds().get(2), "B", 1),
                assignment(fixture.participantIds().get(3), "B", 2)
        );
        UUID matchId = createdId(
                "/api/sessions/{sessionId}/matches",
                Map.of(
                        "sessionCourtId", fixture.sessionCourtId(),
                        "participants", assignments
                ),
                fixture.sessionId()
        );
        mockMvc.perform(post("/api/matches/{matchId}/start", matchId))
                .andExpect(status().isOk());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("winnerTeam", winnerTeam);
        result.put("teamAScore", teamAScore);
        result.put("teamBScore", teamBScore);
        mockMvc.perform(post("/api/matches/{matchId}/complete", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        return matchId;
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

    private void assertOperationalCompletion(ApiFixture fixture, UUID matchId) {
        assertThat(matchRepository.findById(matchId).orElseThrow()
                .toDomain().status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow().getStatus()).isEqualTo(
                SessionCourtStatus.AVAILABLE
        );
        assertThat(fixture.participantIds()).allSatisfy(participantId ->
                assertThat(sessionParticipantRepository.findById(participantId)
                        .orElseThrow().getStatus()).isEqualTo(
                        ParticipantStatus.WAITING
                ));
    }

    private void assertGoldenFirstRatings(ApiFixture fixture) {
        Map<UUID, PlayerRatingEntity> ratings = ratingsByPlayer();
        assertThat(ratings).hasSize(4);
        assertRating(
                ratings.get(fixture.playerIds().get(0)),
                SkillLevel.GOOD,
                "35.552386939"
        );
        assertRating(
                ratings.get(fixture.playerIds().get(1)),
                SkillLevel.INTERMEDIATE_PLUS,
                "31.552386939"
        );
        assertRating(
                ratings.get(fixture.playerIds().get(2)),
                SkillLevel.WEAK_PLUS,
                "18.447613061"
        );
        assertRating(
                ratings.get(fixture.playerIds().get(3)),
                SkillLevel.WEAK,
                "14.447613061"
        );
    }

    private void assertRating(
            PlayerRatingEntity rating,
            SkillLevel skillLevel,
            String ratingValue
    ) {
        assertThat(rating.getInitialSkillLevel()).isEqualTo(skillLevel);
        assertThat(rating.getRatingValue()).isEqualByComparingTo(ratingValue);
        assertThat(rating.getUncertainty())
                .isEqualByComparingTo("8.258402475");
        assertThat(rating.getRatedMatches()).isEqualTo(1);
        assertThat(rating.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
    }

    private Map<UUID, PlayerRatingEntity> ratingsByPlayer() {
        Map<UUID, PlayerRatingEntity> ratings = new HashMap<>();
        playerRatingRepository.findAll().forEach(
                rating -> ratings.put(rating.getPlayerId(), rating)
        );
        return ratings;
    }

    private Map<UUID, RatingSnapshot> ratingSnapshots() {
        Map<UUID, RatingSnapshot> snapshots = new HashMap<>();
        playerRatingRepository.findAll().forEach(rating -> snapshots.put(
                rating.getPlayerId(),
                new RatingSnapshot(
                        rating.getRatingValue(),
                        rating.getUncertainty(),
                        rating.getRatedMatches(),
                        rating.getUpdatedAt()
                )
        ));
        return Map.copyOf(snapshots);
    }

    private List<RatingEventEntity> events(UUID matchId) {
        return ratingEventRepository
                .findAllByMatchIdAndResultVersionOrderByPlayerRatingId(
                        matchId,
                        1
                );
    }

    private Map<UUID, RatingEventEntity> eventsByRating(UUID matchId) {
        Map<UUID, RatingEventEntity> events = new HashMap<>();
        events(matchId).forEach(event -> events.put(
                event.getPlayerRatingId(),
                event
        ));
        return events;
    }

    private boolean tryAcquireRatingLock(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_try_advisory_xact_lock(?, ?)"
        )) {
            statement.setInt(
                    1,
                    PostgreSqlRatingContextLock.RATING_V1_LOCK_NAMESPACE
            );
            statement.setInt(
                    2,
                    PostgreSqlRatingContextLock.BADMINTON_DOUBLES_CONTEXT_KEY
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }

    private record ApiFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> playerIds,
            List<UUID> participantIds
    ) {
        private ApiFixture {
            playerIds = List.copyOf(playerIds);
            participantIds = List.copyOf(participantIds);
        }
    }

    private record RatingSnapshot(
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            Instant updatedAt
    ) {
    }
}

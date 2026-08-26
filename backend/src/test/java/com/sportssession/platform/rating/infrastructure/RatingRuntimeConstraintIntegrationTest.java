package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchEntity;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.rating.domain.RatingOutcome;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.infrastructure.SessionCourtEntity;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionEntity;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingRuntimeConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String ALGORITHM_VERSION = "weng-lin-pl-v1";

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void v4TablesExist() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('player_ratings', 'rating_events')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly("player_ratings", "rating_events");
    }

    @Test
    void playerRatingEntityMapsEveryColumn() {
        UUID playerId = createPlayer();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PlayerRatingEntity entity = new PlayerRatingEntity(
                UUID.randomUUID(),
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                new BigDecimal("-1.234567890"),
                new BigDecimal("8.333333333"),
                7,
                SkillLevel.INTERMEDIATE_PLUS,
                ALGORITHM_VERSION,
                0,
                now,
                now
        );

        UUID ratingId = playerRatingRepository.saveAndFlush(entity).getId();
        PlayerRatingEntity persisted = playerRatingRepository.findById(ratingId)
                .orElseThrow();

        assertThat(persisted.getPlayerId()).isEqualTo(playerId);
        assertThat(persisted.getSportCode()).isEqualTo(SportCode.BADMINTON);
        assertThat(persisted.getMatchFormat()).isEqualTo(MatchFormat.DOUBLES);
        assertThat(persisted.getRatingValue())
                .isEqualByComparingTo("-1.234567890");
        assertThat(persisted.getUncertainty())
                .isEqualByComparingTo("8.333333333");
        assertThat(persisted.getRatedMatches()).isEqualTo(7);
        assertThat(persisted.getInitialSkillLevel())
                .isEqualTo(SkillLevel.INTERMEDIATE_PLUS);
        assertThat(persisted.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
        assertThat(persisted.getVersion()).isZero();
        assertThat(persisted.getCreatedAt()).isEqualTo(now);
        assertThat(persisted.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void ratingEventEntityMapsEveryColumn() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        RatingEventEntity entity = new RatingEventEntity(
                UUID.randomUUID(),
                playerRatingId,
                matchId,
                1,
                RatingOutcome.WIN,
                new BigDecimal("27.000000000"),
                new BigDecimal("28.500000000"),
                new BigDecimal("8.333333333"),
                new BigDecimal("7.900000000"),
                ALGORITHM_VERSION,
                now
        );

        UUID eventId = ratingEventRepository.saveAndFlush(entity).getId();
        RatingEventEntity persisted = ratingEventRepository.findById(eventId)
                .orElseThrow();

        assertThat(persisted.getPlayerRatingId()).isEqualTo(playerRatingId);
        assertThat(persisted.getMatchId()).isEqualTo(matchId);
        assertThat(persisted.getResultVersion()).isEqualTo(1);
        assertThat(persisted.getOutcome()).isEqualTo(RatingOutcome.WIN);
        assertThat(persisted.getBeforeRating()).isEqualByComparingTo("27");
        assertThat(persisted.getAfterRating()).isEqualByComparingTo("28.5");
        assertThat(persisted.getBeforeUncertainty())
                .isEqualByComparingTo("8.333333333");
        assertThat(persisted.getAfterUncertainty()).isEqualByComparingTo("7.9");
        assertThat(persisted.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
        assertThat(persisted.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void duplicatePlayerRatingContextIsRejected() {
        UUID playerId = createPlayer();
        insertPlayerRating(playerId, "BADMINTON", "DOUBLES", "27", "8", 0,
                "INTERMEDIATE", ALGORITHM_VERSION, 0, now(), now());

        assertThatThrownBy(() -> insertPlayerRating(
                playerId, "BADMINTON", "DOUBLES", "31", "8", 0,
                "GOOD", ALGORITHM_VERSION, 0, now(), now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_player_ratings_player_sport_format");
    }

    @Test
    void invalidPlayerRatingSportCodeIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "TENNIS", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_sport_code");
    }

    @Test
    void invalidPlayerRatingMatchFormatIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "SINGLES", "INTERMEDIATE",
                "27", "8", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_match_format");
    }

    @Test
    void invalidPlayerRatingInitialSkillLevelIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "DOUBLES", "EXPERT",
                "27", "8", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_initial_skill_level");
    }

    @Test
    void playerRatingNaNValueIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "NaN", "8", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_rating_value_not_nan");
    }

    @Test
    void playerRatingUncertaintyMustBePositiveAndNotNaN() {
        UUID playerId = createPlayer();

        assertPlayerRatingRejected(
                playerId, "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "0", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_uncertainty_positive");
        assertPlayerRatingRejected(
                playerId, "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "-0.1", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_uncertainty_positive");
        assertPlayerRatingRejected(
                playerId, "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "NaN", 0, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_uncertainty_positive");

        UUID acceptedId = insertPlayerRating(
                playerId, "BADMINTON", "DOUBLES", "27", "0.000000001", 0,
                "INTERMEDIATE", ALGORITHM_VERSION, 0, now(), now());
        assertThat(playerRatingRepository.existsById(acceptedId)).isTrue();
    }

    @Test
    void negativeRatedMatchesIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", -1, ALGORITHM_VERSION, 0, now(), now(),
                "chk_player_ratings_rated_matches_non_negative");
    }

    @Test
    void negativePlayerRatingVersionIsRejected() {
        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, ALGORITHM_VERSION, -1, now(), now(),
                "chk_player_ratings_version_non_negative");
    }

    @Test
    void algorithmVersionMustBeNonblank() {
        UUID playerId = createPlayer();

        assertPlayerRatingRejected(
                playerId, "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, "", 0, now(), now(),
                "chk_player_ratings_algorithm_version_not_blank");
        assertPlayerRatingRejected(
                playerId, "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, "   ", 0, now(), now(),
                "chk_player_ratings_algorithm_version_not_blank");

        UUID acceptedId = insertPlayerRating(
                playerId, "BADMINTON", "DOUBLES", "27", "8", 0,
                "INTERMEDIATE", ALGORITHM_VERSION, 0, now(), now());
        assertThat(playerRatingRepository.existsById(acceptedId)).isTrue();
    }

    @Test
    void playerRatingUpdatedAtCannotPrecedeCreatedAt() {
        OffsetDateTime createdAt = now();
        OffsetDateTime updatedAt = createdAt.minusSeconds(1);

        assertPlayerRatingRejected(
                createPlayer(), "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, ALGORITHM_VERSION, 0, createdAt, updatedAt,
                "chk_player_ratings_timestamp_order");
    }

    @Test
    void duplicateRatingEventIdempotencyKeyIsRejected() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();
        insertRatingEvent(playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION);

        assertThatThrownBy(() -> insertRatingEvent(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining(
                        "uk_rating_events_match_version_player_rating");
    }

    @Test
    void sameMatchResultAllowsDifferentPlayerRatings() {
        UUID matchId = createCompletedMatch();
        UUID firstRatingId = createPlayerRating(createPlayer());
        UUID secondRatingId = createPlayerRating(createPlayer());

        insertRatingEvent(firstRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION);
        insertRatingEvent(secondRatingId, matchId, 1, "LOSS",
                "27", "26", "8", "7.5", ALGORITHM_VERSION);

        assertThat(ratingEventRepository.count()).isEqualTo(2);
    }

    @Test
    void ratingEventResultVersionMustBePositive() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();

        assertRatingEventRejected(
                playerRatingId, matchId, 0, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_result_version_positive");

        UUID acceptedId = insertRatingEvent(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION);
        assertThat(ratingEventRepository.existsById(acceptedId)).isTrue();
    }

    @Test
    void ratingEventOutcomeAllowsWinAndLossOnly() {
        UUID matchId = createCompletedMatch();
        UUID winRatingId = createPlayerRating(createPlayer());
        UUID lossRatingId = createPlayerRating(createPlayer());
        UUID invalidRatingId = createPlayerRating(createPlayer());

        insertRatingEvent(winRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION);
        insertRatingEvent(lossRatingId, matchId, 1, "LOSS",
                "27", "26", "8", "7.5", ALGORITHM_VERSION);
        assertRatingEventRejected(
                invalidRatingId, matchId, 1, "DRAW",
                "27", "27", "8", "8", ALGORITHM_VERSION,
                "chk_rating_events_outcome");

        assertThat(ratingEventRepository.count()).isEqualTo(2);
    }

    @Test
    void ratingEventRatingValuesRejectNaN() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();

        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "NaN", "28", "8", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_before_rating_not_nan");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "NaN", "8", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_after_rating_not_nan");
    }

    @Test
    void ratingEventUncertaintiesMustBePositiveAndNotNaN() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();

        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "0", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_before_uncertainty_positive");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "-1", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_before_uncertainty_positive");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "NaN", "7.5", ALGORITHM_VERSION,
                "chk_rating_events_before_uncertainty_positive");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "0", ALGORITHM_VERSION,
                "chk_rating_events_after_uncertainty_positive");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "-1", ALGORITHM_VERSION,
                "chk_rating_events_after_uncertainty_positive");
        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "NaN", ALGORITHM_VERSION,
                "chk_rating_events_after_uncertainty_positive");
    }

    @Test
    void ratingEventAlgorithmVersionMustBeNonblank() {
        UUID playerRatingId = createPlayerRating(createPlayer());
        UUID matchId = createCompletedMatch();

        assertRatingEventRejected(
                playerRatingId, matchId, 1, "WIN",
                "27", "28", "8", "7.5", " ",
                "chk_rating_events_algorithm_version_not_blank");
    }

    @Test
    void ratingForeignKeysAreEnforced() {
        assertPlayerRatingRejected(
                UUID.randomUUID(), "BADMINTON", "DOUBLES", "INTERMEDIATE",
                "27", "8", 0, ALGORITHM_VERSION, 0, now(), now(),
                "fk_player_ratings_player");

        UUID matchId = createCompletedMatch();
        assertRatingEventRejected(
                UUID.randomUUID(), matchId, 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION,
                "fk_rating_events_player_rating");

        UUID playerRatingId = createPlayerRating(createPlayer());
        assertRatingEventRejected(
                playerRatingId, UUID.randomUUID(), 1, "WIN",
                "27", "28", "8", "7.5", ALGORITHM_VERSION,
                "fk_rating_events_match");
    }

    @Test
    void ratingForeignKeysDoNotCascadeDeletes() {
        List<String> deleteRules = jdbcTemplate.queryForList("""
                SELECT rc.delete_rule
                FROM information_schema.referential_constraints rc
                WHERE rc.constraint_schema = 'public'
                  AND rc.constraint_name IN (
                    'fk_player_ratings_player',
                    'fk_rating_events_player_rating',
                    'fk_rating_events_match'
                  )
                ORDER BY rc.constraint_name
                """, String.class);

        assertThat(deleteRules).containsExactly(
                "NO ACTION", "NO ACTION", "NO ACTION");
    }

    @Test
    void completedMatchDiscoveryIndexHasExpectedColumnsAndPredicate() {
        String indexDefinition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_matches_completed_at_id_result_version'
                """, String.class);

        assertThat(indexDefinition)
                .contains("(completed_at, id, result_version)")
                .contains("WHERE")
                .contains("status")
                .contains("COMPLETED");
    }

    private void assertPlayerRatingRejected(
            UUID playerId,
            String sportCode,
            String matchFormat,
            String initialSkillLevel,
            String ratingValue,
            String uncertainty,
            int ratedMatches,
            String algorithmVersion,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            String constraintName
    ) {
        assertThatThrownBy(() -> insertPlayerRating(
                playerId,
                sportCode,
                matchFormat,
                ratingValue,
                uncertainty,
                ratedMatches,
                initialSkillLevel,
                algorithmVersion,
                version,
                createdAt,
                updatedAt
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining(constraintName);
    }

    private void assertRatingEventRejected(
            UUID playerRatingId,
            UUID matchId,
            int resultVersion,
            String outcome,
            String beforeRating,
            String afterRating,
            String beforeUncertainty,
            String afterUncertainty,
            String algorithmVersion,
            String constraintName
    ) {
        assertThatThrownBy(() -> insertRatingEvent(
                playerRatingId,
                matchId,
                resultVersion,
                outcome,
                beforeRating,
                afterRating,
                beforeUncertainty,
                afterUncertainty,
                algorithmVersion
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining(constraintName);
    }

    private UUID insertPlayerRating(
            UUID playerId,
            String sportCode,
            String matchFormat,
            String ratingValue,
            String uncertainty,
            int ratedMatches,
            String initialSkillLevel,
            String algorithmVersion,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO player_ratings (
                    id,
                    player_id,
                    sport_code,
                    match_format,
                    rating_value,
                    uncertainty,
                    rated_matches,
                    initial_skill_level,
                    algorithm_version,
                    version,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, ?, ?, ?, CAST(? AS numeric), CAST(? AS numeric),
                    ?, ?, ?, ?, ?, ?
                )
                """,
                id,
                playerId,
                sportCode,
                matchFormat,
                ratingValue,
                uncertainty,
                ratedMatches,
                initialSkillLevel,
                algorithmVersion,
                version,
                createdAt,
                updatedAt
        );
        return id;
    }

    private UUID insertRatingEvent(
            UUID playerRatingId,
            UUID matchId,
            int resultVersion,
            String outcome,
            String beforeRating,
            String afterRating,
            String beforeUncertainty,
            String afterUncertainty,
            String algorithmVersion
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rating_events (
                    id,
                    player_rating_id,
                    match_id,
                    result_version,
                    outcome,
                    before_rating,
                    after_rating,
                    before_uncertainty,
                    after_uncertainty,
                    algorithm_version,
                    created_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    CAST(? AS numeric), CAST(? AS numeric),
                    CAST(? AS numeric), CAST(? AS numeric), ?, ?
                )
                """,
                id,
                playerRatingId,
                matchId,
                resultVersion,
                outcome,
                beforeRating,
                afterRating,
                beforeUncertainty,
                afterUncertainty,
                algorithmVersion,
                now()
        );
        return id;
    }

    private UUID createPlayerRating(UUID playerId) {
        Instant now = Instant.now();
        PlayerRatingEntity entity = new PlayerRatingEntity(
                UUID.randomUUID(),
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                new BigDecimal("27.000000000"),
                new BigDecimal("8.333333333"),
                0,
                SkillLevel.INTERMEDIATE,
                ALGORITHM_VERSION,
                0,
                now,
                now
        );
        return playerRatingRepository.saveAndFlush(entity).getId();
    }

    private UUID createPlayer() {
        Instant now = Instant.now();
        Player player = Player.create("Player " + UUID.randomUUID(), now);
        return playerRepository.saveAndFlush(PlayerEntity.from(player)).getId();
    }

    private UUID createCompletedMatch() {
        Instant now = Instant.now();
        Venue venue = Venue.create(
                "Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();

        Court court = Court.create(
                venueId,
                "Court 1",
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository
                .saveAndFlush(CourtEntity.from(court))
                .getId();

        Session session = Session.create(
                venueId,
                "Rating Fixture",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        );
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();

        SessionCourt sessionCourt = SessionCourt.allocate(sessionId, courtId, now);
        UUID sessionCourtId = sessionCourtRepository
                .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                .getId();

        Match match = Match.create(
                sessionId,
                sessionCourtId,
                MatchSource.MANUAL,
                now
        ).start(now.plusSeconds(1)).complete(
                MatchResult.winnerOnly(TeamSide.A),
                now.plusSeconds(2)
        );

        return matchRepository.saveAndFlush(MatchEntity.from(match)).getId();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}

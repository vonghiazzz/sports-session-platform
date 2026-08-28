package com.sportssession.platform.rating.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.domain.RatingInitializer;
import com.sportssession.platform.rating.domain.RatingNumericNormalizer;
import com.sportssession.platform.rating.domain.RatingOutcome;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.domain.RatingUpdate;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.rating.domain.WinningTeam;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventEntity;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RatingProcessingServiceIntegrationTest extends PostgreSqlIntegrationTest {

    private static final List<SkillLevel> GOLDEN_SKILLS = List.of(
            SkillLevel.GOOD,
            SkillLevel.INTERMEDIATE_PLUS,
            SkillLevel.WEAK_PLUS,
            SkillLevel.WEAK
    );

    @Autowired
    private RatingProcessingService ratingProcessingService;

    @Autowired
    private RatingReconciliationService ratingReconciliationService;

    @Autowired
    private RatingContextLock ratingContextLock;

    @Autowired
    private PendingCompletedMatchRatingLookup pendingMatchLookup;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    }

    @Test
    void firstMatchAppliesExactGoldenRatingsAndFourAtomicEvents() throws Exception {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createManualMatchThroughApi(fixture);
        startAndCompleteThroughApi(matchId, TeamSide.A, 21, 18);

        assertThat(ratingProcessingService.processRating(matchId))
                .isEqualTo(RatingProcessingResult.APPLIED);

        Map<UUID, PlayerRatingEntity> ratings = ratingsByPlayer();
        assertThat(ratings).hasSize(4);
        assertGoldenRating(ratings.get(fixture.playerIds().get(0)),
                SkillLevel.GOOD, "35.552386939", "8.258402475");
        assertGoldenRating(ratings.get(fixture.playerIds().get(1)),
                SkillLevel.INTERMEDIATE_PLUS, "31.552386939", "8.258402475");
        assertGoldenRating(ratings.get(fixture.playerIds().get(2)),
                SkillLevel.WEAK_PLUS, "18.447613061", "8.258402475");
        assertGoldenRating(ratings.get(fixture.playerIds().get(3)),
                SkillLevel.WEAK, "14.447613061", "8.258402475");

        List<RatingEventEntity> events = events(matchId);
        assertThat(events).hasSize(4);
        assertEvent(events, ratings.get(fixture.playerIds().get(0)),
                RatingOutcome.WIN, "35.000000000", "35.552386939");
        assertEvent(events, ratings.get(fixture.playerIds().get(1)),
                RatingOutcome.WIN, "31.000000000", "31.552386939");
        assertEvent(events, ratings.get(fixture.playerIds().get(2)),
                RatingOutcome.LOSS, "19.000000000", "18.447613061");
        assertEvent(events, ratings.get(fixture.playerIds().get(3)),
                RatingOutcome.LOSS, "15.000000000", "14.447613061");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getBeforeUncertainty())
                    .isEqualByComparingTo("8.333333333");
            assertThat(event.getAfterUncertainty())
                    .isEqualByComparingTo("8.258402475");
            assertThat(event.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
        });
        Set<Instant> processingTimes = new HashSet<>();
        ratings.values().forEach(rating -> {
            processingTimes.add(rating.getCreatedAt());
            processingTimes.add(rating.getUpdatedAt());
        });
        events.forEach(event -> processingTimes.add(event.getCreatedAt()));
        assertThat(processingTimes).hasSize(1);
        assertOperationalStateReleased(fixture, matchId);
    }

    @Test
    void secondOrderedMatchReusesRatingsAndIgnoresChangedProfileSkill() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstCompletedAt = Instant.parse("2026-08-27T01:00:00Z");
        UUID firstMatchId = createCompletedMatch(
                fixture, firstCompletedAt, TeamSide.A, 21, 18);
        assertThat(ratingProcessingService.processRating(firstMatchId))
                .isEqualTo(RatingProcessingResult.APPLIED);
        Map<UUID, RatingSnapshot> afterFirst = ratingSnapshots();
        Set<UUID> ratingIds = new HashSet<>();
        afterFirst.values().forEach(snapshot -> ratingIds.add(snapshot.ratingId()));

        UUID changedPlayerId = fixture.playerIds().getFirst();
        jdbcTemplate.update("""
                UPDATE player_sport_profiles
                   SET skill_level = 'WEAK', updated_at = now()
                 WHERE player_id = ? AND sport_code = 'BADMINTON'
                """, changedPlayerId);
        UUID secondMatchId = createCompletedMatch(
                fixture,
                firstCompletedAt.plusSeconds(60),
                TeamSide.B,
                null,
                null
        );

        assertThat(ratingProcessingService.processRating(secondMatchId))
                .isEqualTo(RatingProcessingResult.APPLIED);

        Map<UUID, PlayerRatingEntity> ratings = ratingsByPlayer();
        assertThat(ratings.values()).extracting(PlayerRatingEntity::getId)
                .containsExactlyInAnyOrderElementsOf(ratingIds);
        assertThat(ratings.values()).allSatisfy(rating -> {
            assertThat(rating.getRatedMatches()).isEqualTo(2);
            assertThat(rating.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
        });
        assertThat(ratings.get(changedPlayerId).getInitialSkillLevel())
                .isEqualTo(SkillLevel.GOOD);
        List<RatingUpdate> expectedUpdates = new WengLinPlackettLuceRatingEngine()
                .rate(
                        states(afterFirst, fixture.playerIds().subList(0, 2)),
                        states(afterFirst, fixture.playerIds().subList(2, 4)),
                        WinningTeam.B
                );
        for (int index = 0; index < fixture.playerIds().size(); index++) {
            PlayerRatingEntity rating = ratings.get(fixture.playerIds().get(index));
            RatingSnapshot previous = afterFirst.get(rating.getPlayerId());
            assertThat(rating.getCreatedAt()).isEqualTo(previous.createdAt());
            assertThat(rating.getRatingValue()).isEqualByComparingTo(
                    RatingNumericNormalizer.normalizeToDecimal(
                            expectedUpdates.get(index).after().mu()
                    )
            );
            assertThat(rating.getUncertainty()).isEqualByComparingTo(
                    RatingNumericNormalizer.normalizeToDecimal(
                            expectedUpdates.get(index).after().sigma()
                    )
            );
        }
        for (RatingEventEntity event : events(secondMatchId)) {
            UUID playerId = playerIdForRating(event.getPlayerRatingId(), ratings);
            RatingSnapshot previous = afterFirst.get(playerId);
            assertThat(event.getBeforeRating())
                    .isEqualByComparingTo(previous.ratingValue());
            assertThat(event.getBeforeUncertainty())
                    .isEqualByComparingTo(previous.uncertainty());
        }
        assertThat(ratingEventRepository.count()).isEqualTo(8);
    }

    @Test
    void missingInitialProfileRollsBackAndLeavesMatchOperationsCommitted()
            throws Exception {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of(3));
        UUID matchId = createManualMatchThroughApi(fixture);
        startAndCompleteThroughApi(matchId, TeamSide.A, null, null);

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(MissingPlayerRatingPriorException.class);

        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
        assertOperationalStateReleased(fixture, matchId);
    }

    @Test
    void sequentialRetryReturnsAlreadyAppliedWithoutRewritingState() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T03:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        assertThat(ratingProcessingService.processRating(matchId))
                .isEqualTo(RatingProcessingResult.APPLIED);
        Map<UUID, RatingSnapshot> afterFirst = ratingSnapshots();

        assertThat(ratingProcessingService.processRating(matchId))
                .isEqualTo(RatingProcessingResult.ALREADY_APPLIED);

        assertThat(ratingSnapshots()).isEqualTo(afterFirst);
        assertThat(ratingEventRepository.count()).isEqualTo(4);
    }

    @Test
    void alreadyAppliedEventsWithAnotherAlgorithmAreRejected() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T03:30:00Z"),
                TeamSide.A,
                null,
                null
        );
        ratingProcessingService.processRating(matchId);
        Map<UUID, RatingSnapshot> beforeRetry = ratingSnapshots();
        UUID eventId = events(matchId).getFirst().getId();
        jdbcTemplate.update("""
                UPDATE rating_events
                   SET algorithm_version = 'legacy-test-v0'
                 WHERE id = ?
                """, eventId);

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(RatingProcessingIntegrityException.class)
                .hasMessageContaining("algorithmVersion");

        assertThat(ratingSnapshots()).isEqualTo(beforeRetry);
        assertThat(events(matchId)).hasSize(4);
    }

    @Test
    void partialEventSetIsRejectedWithoutRatingMutation() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T04:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        ratingProcessingService.processRating(matchId);
        UUID retainedEventId = events(matchId).getFirst().getId();
        jdbcTemplate.update("""
                DELETE FROM rating_events
                 WHERE match_id = ? AND id <> ?
                """, matchId, retainedEventId);
        Map<UUID, RatingSnapshot> beforeRetry = ratingSnapshots();

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(RatingProcessingIntegrityException.class)
                .hasMessageContaining("zero or four");

        assertThat(ratingSnapshots()).isEqualTo(beforeRetry);
        assertThat(events(matchId)).hasSize(1);
    }

    @Test
    void wrongFourEventIdentitySetIsRejected() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T05:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        ratingProcessingService.processRating(matchId);
        Map<UUID, RatingSnapshot> beforeRetry = ratingSnapshots();
        UUID externalRatingId = createExternalRating();
        UUID replacedEventId = events(matchId).getFirst().getId();
        jdbcTemplate.update("""
                UPDATE rating_events
                   SET player_rating_id = ?
                 WHERE id = ?
                """, externalRatingId, replacedEventId);

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(RatingProcessingIntegrityException.class)
                .hasMessageContaining("identities");

        beforeRetry.forEach((playerId, snapshot) -> assertThat(
                snapshot(ratingsByPlayer().get(playerId))
        ).isEqualTo(snapshot));
        assertThat(events(matchId)).hasSize(4);
    }

    @Test
    void existingLegacyAlgorithmVersionIsRejectedWithoutOtherWrites() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID legacyPlayerId = fixture.playerIds().getFirst();
        PlayerRatingEntity legacy = PlayerRatingEntity.initialize(
                legacyPlayerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.GOOD,
                RatingInitializer.initialize(SkillLevel.GOOD),
                "legacy-test-v0",
                Instant.now()
        );
        playerRatingRepository.saveAndFlush(legacy);
        RatingSnapshot before = snapshot(legacy);
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T06:00:00Z"),
                TeamSide.A,
                null,
                null
        );

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(RatingProcessingIntegrityException.class)
                .hasMessageContaining("algorithmVersion");

        assertThat(playerRatingRepository.count()).isEqualTo(1);
        assertThat(snapshot(playerRatingRepository.findById(legacy.getId())
                .orElseThrow())).isEqualTo(before);
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void resultVersionTwoIsRejectedBeforeRatingMutation() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T07:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        jdbcTemplate.update(
                "UPDATE matches SET result_version = 2 WHERE id = ?",
                matchId
        );

        assertThatThrownBy(() -> ratingProcessingService.processRating(matchId))
                .isInstanceOf(UnsupportedRatingResultVersionException.class)
                .hasMessageContaining("resultVersion 1");

        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void laterMatchAfterEarlierMatchAppliesInCanonicalOrder() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T08:00:00Z");
        UUID first = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID second = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);

        assertThat(ratingProcessingService.processRating(first))
                .isEqualTo(RatingProcessingResult.APPLIED);
        assertThat(ratingProcessingService.processRating(second))
                .isEqualTo(RatingProcessingResult.APPLIED);

        assertThat(playerRatingRepository.findAll())
                .allSatisfy(rating -> assertThat(rating.getRatedMatches())
                        .isEqualTo(2));
        assertThat(ratingEventRepository.count()).isEqualTo(8);
    }

    @Test
    void olderMatchIsRejectedAfterNewerMatchWasApplied() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant olderTime = Instant.parse("2026-08-27T09:00:00Z");
        UUID older = createCompletedMatch(
                fixture, olderTime, TeamSide.A, null, null);
        UUID newer = createCompletedMatch(
                fixture, olderTime.plusSeconds(30), TeamSide.B, null, null);
        assertThat(ratingProcessingService.processRating(newer))
                .isEqualTo(RatingProcessingResult.APPLIED);
        Map<UUID, RatingSnapshot> afterNewer = ratingSnapshots();

        assertThatThrownBy(() -> ratingProcessingService.processRating(older))
                .isInstanceOf(RatingHistoryOrderingException.class);

        assertThat(ratingSnapshots()).isEqualTo(afterNewer);
        assertThat(events(newer)).hasSize(4);
        assertThat(events(older)).isEmpty();
    }

    @Test
    void sameCompletedAtUsesPostgreSqlUuidOrderForHistoryGuard() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant completedAt = Instant.parse("2026-08-27T10:00:00Z");
        UUID firstCreated = createCompletedMatch(
                fixture, completedAt, TeamSide.A, null, null);
        UUID secondCreated = createCompletedMatch(
                fixture, completedAt, TeamSide.B, null, null);
        List<UUID> postgresOrder = jdbcTemplate.query(
                "SELECT id FROM matches WHERE id IN (?, ?) ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                firstCreated,
                secondCreated
        );
        UUID lower = postgresOrder.getFirst();
        UUID higher = postgresOrder.getLast();
        ratingProcessingService.processRating(higher);
        Map<UUID, RatingSnapshot> afterHigher = ratingSnapshots();

        assertThatThrownBy(() -> ratingProcessingService.processRating(lower))
                .isInstanceOf(RatingHistoryOrderingException.class);

        assertThat(ratingSnapshots()).isEqualTo(afterHigher);
        assertThat(events(higher)).hasSize(4);
        assertThat(events(lower)).isEmpty();
    }

    @Test
    void validScoresDoNotAffectEquivalentRatingCalculations() {
        RuntimeFixture firstFixture = createFixture(GOLDEN_SKILLS, Set.of());
        RuntimeFixture secondFixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID firstMatch = createCompletedMatch(
                firstFixture,
                Instant.parse("2026-08-27T11:00:00Z"),
                TeamSide.A,
                21,
                18
        );
        UUID secondMatch = createCompletedMatch(
                secondFixture,
                Instant.parse("2026-08-27T11:01:00Z"),
                TeamSide.A,
                30,
                5
        );

        ratingProcessingService.processRating(firstMatch);
        ratingProcessingService.processRating(secondMatch);

        Map<UUID, PlayerRatingEntity> ratings = ratingsByPlayer();
        for (int index = 0; index < 4; index++) {
            PlayerRatingEntity first = ratings.get(firstFixture.playerIds().get(index));
            PlayerRatingEntity second = ratings.get(secondFixture.playerIds().get(index));
            assertThat(first.getRatingValue())
                    .isEqualByComparingTo(second.getRatingValue());
            assertThat(first.getUncertainty())
                    .isEqualByComparingTo(second.getUncertainty());
        }
    }

    @Test
    void reconciliationReturnsIdleWithoutRatingWrites() {
        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.IDLE);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void reconciliationRecoversCompletedOperationalMatchAfterRatingCrashWindow()
            throws Exception {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createManualMatchThroughApi(fixture);
        startAndCompleteThroughApi(matchId, TeamSide.A, 21, 18);

        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.APPLIED);

        assertThat(playerRatingRepository.count()).isEqualTo(4);
        assertThat(events(matchId)).hasSize(4);
        assertOperationalStateReleased(fixture, matchId);
    }

    @Test
    void reconciliationProcessesAtMostOnePendingMatchInCanonicalOrder() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T12:00:00Z");
        UUID first = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID second = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);

        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.APPLIED);
        assertThat(events(first)).hasSize(4);
        assertThat(events(second)).isEmpty();
        assertThat(playerRatingRepository.findAll())
                .allSatisfy(rating -> assertThat(rating.getRatedMatches())
                        .isEqualTo(1));

        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.APPLIED);
        assertThat(events(second)).hasSize(4);
        assertThat(playerRatingRepository.findAll())
                .allSatisfy(rating -> assertThat(rating.getRatedMatches())
                        .isEqualTo(2));
    }

    @Test
    void reconciliationUsesPostgreSqlUuidOrderForEqualCompletionTimes() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant completedAt = Instant.parse("2026-08-27T13:00:00Z");
        UUID firstCreated = createCompletedMatch(
                fixture, completedAt, TeamSide.A, null, null);
        UUID secondCreated = createCompletedMatch(
                fixture, completedAt, TeamSide.B, null, null);
        List<UUID> databaseOrder = jdbcTemplate.query(
                "SELECT id FROM matches WHERE id IN (?, ?) ORDER BY id ASC",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                firstCreated,
                secondCreated
        );

        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.APPLIED);

        assertThat(events(databaseOrder.getFirst())).hasSize(4);
        assertThat(events(databaseOrder.getLast())).isEmpty();
    }

    @Test
    void reconciliationSkipsFullyProcessedMatchAndAppliesNextPendingMatch() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T14:00:00Z");
        UUID processed = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID pending = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        assertThat(ratingProcessingService.processRating(processed))
                .isEqualTo(RatingProcessingResult.APPLIED);

        assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.APPLIED);

        assertThat(events(processed)).hasSize(4);
        assertThat(events(pending)).hasSize(4);
        assertThat(playerRatingRepository.findAll())
                .allSatisfy(rating -> assertThat(rating.getRatedMatches())
                        .isEqualTo(2));
    }

    @Test
    void reconciliationKeepsPartialEventPoisonAheadOfLaterMatch() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T15:00:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        ratingProcessingService.processRating(poison);
        UUID retainedEvent = events(poison).getFirst().getId();
        jdbcTemplate.update(
                "DELETE FROM rating_events WHERE match_id = ? AND id <> ?",
                poison,
                retainedEvent
        );
        Map<UUID, RatingSnapshot> before = ratingSnapshots();

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(RatingProcessingIntegrityException.class);

        assertThat(events(poison)).hasSize(1);
        assertThat(events(later)).isEmpty();
        assertThat(ratingSnapshots()).isEqualTo(before);
    }

    @Test
    void reconciliationKeepsWrongFourEventIdentityPoisonAheadOfLaterMatch() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T16:00:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        ratingProcessingService.processRating(poison);
        UUID externalRating = createExternalRating();
        jdbcTemplate.update(
                "UPDATE rating_events SET player_rating_id = ? WHERE id = ?",
                externalRating,
                events(poison).getFirst().getId()
        );

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(RatingProcessingIntegrityException.class);

        assertThat(events(poison)).hasSize(4);
        assertThat(events(later)).isEmpty();
    }

    @Test
    void reconciliationKeepsAlgorithmMismatchPoisonAheadOfLaterMatch() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T17:00:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        ratingProcessingService.processRating(poison);
        jdbcTemplate.update(
                "UPDATE rating_events SET algorithm_version = 'legacy-test-v0' "
                        + "WHERE id = ?",
                events(poison).getFirst().getId()
        );

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(RatingProcessingIntegrityException.class);

        assertThat(events(poison)).hasSize(4);
        assertThat(events(later)).isEmpty();
    }

    @Test
    void reconciliationKeepsPlayerRatingAlgorithmMismatchAsPoison() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T17:10:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        ratingProcessingService.processRating(poison);
        jdbcTemplate.update(
                "UPDATE player_ratings SET algorithm_version = 'legacy-test-v0' "
                        + "WHERE id = ?",
                events(poison).getFirst().getPlayerRatingId()
        );

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(RatingProcessingIntegrityException.class);

        assertThat(events(poison)).hasSize(4);
        assertThat(events(later)).isEmpty();
    }

    @Test
    void reconciliationKeepsMoreThanFourEventsAsPoison() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T17:20:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        ratingProcessingService.processRating(poison);
        UUID externalRating = createExternalRating();
        ratingEventRepository.saveAndFlush(RatingEventEntity.create(
                externalRating,
                poison,
                1,
                RatingOutcome.WIN,
                new RatingState(25.0, 8.0),
                new RatingState(26.0, 7.9),
                ALGORITHM_VERSION,
                Instant.now()
        ));

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(RatingProcessingIntegrityException.class);

        assertThat(events(poison)).hasSize(5);
        assertThat(events(later)).isEmpty();
    }

    @Test
    void reconciliationSelectsUnsupportedResultVersionPoisonBeforeLaterMatch() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T18:00:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        jdbcTemplate.update(
                "UPDATE matches SET result_version = 2 WHERE id = ?",
                poison
        );

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(UnsupportedRatingResultVersionException.class);

        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
        assertThat(events(later)).isEmpty();
    }

    @Test
    void reconciliationKeepsVersionTwoWithFourCorrectEventsAsPoison() {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        Instant firstTime = Instant.parse("2026-08-27T18:30:00Z");
        UUID poison = createCompletedMatch(
                fixture, firstTime, TeamSide.A, null, null);
        UUID later = createCompletedMatch(
                fixture, firstTime.plusSeconds(1), TeamSide.B, null, null);
        jdbcTemplate.update(
                "UPDATE matches SET result_version = 2 WHERE id = ?",
                poison
        );
        createVersionTwoRatingEvidence(fixture, poison, TeamSide.A);
        Map<UUID, RatingSnapshot> before = ratingSnapshots();

        assertThat(events(poison, 2)).hasSize(4);
        assertThat(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).contains(poison);

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(UnsupportedRatingResultVersionException.class);

        assertThat(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).contains(poison);
        assertThat(events(poison, 2)).hasSize(4);
        assertThat(events(later)).isEmpty();
        assertThat(ratingSnapshots()).isEqualTo(before);

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(UnsupportedRatingResultVersionException.class);

        assertThat(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).contains(poison);
        assertThat(events(poison, 2)).hasSize(4);
        assertThat(events(later)).isEmpty();
        assertThat(ratingSnapshots()).isEqualTo(before);
    }

    @Test
    void missingProfilePoisonRollsBackReleasesLockAndRemainsEarliest() {
        RuntimeFixture poisonFixture = createFixture(GOLDEN_SKILLS, Set.of(3));
        RuntimeFixture laterFixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID poison = createCompletedMatch(
                poisonFixture,
                Instant.parse("2026-08-27T19:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        UUID later = createCompletedMatch(
                laterFixture,
                Instant.parse("2026-08-27T19:00:01Z"),
                TeamSide.A,
                null,
                null
        );

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(MissingPlayerRatingPriorException.class);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();

        Boolean reacquired = new TransactionTemplate(transactionManager).execute(
                status -> ratingContextLock.tryAcquire(
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES
                )
        );
        assertThat(reacquired).isTrue();

        assertThatThrownBy(this::reconcileOne)
                .isInstanceOf(MissingPlayerRatingPriorException.class);
        assertThat(events(poison)).isEmpty();
        assertThat(events(later)).isEmpty();
    }

    @Test
    void overlappingReconciliationReturnsAppliedAndBusyWithoutDoubleRating()
            throws Exception {
        RuntimeFixture fixture = createFixture(GOLDEN_SKILLS, Set.of());
        UUID matchId = createCompletedMatch(
                fixture,
                Instant.parse("2026-08-27T20:00:00Z"),
                TeamSide.A,
                null,
                null
        );
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        RatingContextLock holdingLock = new HoldingRatingContextLock(
                ratingContextLock,
                lockHeld,
                releaseOwner
        );
        RatingReconciliationService holdingService =
                new RatingReconciliationService(
                        holdingLock,
                        pendingMatchLookup,
                        ratingProcessingService
                );
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<RatingReconciliationResult> owner = executor.submit(
                    () -> transactionTemplate.execute(
                            status -> holdingService.reconcileOne(
                                    SportCode.BADMINTON,
                                    MatchFormat.DOUBLES
                            )
                    )
            );
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(reconcileOne()).isEqualTo(RatingReconciliationResult.BUSY);

            releaseOwner.countDown();
            assertThat(owner.get(20, TimeUnit.SECONDS))
                    .isEqualTo(RatingReconciliationResult.APPLIED);
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
        }

        assertThat(events(matchId)).hasSize(4);
        assertThat(playerRatingRepository.count()).isEqualTo(4);
        assertThat(playerRatingRepository.findAll())
                .allSatisfy(rating -> assertThat(rating.getRatedMatches())
                        .isEqualTo(1));
    }

    private RatingReconciliationResult reconcileOne() {
        return ratingReconciliationService.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
    }

    private RuntimeFixture createFixture(
            List<SkillLevel> skillLevels,
            Set<Integer> missingProfileIndexes
    ) {
        Instant now = Instant.now().minusSeconds(30);
        Venue venue = Venue.create("Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
        Court court = Court.create(
                venueId,
                "Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
        Session session = Session.create(
                venueId,
                "Rating Processing Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        ).start(now.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();
        UUID sessionCourtId = sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(SessionCourt.allocate(
                        sessionId,
                        courtId,
                        now.plusSeconds(2)
                ))
        ).getId();

        List<UUID> playerIds = new ArrayList<>();
        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Player player = Player.create(
                    "Player " + UUID.randomUUID(),
                    now.plusSeconds(3)
            );
            UUID playerId = playerRepository
                    .saveAndFlush(PlayerEntity.from(player))
                    .getId();
            playerIds.add(playerId);
            if (!missingProfileIndexes.contains(index)) {
                profileRepository.saveAndFlush(PlayerSportProfileEntity.from(
                        PlayerSportProfile.create(
                                playerId,
                                SportCode.BADMINTON,
                                skillLevels.get(index),
                                now.plusSeconds(3)
                        )
                ));
            }
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    now.plusSeconds(3)
            ).checkIn(now.plusSeconds(4));
            participantIds.add(sessionParticipantRepository.saveAndFlush(
                    SessionParticipantEntity.from(participant)
            ).getId());
        }
        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                playerIds,
                participantIds
        );
    }

    private UUID createCompletedMatch(
            RuntimeFixture fixture,
            Instant completedAt,
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) {
        MatchResult result = teamAScore == null
                ? MatchResult.winnerOnly(winnerTeam)
                : MatchResult.withScore(winnerTeam, teamAScore, teamBScore);
        Match match = Match.create(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                MatchSource.MANUAL,
                completedAt.minusSeconds(2)
        ).start(completedAt.minusSeconds(1)).complete(result, completedAt);
        matchRepository.saveAndFlush(MatchEntity.from(match));
        saveAssignments(match.id(), fixture.participantIds());
        return match.id();
    }

    private UUID createManualMatchThroughApi(RuntimeFixture fixture) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sessionCourtId", fixture.sessionCourtId());
        request.put("participants", assignments(fixture.participantIds()));
        String response = mockMvc.perform(post(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return UUID.fromString(body.get("id").asText());
    }

    private void startAndCompleteThroughApi(
            UUID matchId,
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore
    ) throws Exception {
        mockMvc.perform(post("/api/matches/{matchId}/start", matchId))
                .andExpect(status().isOk());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("winnerTeam", winnerTeam);
        result.put("teamAScore", teamAScore);
        result.put("teamBScore", teamBScore);
        mockMvc.perform(post("/api/matches/{matchId}/complete", matchId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(result)))
                .andExpect(status().isOk());
    }

    private void saveAssignments(UUID matchId, List<UUID> participantIds) {
        matchParticipantRepository.saveAllAndFlush(List.of(
                assignment(matchId, participantIds.get(0), TeamSide.A, 1),
                assignment(matchId, participantIds.get(1), TeamSide.A, 2),
                assignment(matchId, participantIds.get(2), TeamSide.B, 1),
                assignment(matchId, participantIds.get(3), TeamSide.B, 2)
        ));
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

    private List<Map<String, Object>> assignments(List<UUID> participantIds) {
        return List.of(
                requestAssignment(participantIds.get(0), TeamSide.A, 1),
                requestAssignment(participantIds.get(1), TeamSide.A, 2),
                requestAssignment(participantIds.get(2), TeamSide.B, 1),
                requestAssignment(participantIds.get(3), TeamSide.B, 2)
        );
    }

    private Map<String, Object> requestAssignment(
            UUID participantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return Map.of(
                "sessionParticipantId", participantId,
                "teamSide", teamSide,
                "teamSlot", teamSlot
        );
    }

    private UUID createExternalRating() {
        Player player = Player.create(
                "External " + UUID.randomUUID(),
                Instant.now()
        );
        UUID playerId = playerRepository
                .saveAndFlush(PlayerEntity.from(player))
                .getId();
        PlayerRatingEntity rating = PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.INTERMEDIATE,
                RatingInitializer.initialize(SkillLevel.INTERMEDIATE),
                ALGORITHM_VERSION,
                Instant.now()
        );
        return playerRatingRepository.saveAndFlush(rating).getId();
    }

    private void createVersionTwoRatingEvidence(
            RuntimeFixture fixture,
            UUID matchId,
            TeamSide winnerTeam
    ) {
        Instant now = Instant.parse("2026-08-27T18:30:02Z");
        List<PlayerRatingEntity> initializedRatings = new ArrayList<>(4);
        for (int index = 0; index < fixture.playerIds().size(); index++) {
            SkillLevel skillLevel = GOLDEN_SKILLS.get(index);
            initializedRatings.add(PlayerRatingEntity.initialize(
                    fixture.playerIds().get(index),
                    SportCode.BADMINTON,
                    MatchFormat.DOUBLES,
                    skillLevel,
                    RatingInitializer.initialize(skillLevel),
                    ALGORITHM_VERSION,
                    now
            ));
        }
        List<PlayerRatingEntity> ratings = playerRatingRepository
                .saveAllAndFlush(initializedRatings);
        WengLinPlackettLuceRatingEngine engine =
                new WengLinPlackettLuceRatingEngine();
        List<RatingUpdate> updates = engine.rate(
                ratings.subList(0, 2).stream()
                        .map(PlayerRatingEntity::toRatingState)
                        .toList(),
                ratings.subList(2, 4).stream()
                        .map(PlayerRatingEntity::toRatingState)
                        .toList(),
                winnerTeam == TeamSide.A ? WinningTeam.A : WinningTeam.B
        );
        List<RatingEventEntity> events = new ArrayList<>(4);
        for (int index = 0; index < ratings.size(); index++) {
            PlayerRatingEntity rating = ratings.get(index);
            RatingUpdate update = updates.get(index);
            boolean won = (index < 2) == (winnerTeam == TeamSide.A);
            rating.applyRating(update.after(), now);
            events.add(RatingEventEntity.create(
                    rating.getId(),
                    matchId,
                    2,
                    won ? RatingOutcome.WIN : RatingOutcome.LOSS,
                    update.before(),
                    update.after(),
                    ALGORITHM_VERSION,
                    now
            ));
        }
        ratingEventRepository.saveAllAndFlush(events);
    }

    private Map<UUID, PlayerRatingEntity> ratingsByPlayer() {
        Map<UUID, PlayerRatingEntity> result = new HashMap<>();
        playerRatingRepository.findAll().forEach(
                rating -> result.put(rating.getPlayerId(), rating)
        );
        return result;
    }

    private Map<UUID, RatingSnapshot> ratingSnapshots() {
        Map<UUID, RatingSnapshot> result = new HashMap<>();
        playerRatingRepository.findAll().forEach(
                rating -> result.put(rating.getPlayerId(), snapshot(rating))
        );
        return Map.copyOf(result);
    }

    private RatingSnapshot snapshot(PlayerRatingEntity rating) {
        return new RatingSnapshot(
                rating.getId(),
                rating.getRatingValue(),
                rating.getUncertainty(),
                rating.getRatedMatches(),
                rating.getInitialSkillLevel(),
                rating.getAlgorithmVersion(),
                rating.getCreatedAt(),
                rating.getUpdatedAt()
        );
    }

    private List<RatingState> states(
            Map<UUID, RatingSnapshot> snapshots,
            List<UUID> playerIds
    ) {
        return playerIds.stream()
                .map(snapshots::get)
                .map(snapshot -> new RatingState(
                        snapshot.ratingValue().doubleValue(),
                        snapshot.uncertainty().doubleValue()
                ))
                .toList();
    }

    private List<RatingEventEntity> events(UUID matchId) {
        return events(matchId, 1);
    }

    private List<RatingEventEntity> events(UUID matchId, int resultVersion) {
        return ratingEventRepository
                .findAllByMatchIdAndResultVersionOrderByPlayerRatingId(
                        matchId,
                        resultVersion
                );
    }

    private UUID playerIdForRating(
            UUID ratingId,
            Map<UUID, PlayerRatingEntity> ratings
    ) {
        return ratings.values().stream()
                .filter(rating -> rating.getId().equals(ratingId))
                .map(PlayerRatingEntity::getPlayerId)
                .findFirst()
                .orElseThrow();
    }

    private void assertGoldenRating(
            PlayerRatingEntity rating,
            SkillLevel initialSkillLevel,
            String expectedRating,
            String expectedUncertainty
    ) {
        assertThat(rating.getSportCode()).isEqualTo(SportCode.BADMINTON);
        assertThat(rating.getMatchFormat()).isEqualTo(MatchFormat.DOUBLES);
        assertThat(rating.getRatingValue()).isEqualByComparingTo(expectedRating);
        assertThat(rating.getUncertainty())
                .isEqualByComparingTo(expectedUncertainty);
        assertThat(rating.getRatedMatches()).isEqualTo(1);
        assertThat(rating.getInitialSkillLevel()).isEqualTo(initialSkillLevel);
        assertThat(rating.getAlgorithmVersion()).isEqualTo(ALGORITHM_VERSION);
    }

    private void assertEvent(
            List<RatingEventEntity> events,
            PlayerRatingEntity rating,
            RatingOutcome outcome,
            String beforeRating,
            String afterRating
    ) {
        RatingEventEntity event = events.stream()
                .filter(candidate -> candidate.getPlayerRatingId()
                        .equals(rating.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(event.getOutcome()).isEqualTo(outcome);
        assertThat(event.getBeforeRating()).isEqualByComparingTo(beforeRating);
        assertThat(event.getAfterRating()).isEqualByComparingTo(afterRating);
    }

    private void assertOperationalStateReleased(
            RuntimeFixture fixture,
            UUID matchId
    ) {
        assertThat(matchRepository.findById(matchId).orElseThrow()
                .toDomain().status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow().getStatus()).isEqualTo(SessionCourtStatus.AVAILABLE);
        assertThat(fixture.participantIds()).allSatisfy(participantId ->
                assertThat(sessionParticipantRepository.findById(participantId)
                        .orElseThrow().getStatus())
                        .isEqualTo(ParticipantStatus.WAITING));
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> playerIds,
            List<UUID> participantIds
    ) {
        private RuntimeFixture {
            playerIds = List.copyOf(playerIds);
            participantIds = List.copyOf(participantIds);
        }
    }

    private record HoldingRatingContextLock(
            RatingContextLock delegate,
            CountDownLatch lockHeld,
            CountDownLatch releaseOwner
    ) implements RatingContextLock {

        @Override
        public boolean tryAcquire(
                SportCode sportCode,
                MatchFormat matchFormat
        ) {
            boolean acquired = delegate.tryAcquire(sportCode, matchFormat);
            if (!acquired) {
                return false;
            }
            lockHeld.countDown();
            try {
                if (!releaseOwner.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "Timed out waiting to release Rating context lock"
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "Interrupted while holding Rating context lock",
                        exception
                );
            }
            return true;
        }
    }

    private record RatingSnapshot(
            UUID ratingId,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            SkillLevel initialSkillLevel,
            String algorithmVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}

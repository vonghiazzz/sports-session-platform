package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.matchmaking.application.MatchmakingRatingReader;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionException;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingSnapshot;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingRatingReaderIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-08-28T04:00:00Z");
    private static final BigDecimal INITIAL_UNCERTAINTY =
            new BigDecimal("8.333333333");

    @Autowired
    private MatchmakingRatingReader ratingReader;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void returnsExactPersistedRatingsForFourPlayersWithoutWrites() {
        List<UUID> playerIds = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            UUID playerId = createPlayerWithProfile(
                    index,
                    SkillLevel.GOOD
            );
            playerIds.add(playerId);
            createPersistedRating(
                    playerId,
                    new BigDecimal(index + ".123456789"),
                    new BigDecimal(index + ".987654321"),
                    index * 3,
                    SkillLevel.WEAK,
                    WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION
            );
        }
        List<PersistedState> before = persistedStates();
        long eventCountBefore = ratingEventRepository.count();

        Map<UUID, MatchmakingRatingSnapshot> result =
                ratingReader.readEffectiveRatings(
                        playerIds,
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES
                );

        assertThat(result).hasSize(4).containsOnlyKeys(playerIds);
        for (int index = 1; index <= 4; index++) {
            MatchmakingRatingSnapshot rating = result.get(uuid(index));
            assertThat(rating.ratingValue())
                    .isEqualByComparingTo(index + ".123456789");
            assertThat(rating.uncertainty())
                    .isEqualByComparingTo(index + ".987654321");
            assertThat(rating.ratedMatches()).isEqualTo(index * 3);
            assertThat(rating.ratingBasis()).isEqualTo(RatingBasis.PERSISTED);
        }
        assertThat(persistedStates()).isEqualTo(before);
        assertThat(ratingEventRepository.count()).isEqualTo(eventCountBefore);
    }

    @Test
    void persistedRatingWinsOverChangedCurrentSkillLevel() {
        UUID playerId = createPlayerWithProfile(1, SkillLevel.GOOD);
        createPersistedRating(
                playerId,
                new BigDecimal("19.500000001"),
                new BigDecimal("7.250000002"),
                9,
                SkillLevel.WEAK,
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION
        );

        MatchmakingRatingSnapshot result = ratingReader.readEffectiveRatings(
                List.of(playerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        ).get(playerId);

        assertThat(result.ratingValue())
                .isEqualByComparingTo("19.500000001");
        assertThat(result.uncertainty())
                .isEqualByComparingTo("7.250000002");
        assertThat(result.ratedMatches()).isEqualTo(9);
        assertThat(result.ratingBasis()).isEqualTo(RatingBasis.PERSISTED);
    }

    @ParameterizedTest(name = "{0} has canonical initial mu {1}")
    @MethodSource("initialPriorCases")
    void allSkillLevelsResolveToCanonicalReadOnlyInitialPrior(
            SkillLevel skillLevel,
            String expectedRating
    ) {
        UUID playerId = createPlayerWithProfile(1, skillLevel);

        Map<UUID, MatchmakingRatingSnapshot> result =
                ratingReader.readEffectiveRatings(
                        List.of(playerId),
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES
                );

        MatchmakingRatingSnapshot rating = result.get(playerId);
        assertThat(rating.ratingValue()).isEqualTo(new BigDecimal(expectedRating));
        assertThat(rating.uncertainty()).isEqualTo(INITIAL_UNCERTAINTY);
        assertThat(rating.ratedMatches()).isZero();
        assertThat(rating.ratingBasis()).isEqualTo(RatingBasis.INITIAL_PRIOR);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void mixedBatchReturnsEveryPersistedAndInitialPriorRatingImmutably() {
        UUID persistedOne = createPlayerWithProfile(1, SkillLevel.GOOD);
        UUID priorOne = createPlayerWithProfile(2, SkillLevel.WEAK_PLUS);
        UUID persistedTwo = createPlayerWithProfile(3, SkillLevel.WEAK);
        UUID priorTwo = createPlayerWithProfile(
                4,
                SkillLevel.INTERMEDIATE_PLUS
        );
        createPersistedRating(
                persistedOne,
                new BigDecimal("30.125000000"),
                new BigDecimal("6.500000000"),
                12,
                SkillLevel.INTERMEDIATE,
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION
        );
        createPersistedRating(
                persistedTwo,
                new BigDecimal("18.750000000"),
                new BigDecimal("7.000000000"),
                4,
                SkillLevel.WEAK,
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION
        );
        List<UUID> requested = List.of(
                persistedOne,
                priorOne,
                persistedTwo,
                priorTwo
        );

        Map<UUID, MatchmakingRatingSnapshot> result =
                ratingReader.readEffectiveRatings(
                        requested,
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES
                );

        assertThat(result).hasSize(4).containsOnlyKeys(requested);
        assertThat(result.get(persistedOne).ratingBasis())
                .isEqualTo(RatingBasis.PERSISTED);
        assertThat(result.get(persistedTwo).ratingBasis())
                .isEqualTo(RatingBasis.PERSISTED);
        assertThat(result.get(priorOne).ratingValue())
                .isEqualByComparingTo("19.000000000");
        assertThat(result.get(priorTwo).ratingValue())
                .isEqualByComparingTo("31.000000000");
        assertThat(result.get(priorOne).ratingBasis())
                .isEqualTo(RatingBasis.INITIAL_PRIOR);
        assertThat(result.get(priorTwo).ratingBasis())
                .isEqualTo(RatingBasis.INITIAL_PRIOR);
        assertThat(playerRatingRepository.count()).isEqualTo(2);
        assertThat(ratingEventRepository.count()).isZero();
        assertThatThrownBy(() -> result.put(
                UUID.randomUUID(),
                result.get(persistedOne)
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingProfileFailsWholeBatchWithoutCreatingPartialState() {
        UUID profiledPlayerId = createPlayerWithProfile(
                1,
                SkillLevel.INTERMEDIATE
        );
        UUID missingPlayerId = createPlayer(2);

        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                List.of(profiledPlayerId, missingPlayerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isInstanceOfSatisfying(
                MatchmakingRatingResolutionException.class,
                exception -> {
                    assertThat(exception.reason()).isEqualTo(
                            MatchmakingRatingResolutionFailureReason
                                    .MISSING_INITIAL_PRIOR
                    );
                    assertThat(exception.affectedPlayerIds())
                            .containsExactly(missingPlayerId);
                    assertThat(exception.sportCode())
                            .isEqualTo(SportCode.BADMINTON);
                    assertThat(exception.matchFormat())
                            .isEqualTo(MatchFormat.DOUBLES);
                }
        );
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void wrongPersistedAlgorithmFailsWithoutSkillLevelFallback() {
        UUID playerId = createPlayerWithProfile(1, SkillLevel.GOOD);
        createPersistedRating(
                playerId,
                new BigDecimal("22.000000000"),
                new BigDecimal("7.000000000"),
                2,
                SkillLevel.WEAK,
                "legacy-test-v0"
        );
        List<PersistedState> before = persistedStates();

        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                List.of(playerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isInstanceOfSatisfying(
                MatchmakingRatingResolutionException.class,
                exception -> {
                    assertThat(exception.reason()).isEqualTo(
                            MatchmakingRatingResolutionFailureReason
                                    .UNSUPPORTED_PERSISTED_ALGORITHM_VERSION
                    );
                    assertThat(exception.affectedPlayerIds())
                            .containsExactly(playerId);
                }
        );
        assertThat(persistedStates()).isEqualTo(before);
        assertThat(playerRatingRepository.count()).isOne();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void emptyInputReturnsImmutableEmptyResultWithoutWrites() {
        Map<UUID, MatchmakingRatingSnapshot> result =
                ratingReader.readEffectiveRatings(
                        List.of(),
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES
                );

        assertThat(result).isEmpty();
        assertThatThrownBy(() -> result.put(UUID.randomUUID(), null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void duplicateRequestedPlayerIsRejectedExplicitly() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                List.of(playerId, playerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playerIds must not contain duplicates: " + playerId);
    }

    @Test
    void requiredReaderInputsAreValidated() {
        UUID playerId = UUID.randomUUID();

        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                null,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("playerIds are required");
        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                java.util.Arrays.asList(playerId, null),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("playerId is required");
        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                List.of(playerId),
                null,
                MatchFormat.DOUBLES
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("sportCode is required");
        assertThatThrownBy(() -> ratingReader.readEffectiveRatings(
                List.of(playerId),
                SportCode.BADMINTON,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessage("matchFormat is required");
    }

    private static Stream<Arguments> initialPriorCases() {
        return Stream.of(
                Arguments.of(SkillLevel.WEAK, "15.000000000"),
                Arguments.of(SkillLevel.WEAK_PLUS, "19.000000000"),
                Arguments.of(
                        SkillLevel.INTERMEDIATE_MINUS,
                        "23.000000000"
                ),
                Arguments.of(SkillLevel.INTERMEDIATE, "27.000000000"),
                Arguments.of(
                        SkillLevel.INTERMEDIATE_PLUS,
                        "31.000000000"
                ),
                Arguments.of(SkillLevel.GOOD, "35.000000000")
        );
    }

    private UUID createPlayerWithProfile(int number, SkillLevel skillLevel) {
        UUID playerId = createPlayer(number);
        PlayerSportProfile profile = PlayerSportProfile.create(
                playerId,
                SportCode.BADMINTON,
                skillLevel,
                BASE_TIME
        );
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(profile));
        return playerId;
    }

    private UUID createPlayer(int number) {
        UUID playerId = uuid(number);
        Player player = new Player(
                playerId,
                "Rating Player " + number,
                BASE_TIME,
                BASE_TIME
        );
        playerRepository.saveAndFlush(PlayerEntity.from(player));
        return playerId;
    }

    private void createPersistedRating(
            UUID playerId,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            SkillLevel initialSkillLevel,
            String algorithmVersion
    ) {
        playerRatingRepository.saveAndFlush(new PlayerRatingEntity(
                UUID.randomUUID(),
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                ratingValue,
                uncertainty,
                ratedMatches,
                initialSkillLevel,
                algorithmVersion,
                0,
                BASE_TIME,
                BASE_TIME
        ));
    }

    private List<PersistedState> persistedStates() {
        return playerRatingRepository.findAll().stream()
                .map(rating -> new PersistedState(
                        rating.getId(),
                        rating.getPlayerId(),
                        rating.getRatingValue(),
                        rating.getUncertainty(),
                        rating.getRatedMatches(),
                        rating.getAlgorithmVersion(),
                        rating.getUpdatedAt()
                ))
                .sorted((first, second) -> first.playerId().compareTo(
                        second.playerId()
                ))
                .toList();
    }

    private UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record PersistedState(
            UUID id,
            UUID playerId,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            String algorithmVersion,
            Instant updatedAt
    ) {
    }
}

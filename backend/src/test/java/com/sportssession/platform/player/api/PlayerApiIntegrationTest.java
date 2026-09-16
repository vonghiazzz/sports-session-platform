package com.sportssession.platform.player.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingReader;
import com.sportssession.platform.matchmaking.application.MatchmakingSkillLevelReader;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.player.application.PlayerManagementRatingReader;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlayerApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @Autowired
    private MatchmakingSkillLevelReader matchmakingSkillLevelReader;

    @Autowired
    private MatchmakingRatingReader matchmakingRatingReader;

    @MockitoSpyBean
    private PlayerManagementRatingReader playerManagementRatingReader;

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void createPlayerSucceedsAndPersistsPlayerWithSportProfile() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson("Player A")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/players/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.playerCode", org.hamcrest.Matchers.matchesPattern(
                        "P[0-9]{6,}")))
                .andExpect(jsonPath("$.displayName").value("Player A"))
                .andExpect(jsonPath("$.sportProfiles[0].sport").value("BADMINTON"))
                .andExpect(jsonPath("$.sportProfiles[0].skillLevel")
                        .value("INTERMEDIATE"))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingValue")
                        .value(27.0))
                .andExpect(jsonPath("$.sportProfiles[0].rating.uncertainty")
                        .value(8.333333333))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratedMatches")
                        .value(0))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingBasis")
                        .value("INITIAL_PRIOR"))
                .andExpect(jsonPath(
                        "$.sportProfiles[0].rating.ratingAlgorithmVersion"
                ).doesNotExist())
                .andReturn();

        UUID playerId = responsePlayerId(result);
        assertThat(playerRepository.findById(playerId)).isPresent();
        assertThat(profileRepository.findAllByPlayerIdOrderByCreatedAtAsc(playerId))
                .singleElement()
                .satisfies(profile -> {
                    assertThat(profile.getPlayerId()).isEqualTo(playerId);
                    assertThat(profile.getSportCode().name()).isEqualTo("BADMINTON");
                    assertThat(profile.getSkillLevel().name()).isEqualTo("INTERMEDIATE");
                });
    }

    @Test
    void blankDisplayNameIsRejectedWithoutPersistence() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.displayName")
                        .value("displayName must not be blank"));

        assertThat(playerRepository.count()).isZero();
        assertThat(profileRepository.count()).isZero();
    }

    @Test
    void unsupportedSportIsRejected() throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Player A",
                                  "sport": "TENNIS",
                                  "skillLevel": "INTERMEDIATE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Malformed request or unsupported sport/skillLevel value"));
    }

    @Test
    void getExistingPlayerSucceeds() throws Exception {
        UUID playerId = createPlayer("Player B");

        mockMvc.perform(get("/api/players/{playerId}", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId.toString()))
                .andExpect(jsonPath("$.playerCode", org.hamcrest.Matchers.matchesPattern(
                        "P[0-9]{6,}")))
                .andExpect(jsonPath("$.displayName").value("Player B"))
                .andExpect(jsonPath("$.sportProfiles.length()").value(1))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingValue")
                        .value(27.0))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingBasis")
                        .value("INITIAL_PRIOR"));
    }

    @Test
    void getNonexistentPlayerReturnsNotFound() throws Exception {
        UUID missingPlayerId = UUID.randomUUID();

        mockMvc.perform(get("/api/players/{playerId}", missingPlayerId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Player not found: " + missingPlayerId));
    }

    @Test
    void listAndCaseInsensitiveNameSearchReturnMatchingPlayers() throws Exception {
        createPlayer("Lan Anh");
        createPlayer("Minh Khoa");

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].playerCode").isString())
                .andExpect(jsonPath("$[1].playerCode").isString())
                .andExpect(jsonPath("$[0].sportProfiles[0].rating.ratingBasis")
                        .value("INITIAL_PRIOR"))
                .andExpect(jsonPath("$[1].sportProfiles[0].rating.ratingBasis")
                        .value("INITIAL_PRIOR"));

        mockMvc.perform(get("/api/players").queryParam("name", "LAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Lan Anh"));
    }

    @Test
    void listComposesAllBadmintonRatingsThroughOneBatchRead() throws Exception {
        createPlayer("Player One");
        createPlayer("Player Two");
        clearInvocations(playerManagementRatingReader);

        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(playerManagementRatingReader, times(1))
                .readEffectiveRatings(any(), eq(MatchFormat.DOUBLES));
    }

    @Test
    void unratedSkillUpdateChangesEffectivePriorWithoutPersistingRating()
            throws Exception {
        UUID playerId = createPlayer("Unrated Player");

        updateSkillLevel(playerId, "INTERMEDIATE_PLUS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportProfiles[0].skillLevel")
                        .value("INTERMEDIATE_PLUS"))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingValue")
                        .value(31.0))
                .andExpect(jsonPath("$.sportProfiles[0].rating.uncertainty")
                        .value(8.333333333))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratedMatches")
                        .value(0))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingBasis")
                        .value("INITIAL_PRIOR"))
                .andExpect(jsonPath(
                        "$.sportProfiles[0].rating.ratingAlgorithmVersion"
                ).doesNotExist());

        mockMvc.perform(get("/api/players/{playerId}", playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportProfiles[0].skillLevel")
                        .value("INTERMEDIATE_PLUS"))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingValue")
                        .value(31.0));

        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void skillUpdatePreservesMatureRatingAndMatchmakingReadsBothDimensions()
            throws Exception {
        UUID playerId = createPlayer("Rated Player");
        long playerCode = playerRepository.findById(playerId).orElseThrow()
                .getPlayerCode();
        Instant initializedAt = Instant.parse("2026-09-11T01:00:00Z");
        PlayerRatingEntity rating = PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.INTERMEDIATE,
                new RatingState(27.0, 8.333333333),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                initializedAt
        );
        rating.applyRating(
                new RatingState(28.765432109, 7.123456789),
                initializedAt.plusSeconds(1)
        );
        playerRatingRepository.saveAndFlush(rating);
        BigDecimal ratingBefore = rating.getRatingValue();
        BigDecimal uncertaintyBefore = rating.getUncertainty();
        int ratedMatchesBefore = rating.getRatedMatches();
        long eventCountBefore = ratingEventRepository.count();

        updateSkillLevel(playerId, "INTERMEDIATE_PLUS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerCode")
                        .value("P%06d".formatted(playerCode)))
                .andExpect(jsonPath("$.sportProfiles[0].skillLevel")
                        .value("INTERMEDIATE_PLUS"))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingValue")
                        .value(28.765432109))
                .andExpect(jsonPath("$.sportProfiles[0].rating.uncertainty")
                        .value(7.123456789))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratedMatches")
                        .value(1))
                .andExpect(jsonPath("$.sportProfiles[0].rating.ratingBasis")
                        .value("PERSISTED"))
                .andExpect(jsonPath(
                        "$.sportProfiles[0].rating.ratingAlgorithmVersion"
                ).value(WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION));

        PlayerRatingEntity after = playerRatingRepository.findById(rating.getId())
                .orElseThrow();
        assertThat(after.getRatingValue()).isEqualByComparingTo(ratingBefore);
        assertThat(after.getUncertainty())
                .isEqualByComparingTo(uncertaintyBefore);
        assertThat(after.getRatedMatches()).isEqualTo(ratedMatchesBefore);
        assertThat(after.getInitialSkillLevel())
                .isEqualTo(SkillLevel.INTERMEDIATE);
        assertThat(after.getAlgorithmVersion())
                .isEqualTo(WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION);
        assertThat(ratingEventRepository.count()).isEqualTo(eventCountBefore);

        assertThat(matchmakingSkillLevelReader.readSkillLevels(
                List.of(playerId),
                SportCode.BADMINTON
        )).containsEntry(playerId, SkillLevel.INTERMEDIATE_PLUS);
        var matchmakingRating = matchmakingRatingReader.readEffectiveRatings(
                List.of(playerId),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        ).get(playerId);
        assertThat(matchmakingRating.ratingValue())
                .isEqualByComparingTo(ratingBefore);
        assertThat(matchmakingRating.uncertainty())
                .isEqualByComparingTo(uncertaintyBefore);
        assertThat(matchmakingRating.ratedMatches())
                .isEqualTo(ratedMatchesBefore);
        assertThat(matchmakingRating.ratingBasis()).isEqualTo(RatingBasis.PERSISTED);
    }

    @Test
    void invalidSkillLevelIsRejected() throws Exception {
        UUID playerId = createPlayer("Invalid Skill Player");

        updateSkillLevel(playerId, "EXPERT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Malformed request or unsupported sport/skillLevel value"));
    }

    @Test
    void missingSkillLevelIsRejectedByBeanValidation() throws Exception {
        UUID playerId = createPlayer("Missing Skill Player");

        mockMvc.perform(put(
                        "/api/players/{playerId}/sports/{sportCode}/skill-level",
                        playerId,
                        SportCode.BADMINTON
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.skillLevel")
                        .value("skillLevel is required"));
    }

    @Test
    void skillUpdateForUnknownPlayerReturnsNotFound() throws Exception {
        UUID missingPlayerId = UUID.randomUUID();

        updateSkillLevel(missingPlayerId, "GOOD")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Player not found: " + missingPlayerId));
    }

    @Test
    void skillUpdateForMissingProfileReturnsNotFound() throws Exception {
        Player player = Player.create("Profileless Player", Instant.now());
        playerRepository.saveAndFlush(PlayerEntity.from(player));

        updateSkillLevel(player.id(), "GOOD")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Player sport profile not found: " + player.id()
                                + " / BADMINTON"
                ));
    }

    @Test
    void duplicateDisplayNamesAreAllowed() throws Exception {
        UUID firstPlayerId = createPlayer("Same Name");
        UUID secondPlayerId = createPlayer("Same Name");

        assertThat(playerRepository.count()).isEqualTo(2);
        assertThat(profileRepository.count()).isEqualTo(2);
        assertThat(List.of(
                playerRepository.findById(firstPlayerId).orElseThrow()
                        .getPlayerCode(),
                playerRepository.findById(secondPlayerId).orElseThrow()
                        .getPlayerCode()
        )).doesNotHaveDuplicates();
    }

    @Test
    void playerCodeSearchAcceptsFormattedLowercaseAndNumericForms()
            throws Exception {
        UUID playerId = createPlayer("Searchable Player");
        long playerCode = playerRepository.findById(playerId).orElseThrow()
                .getPlayerCode();
        String formattedCode = "P%06d".formatted(playerCode);

        for (String search : List.of(
                formattedCode,
                formattedCode.toLowerCase(),
                formattedCode.substring(1)
        )) {
            mockMvc.perform(get("/api/players").queryParam("name", search))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(playerId.toString()))
                    .andExpect(jsonPath("$[0].playerCode")
                            .value(formattedCode));
        }
    }

    @Test
    void clientSuppliedPlayerCodeCannotOverrideSystemGeneratedCode()
            throws Exception {
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "System Code Owner",
                                  "playerCode": "P999999",
                                  "sport": "BADMINTON",
                                  "skillLevel": "INTERMEDIATE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.playerCode")
                        .value(org.hamcrest.Matchers.not("P999999")));
    }

    @Test
    void concurrentPlayerCreationsReceiveDistinctGlobalCodes()
            throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = executor.submit(() ->
                    createPlayerAfterGate("Concurrent Name", ready, start));
            Future<UUID> second = executor.submit(() ->
                    createPlayerAfterGate("Concurrent Name", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> codes = List.of(
                    playerRepository.findById(
                            first.get(15, TimeUnit.SECONDS)
                    ).orElseThrow().getPlayerCode(),
                    playerRepository.findById(
                            second.get(15, TimeUnit.SECONDS)
                    ).orElseThrow().getPlayerCode()
            );
            assertThat(codes)
                    .allMatch(code -> code > 0)
                    .doesNotHaveDuplicates();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void playerCodesRemainValidWhenTheDatabaseSequenceHasAGap()
            throws Exception {
        long unusedCode = jdbcTemplate.queryForObject(
                "SELECT nextval('players_player_code_seq')",
                Long.class
        );

        UUID playerId = createPlayer("Player After Gap");
        long assignedCode = playerRepository.findById(playerId).orElseThrow()
                .getPlayerCode();

        assertThat(assignedCode).isGreaterThan(unusedCode);
        assertThat(playerRepository.findAll())
                .noneMatch(player -> player.getPlayerCode() == unusedCode);
    }

    private UUID createPlayerAfterGate(
            String displayName,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start gate timeout");
        }
        return createPlayer(displayName);
    }

    private UUID createPlayer(String displayName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPlayerJson(displayName)))
                .andExpect(status().isCreated())
                .andReturn();
        return responsePlayerId(result);
    }

    private org.springframework.test.web.servlet.ResultActions updateSkillLevel(
            UUID playerId,
            String skillLevel
    ) throws Exception {
        return mockMvc.perform(put(
                        "/api/players/{playerId}/sports/{sportCode}/skill-level",
                        playerId,
                        SportCode.BADMINTON
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skillLevel\":\"" + skillLevel + "\"}"));
    }

    private UUID responsePlayerId(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return UUID.fromString(response.get("id").asText());
    }

    private String validPlayerJson(String displayName) throws Exception {
        return objectMapper.writeValueAsString(new CreatePlayerRequest(
                displayName,
                com.sportssession.platform.shared.domain.SportCode.BADMINTON,
                com.sportssession.platform.player.domain.SkillLevel.INTERMEDIATE));
    }
}

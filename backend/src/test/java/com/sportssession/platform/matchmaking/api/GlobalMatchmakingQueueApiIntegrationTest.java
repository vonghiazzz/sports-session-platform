package com.sportssession.platform.matchmaking.api;

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
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationQueueService;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationQueueService;
import com.sportssession.platform.matchmaking.application.SubmittedGlobalCourtRecommendation;
import com.sportssession.platform.matchmaking.application.SubmittedGlobalRecommendationEvidence;
import com.sportssession.platform.matchmaking.application.SubmittedRecommendationAssignment;
import com.sportssession.platform.matchmaking.application.SubmittedRecommendationEvidence;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchplan.application.CreateMatchPlanCommand;
import com.sportssession.platform.matchplan.application.MatchPlanAssignment;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import com.sportssession.platform.matchplan.domain.MatchPlanConflictException;
import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantRepository;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GlobalMatchmakingQueueApiIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final String PREVIEW_ENDPOINT =
            "/api/sessions/{sessionId}/match-recommendations";
    private static final String QUEUE_ENDPOINT = PREVIEW_ENDPOINT + "/queue";
    private static final Instant BASE_TIME =
            Instant.parse("2026-09-11T01:00:00Z");
    private static final Instant OPERATION_TIME =
            Instant.parse("2026-09-11T04:00:00Z");

    @TestBean(name = "clock", enforceOverride = true)
    private Clock clock;

    @MockitoSpyBean
    private MatchPlanService matchPlanService;

    @Autowired
    private GlobalMatchmakingRecommendationService previewService;

    @Autowired
    private GlobalMatchmakingRecommendationQueueService globalQueueService;

    @Autowired
    private MatchmakingRecommendationQueueService singleQueueService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchPlanParticipantRepository matchPlanParticipantRepository;

    @Autowired
    private MatchPlanRepository matchPlanRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @Autowired
    private PlayerRepository playerRepository;

    private static Clock clock() {
        return Clock.fixed(OPERATION_TIME, ZoneOffset.UTC);
    }

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchPlanParticipantRepository.deleteAll();
        matchPlanRepository.deleteAll();
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
    void validEightPlayerPreviewQueuesTwoAuthoritativeDisjointPlans()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());

        MvcResult result = queue(fixture.sessionId(), queueBody(preview))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$.orchestrationVersion").value(
                        GlobalMatchmakingRecommendationService
                                .ORCHESTRATION_VERSION
                ))
                .andExpect(jsonPath("$.selectionAlgorithmVersion").value(
                        MatchmakingEngine.ALGORITHM_VERSION
                ))
                .andExpect(jsonPath("$.createdPlans.length()").value(2))
                .andExpect(jsonPath("$.createdPlans[0].sessionCourtId")
                        .value(fixture.sessionCourtIds().get(0).toString()))
                .andExpect(jsonPath("$.createdPlans[1].sessionCourtId")
                        .value(fixture.sessionCourtIds().get(1).toString()))
                .andExpect(jsonPath("$.createdPlans[0].source")
                        .value("RECOMMENDATION"))
                .andExpect(jsonPath("$.createdPlans[1].source")
                        .value("RECOMMENDATION"))
                .andExpect(jsonPath("$.createdPlans[0].queuePosition").value(1))
                .andExpect(jsonPath("$.createdPlans[1].queuePosition").value(1))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        List<UUID> persistedParticipants = new ArrayList<>();
        response.path("createdPlans").forEach(plan -> {
            assertThat(plan.path("participants")).hasSize(4);
            plan.path("participants").forEach(participant ->
                    persistedParticipants.add(UUID.fromString(
                            participant.path("sessionParticipantId").asText()
                    ))
            );
        });
        assertThat(persistedParticipants)
                .containsExactlyInAnyOrderElementsOf(fixture.participantIds())
                .doesNotHaveDuplicates();
        assertRuntimeUnchanged(fixture);
        assertThat(matchPlanRepository.count()).isEqualTo(2);
        assertThat(matchPlanParticipantRepository.count()).isEqualTo(8);
    }

    @Test
    void previewWithOneRecommendationAndOneUnavailableQueuesOnePlan()
            throws Exception {
        Fixture fixture = fixture(2, 4);
        JsonNode preview = preview(fixture.sessionId());
        assertThat(preview.path("courtResults")).hasSize(2);
        assertThat(preview.at("/courtResults/1/outcome").asText())
                .isEqualTo("UNAVAILABLE");

        queue(fixture.sessionId(), queueBody(preview))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdPlans.length()").value(1));

        assertThat(matchPlanRepository.count()).isEqualTo(1);
        assertThat(matchPlanParticipantRepository.count()).isEqualTo(4);
        assertRuntimeUnchanged(fixture);
    }

    @Test
    void participantLeavingMakesWholeBatchStale() throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        SessionParticipantEntity participant = participantRepository.findById(
                fixture.participantIds().getFirst()
        ).orElseThrow();
        participant.applyRuntimeState(
                participant.toDomain().leave(OPERATION_TIME.plusSeconds(1))
        );
        participantRepository.saveAndFlush(participant);

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
    }

    @Test
    void queuedPlanAppearingMakesWholeBatchStale() throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        matchPlanService.create(new CreateMatchPlanCommand(
                fixture.sessionId(),
                fixture.sessionCourtIds().getFirst(),
                planAssignments(fixture.participantIds().subList(0, 4))
        ));

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 1);
    }

    @Test
    void createdMatchAppearingMakesWholeBatchStale() throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        matchRepository.saveAndFlush(MatchEntity.from(Match.create(
                fixture.sessionId(),
                fixture.sessionCourtIds().getFirst(),
                MatchSource.MANUAL,
                OPERATION_TIME.plusSeconds(1)
        )));

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
        assertThat(matchRepository.count()).isEqualTo(1);
    }

    @Test
    void courtBecomingPlayingMakesWholeBatchStale() throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        SessionCourtEntity court = sessionCourtRepository.findById(
                fixture.sessionCourtIds().getFirst()
        ).orElseThrow();
        court.applyRuntimeState(
                court.toDomain().startMatch(OPERATION_TIME.plusSeconds(1))
        );
        sessionCourtRepository.saveAndFlush(court);

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
    }

    @Test
    void completedMatchChangingSessionCountsMakesBatchStale()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        List<UUID> firstGroup = recommendedParticipantIds(
                preview.path("courtResults").get(0)
        );
        persistCompletedMatch(
                fixture.sessionId(),
                fixture.sessionCourtIds().getFirst(),
                firstGroup
        );

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
        assertThat(matchRepository.count()).isEqualTo(1);
    }

    @Test
    void ratingChangeThatAltersTeamAssignmentMakesBatchStale()
            throws Exception {
        Fixture fixture = fixture(1, 4);
        JsonNode preview = preview(fixture.sessionId());
        JsonNode recommendation = preview.path("courtResults").get(0);
        for (JsonNode assignment : recommendation.path("teamA")) {
            createRating(playerId(assignment), 40.0);
        }
        for (JsonNode assignment : recommendation.path("teamB")) {
            createRating(playerId(assignment), 10.0);
        }

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
    }

    @Test
    void skillLevelChangeThatAltersSelectedGroupMakesBatchStale()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        List<UUID> selected = recommendedParticipantIds(
                preview.path("courtResults").get(0)
        );
        UUID oldestId = fixture.participantIds().getFirst();
        UUID participantToChange = selected.stream()
                .filter(id -> !id.equals(oldestId))
                .findFirst()
                .orElseThrow();
        UUID playerId = participantRepository.findById(participantToChange)
                .orElseThrow()
                .getPlayerId();
        List<PlayerSportProfileEntity> profiles = profileRepository
                .findAllByPlayerIdOrderByCreatedAtAsc(playerId);
        profileRepository.deleteAll(profiles);
        profileRepository.flush();
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(
                PlayerSportProfile.create(
                        playerId,
                        SportCode.BADMINTON,
                        SkillLevel.WEAK,
                        OPERATION_TIME.plusSeconds(1)
                )
        ));

        assertStaleWithoutNewPlans(fixture.sessionId(), queueBody(preview), 0);
    }

    @Test
    void wrongVersionsAndCourtFromAnotherSessionAreStale()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());

        Map<String, Object> wrongOrchestration = queueBody(preview);
        wrongOrchestration.put("orchestrationVersion", "old-global-version");
        assertStaleWithoutNewPlans(
                fixture.sessionId(), wrongOrchestration, 0
        );

        Map<String, Object> wrongSelection = queueBody(preview);
        wrongSelection.put("selectionAlgorithmVersion", "old-selection");
        assertStaleWithoutNewPlans(fixture.sessionId(), wrongSelection, 0);

        UUID otherSessionId = createSession(fixture.venueId());
        UUID otherSessionCourtId = addSessionCourt(
                fixture.venueId(), otherSessionId, BASE_TIME.plusSeconds(500)
        );
        Map<String, Object> wrongSessionCourt = queueBody(preview);
        recommendations(wrongSessionCourt).getFirst().put(
                "sessionCourtId", otherSessionCourtId
        );
        targetCourtIds(wrongSessionCourt).set(
                0,
                otherSessionCourtId.toString()
        );
        assertStaleWithoutNewPlans(
                fixture.sessionId(), wrongSessionCourt, 0
        );
    }

    @Test
    void malformedBatchEvidenceReturnsBadRequestBeforePersistence()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());

        Map<String, Object> duplicateCourt = queueBody(preview);
        List<Map<String, Object>> duplicateCourtRecommendations =
                recommendations(duplicateCourt);
        duplicateCourtRecommendations.get(1).put(
                "sessionCourtId",
                duplicateCourtRecommendations.getFirst().get("sessionCourtId")
        );
        assertBadRequestWithoutPlans(fixture.sessionId(), duplicateCourt);

        Map<String, Object> duplicateParticipant = queueBody(preview);
        List<Map<String, Object>> duplicateParticipantRecommendations =
                recommendations(duplicateParticipant);
        assignments(duplicateParticipantRecommendations.get(1)).getFirst()
                .put(
                        "sessionParticipantId",
                        assignments(duplicateParticipantRecommendations
                                .getFirst()).getFirst()
                                .get("sessionParticipantId")
                );
        assertBadRequestWithoutPlans(fixture.sessionId(), duplicateParticipant);

        Map<String, Object> duplicateSlot = queueBody(preview);
        List<Map<String, Object>> slots = assignments(
                recommendations(duplicateSlot).getFirst()
        );
        slots.get(1).put("teamSide", slots.getFirst().get("teamSide"));
        slots.get(1).put("teamSlot", slots.getFirst().get("teamSlot"));
        assertBadRequestWithoutPlans(fixture.sessionId(), duplicateSlot);

        Map<String, Object> missingParticipant = queueBody(preview);
        assignments(recommendations(missingParticipant).getFirst())
                .getFirst().put("sessionParticipantId", null);
        assertBadRequestWithoutPlans(fixture.sessionId(), missingParticipant);

        Map<String, Object> incomplete = queueBody(preview);
        assignments(recommendations(incomplete).getFirst()).removeLast();
        assertBadRequestWithoutPlans(fixture.sessionId(), incomplete);

        Map<String, Object> invalidSide = queueBody(preview);
        assignments(recommendations(invalidSide).getFirst())
                .getFirst().put("teamSide", "C");
        assertBadRequestWithoutPlans(fixture.sessionId(), invalidSide);

        Map<String, Object> empty = queueBody(preview);
        empty.put("recommendations", List.of());
        assertBadRequestWithoutPlans(fixture.sessionId(), empty);
    }

    @Test
    void secondPlanFailureRollsBackFirstPlan() throws Exception {
        Fixture fixture = fixture(2, 8);
        JsonNode preview = preview(fixture.sessionId());
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new MatchPlanConflictException("forced second failure");
            }
            return invocation.callRealMethod();
        }).when(matchPlanService).createRecommended(any());

        queue(fixture.sessionId(), queueBody(preview))
                .andExpect(status().isConflict());

        assertThat(calls).hasValue(2);
        assertThat(matchPlanRepository.count()).isZero();
        assertThat(matchPlanParticipantRepository.count()).isZero();
    }

    @Test
    void concurrentGlobalAndSingleQueueHaveExactlyOneWinner()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        var globalPreview = previewService.preview(fixture.sessionId());
        SubmittedGlobalRecommendationEvidence globalEvidence =
                globalEvidence(globalPreview);
        var firstRecommendation = globalPreview.courtResults().stream()
                .filter(com.sportssession.platform.matchmaking.domain
                        .MatchRecommendation.class::isInstance)
                .map(com.sportssession.platform.matchmaking.domain
                        .MatchRecommendation.class::cast)
                .findFirst()
                .orElseThrow();
        SubmittedRecommendationEvidence singleEvidence =
                new SubmittedRecommendationEvidence(
                        MatchmakingEngine.ALGORITHM_VERSION,
                        firstRecommendationPlayers(firstRecommendation)
                );

        List<Boolean> results = runConcurrently(
                () -> globalQueueService.addAllToQueue(
                        fixture.sessionId(), globalEvidence
                ),
                () -> singleQueueService.addToQueue(
                        fixture.sessionId(),
                        firstRecommendation.sessionCourtId(),
                        singleEvidence
                )
        );

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertQueuedOwnershipUnique(fixture.sessionId());
        assertThat(matchPlanRepository.count()).isBetween(1L, 2L);
    }

    @Test
    void concurrentQueueAllRequestsHaveExactlyOneWinner()
            throws Exception {
        Fixture fixture = fixture(2, 8);
        SubmittedGlobalRecommendationEvidence evidence = globalEvidence(
                previewService.preview(fixture.sessionId())
        );

        List<Boolean> results = runConcurrently(
                () -> globalQueueService.addAllToQueue(
                        fixture.sessionId(), evidence
                ),
                () -> globalQueueService.addAllToQueue(
                        fixture.sessionId(), evidence
                )
        );

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(matchPlanRepository.count()).isEqualTo(2);
        assertThat(matchPlanParticipantRepository.count()).isEqualTo(8);
        assertQueuedOwnershipUnique(fixture.sessionId());
    }

    private Fixture fixture(int courtCount, int playerCount) {
        Venue venue = Venue.create(
                "Global Queue Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        UUID venueId = venueRepository.saveAndFlush(VenueEntity.from(venue))
                .getId();
        UUID sessionId = createSession(venueId);
        List<UUID> sessionCourtIds = new ArrayList<>();
        for (int index = 0; index < courtCount; index++) {
            sessionCourtIds.add(addSessionCourt(
                    venueId,
                    sessionId,
                    BASE_TIME.plusSeconds(100L + index)
            ));
        }
        List<UUID> participantIds = new ArrayList<>();
        List<UUID> playerIds = new ArrayList<>();
        for (int index = 0; index < playerCount; index++) {
            UUID playerId = uuid(1_000 + index);
            playerIds.add(playerId);
            playerRepository.saveAndFlush(PlayerEntity.from(new Player(
                    playerId,
                    "Global Queue Player " + index,
                    BASE_TIME,
                    BASE_TIME
            )));
            profileRepository.saveAndFlush(PlayerSportProfileEntity.from(
                    PlayerSportProfile.create(
                            playerId,
                            SportCode.BADMINTON,
                            SkillLevel.INTERMEDIATE,
                            BASE_TIME
                    )
            ));
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    BASE_TIME.plusSeconds(10L + index)
            ).checkIn(BASE_TIME.plusSeconds(20L + index));
            participantIds.add(participantRepository.saveAndFlush(
                    SessionParticipantEntity.from(participant)
            ).getId());
        }
        return new Fixture(
                venueId,
                sessionId,
                List.copyOf(sessionCourtIds),
                List.copyOf(participantIds),
                List.copyOf(playerIds)
        );
    }

    private UUID createSession(UUID venueId) {
        Session session = Session.create(
                venueId,
                "Global Queue Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plusSeconds(100),
                BASE_TIME.plusSeconds(10_000),
                BASE_TIME
        ).start(BASE_TIME.plusSeconds(1));
        return sessionRepository.saveAndFlush(SessionEntity.from(session))
                .getId();
    }

    private UUID addSessionCourt(
            UUID venueId,
            UUID sessionId,
            Instant addedAt
    ) {
        Court court = Court.create(
                venueId,
                "Global Queue Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        UUID courtId = courtRepository.saveAndFlush(CourtEntity.from(court))
                .getId();
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                addedAt
        );
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private JsonNode preview(UUID sessionId) throws Exception {
        MvcResult result = mockMvc.perform(post(PREVIEW_ENDPOINT, sessionId))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions queue(
            UUID sessionId,
            Object body
    ) throws Exception {
        return mockMvc.perform(post(QUEUE_ENDPOINT, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> queueBody(JsonNode preview) {
        List<String> targetCourtIds = new ArrayList<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();
        preview.path("courtResults").forEach(result -> {
            targetCourtIds.add(result.path("sessionCourtId").asText());
            if ("RECOMMENDED".equals(result.path("outcome").asText())) {
                recommendations.add(new LinkedHashMap<>(Map.of(
                        "sessionCourtId", result.path("sessionCourtId").asText(),
                        "assignments", mutableAssignments(result)
                )));
            }
        });
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(
                "orchestrationVersion",
                preview.path("orchestrationVersion").asText()
        );
        body.put(
                "selectionAlgorithmVersion",
                preview.path("selectionAlgorithmVersion").asText()
        );
        body.put("targetCourtIds", targetCourtIds);
        body.put("recommendations", recommendations);
        return body;
    }

    private List<Map<String, Object>> mutableAssignments(JsonNode result) {
        List<Map<String, Object>> assignments = new ArrayList<>();
        List.of(
                result.at("/teamA/slot1"),
                result.at("/teamA/slot2"),
                result.at("/teamB/slot1"),
                result.at("/teamB/slot2")
        ).forEach(player -> assignments.add(new LinkedHashMap<>(Map.of(
                "sessionParticipantId",
                player.path("sessionParticipantId").asText(),
                "teamSide",
                player.path("teamSide").asText(),
                "teamSlot",
                player.path("teamSlot").asInt()
        ))));
        return assignments;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recommendations(
            Map<String, Object> body
    ) {
        return (List<Map<String, Object>>) body.get("recommendations");
    }

    @SuppressWarnings("unchecked")
    private List<String> targetCourtIds(
            Map<String, Object> body
    ) {
        return (List<String>) body.get("targetCourtIds");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> assignments(
            Map<String, Object> recommendation
    ) {
        return (List<Map<String, Object>>) recommendation.get("assignments");
    }

    private void assertStaleWithoutNewPlans(
            UUID sessionId,
            Object body,
            long expectedExistingPlans
    ) throws Exception {
        queue(sessionId, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Submitted global recommendation is stale"));
        assertThat(matchPlanRepository.count())
                .isEqualTo(expectedExistingPlans);
    }

    private void assertBadRequestWithoutPlans(
            UUID sessionId,
            Object body
    ) throws Exception {
        queue(sessionId, body).andExpect(status().isBadRequest());
        assertThat(matchPlanRepository.count()).isZero();
    }

    private void assertRuntimeUnchanged(Fixture fixture) {
        assertThat(sessionRepository.findById(fixture.sessionId()))
                .get()
                .extracting(SessionEntity::getStatus)
                .isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(participantRepository
                .findAllBySessionIdOrderByJoinedAtAscIdAsc(fixture.sessionId()))
                .extracting(SessionParticipantEntity::getStatus)
                .containsOnly(ParticipantStatus.WAITING);
        assertThat(sessionCourtRepository
                .findAllBySessionIdOrderByAddedAtAscIdAsc(fixture.sessionId()))
                .extracting(SessionCourtEntity::getStatus)
                .containsOnly(SessionCourtStatus.AVAILABLE);
        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    private List<MatchPlanAssignment> planAssignments(List<UUID> ids) {
        return List.of(
                new MatchPlanAssignment(ids.get(0), TeamSide.A, 1),
                new MatchPlanAssignment(ids.get(1), TeamSide.A, 2),
                new MatchPlanAssignment(ids.get(2), TeamSide.B, 1),
                new MatchPlanAssignment(ids.get(3), TeamSide.B, 2)
        );
    }

    private void persistCompletedMatch(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
        Match match = Match.create(
                sessionId,
                sessionCourtId,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(300)
        ).start(BASE_TIME.plusSeconds(301)).complete(
                new MatchResult(TeamSide.A, 21, 15),
                BASE_TIME.plusSeconds(302)
        );
        matchRepository.saveAndFlush(MatchEntity.from(match));
        List<TeamSide> sides = List.of(
                TeamSide.A, TeamSide.A, TeamSide.B, TeamSide.B
        );
        for (int index = 0; index < participantIds.size(); index++) {
            matchParticipantRepository.saveAndFlush(
                    MatchParticipantEntity.from(MatchParticipant.assign(
                            match.id(),
                            participantIds.get(index),
                            sides.get(index),
                            index % 2 + 1
                    ))
            );
        }
    }

    private void createRating(UUID playerId, double ratingValue) {
        playerRatingRepository.saveAndFlush(PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.INTERMEDIATE,
                new RatingState(ratingValue, 8.0),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                OPERATION_TIME.plusSeconds(1)
        ));
    }

    private UUID playerId(JsonNode recommendedPlayer) {
        UUID participantId = UUID.fromString(
                recommendedPlayer.path("sessionParticipantId").asText()
        );
        return participantRepository.findById(participantId)
                .orElseThrow()
                .getPlayerId();
    }

    private List<UUID> recommendedParticipantIds(JsonNode recommendation) {
        return List.of(
                recommendation.at("/teamA/slot1"),
                recommendation.at("/teamA/slot2"),
                recommendation.at("/teamB/slot1"),
                recommendation.at("/teamB/slot2")
        ).stream().map(player -> UUID.fromString(
                player.path("sessionParticipantId").asText()
        )).toList();
    }

    private SubmittedGlobalRecommendationEvidence globalEvidence(
            com.sportssession.platform.matchmaking.application
                    .GlobalMatchmakingPreview preview
    ) {
        List<SubmittedGlobalCourtRecommendation> recommendations = preview
                .courtResults()
                .stream()
                .filter(com.sportssession.platform.matchmaking.domain
                        .MatchRecommendation.class::isInstance)
                .map(com.sportssession.platform.matchmaking.domain
                        .MatchRecommendation.class::cast)
                .map(recommendation -> new SubmittedGlobalCourtRecommendation(
                        recommendation.sessionCourtId(),
                        firstRecommendationPlayers(recommendation)
                ))
                .toList();
        List<UUID> targetCourtIds = preview.courtResults()
                .stream()
                .map(com.sportssession.platform.matchmaking.domain
                        .MatchmakingResult::sessionCourtId)
                .toList();
        return new SubmittedGlobalRecommendationEvidence(
                preview.orchestrationVersion(),
                preview.selectionAlgorithmVersion(),
                targetCourtIds,
                recommendations
        );
    }

    private List<SubmittedRecommendationAssignment> firstRecommendationPlayers(
            com.sportssession.platform.matchmaking.domain
                    .MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        ).stream().map(player -> new SubmittedRecommendationAssignment(
                player.sessionParticipantId(),
                player.teamSide(),
                player.teamSlot()
        )).toList();
    }

    private List<Boolean> runConcurrently(
            ThrowingOperation first,
            ThrowingOperation second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> runAfterGate(
                    first, ready, start
            ));
            Future<Boolean> secondResult = executor.submit(() -> runAfterGate(
                    second, ready, start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstResult.get(20, TimeUnit.SECONDS),
                    secondResult.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean runAfterGate(
            ThrowingOperation operation,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start gate timeout");
        }
        try {
            operation.run();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void assertQueuedOwnershipUnique(UUID sessionId) {
        List<UUID> participantIds = matchPlanRepository
                .findParticipantIdsBySessionAndStatus(
                        sessionId,
                        MatchPlanStatus.QUEUED
                );
        assertThat(participantIds).doesNotHaveDuplicates();
        assertThat(participantIds)
                .hasSize((int) matchPlanParticipantRepository.count());
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }

    private record Fixture(
            UUID venueId,
            UUID sessionId,
            List<UUID> sessionCourtIds,
            List<UUID> participantIds,
            List<UUID> playerIds
    ) {
    }
}

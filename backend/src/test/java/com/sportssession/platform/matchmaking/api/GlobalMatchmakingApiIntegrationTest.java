package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantRepository;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
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
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GlobalMatchmakingApiIntegrationTest extends PostgreSqlIntegrationTest {

    private static final String ENDPOINT =
            "/api/sessions/{sessionId}/match-recommendations";
    private static final Instant BASE_TIME =
            Instant.parse("2026-09-11T01:00:00Z");
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-11T03:00:00Z");

    @TestBean(name = "clock", enforceOverride = true)
    private Clock clock;

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
        return Clock.fixed(EVALUATION_TIME, ZoneOffset.UTC);
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
    void eightWaitingPlayersAndTwoCourtsReturnTwoDisjointReadOnlyPreviews()
            throws Exception {
        Fixture fixture = fixture(8);
        UUID firstCourt = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(10)
        );
        UUID secondCourt = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(20)
        );

        MvcResult result = mockMvc.perform(post(ENDPOINT, fixture.sessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.orchestrationVersion").value(
                        GlobalMatchmakingRecommendationService
                                .ORCHESTRATION_VERSION
                ))
                .andExpect(jsonPath("$.selectionAlgorithmVersion").value(
                        MatchmakingEngine.ALGORITHM_VERSION
                ))
                .andExpect(jsonPath("$.evaluationTime").value(
                        EVALUATION_TIME.toString()
                ))
                .andExpect(jsonPath("$.initialEligiblePlayerCount").value(8))
                .andExpect(jsonPath("$.courtResults.length()").value(2))
                .andExpect(jsonPath("$.courtResults[0].sessionCourtId")
                        .value(firstCourt.toString()))
                .andExpect(jsonPath("$.courtResults[1].sessionCourtId")
                        .value(secondCourt.toString()))
                .andExpect(jsonPath("$.courtResults[0].outcome")
                        .value("RECOMMENDED"))
                .andExpect(jsonPath("$.courtResults[1].outcome")
                        .value("RECOMMENDED"))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertThat(recommendedParticipantIds(response))
                .containsExactlyInAnyOrderElementsOf(fixture.participantIds())
                .doesNotHaveDuplicates();
        assertReadOnlyState(fixture, 2, 0, 0);
    }

    @Test
    void excludesUnavailableAndAlreadyPlannedCourtsAndQueuedPlayers()
            throws Exception {
        Fixture fixture = fixture(8);
        UUID firstTarget = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(50)
        );
        UUID secondTarget = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(60)
        );
        addSessionCourt(
                fixture,
                SessionCourtStatus.PLAYING,
                BASE_TIME.plusSeconds(1)
        );
        addSessionCourt(
                fixture,
                SessionCourtStatus.UNAVAILABLE,
                BASE_TIME.plusSeconds(2)
        );
        UUID queuedCourt = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(3)
        );
        UUID createdMatchCourt = addSessionCourt(
                fixture,
                SessionCourtStatus.AVAILABLE,
                BASE_TIME.plusSeconds(4)
        );
        queuePlan(
                fixture.sessionId(),
                queuedCourt,
                fixture.participantIds().subList(0, 4)
        );
        matchRepository.saveAndFlush(MatchEntity.from(Match.create(
                fixture.sessionId(),
                createdMatchCourt,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(100)
        )));

        MvcResult result = mockMvc.perform(post(ENDPOINT, fixture.sessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.initialEligiblePlayerCount").value(4))
                .andExpect(jsonPath("$.courtResults.length()").value(2))
                .andExpect(jsonPath("$.courtResults[0].sessionCourtId")
                        .value(firstTarget.toString()))
                .andExpect(jsonPath("$.courtResults[0].outcome")
                        .value("RECOMMENDED"))
                .andExpect(jsonPath("$.courtResults[1].sessionCourtId")
                        .value(secondTarget.toString()))
                .andExpect(jsonPath("$.courtResults[1].outcome")
                        .value("UNAVAILABLE"))
                .andExpect(jsonPath("$.courtResults[1].eligiblePlayerCount")
                        .value(0))
                .andExpect(jsonPath("$.courtResults[1].reason").value(
                        "INSUFFICIENT_ELIGIBLE_PLAYERS"
                ))
                .andReturn();

        JsonNode response = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertThat(recommendedParticipantIds(response))
                .containsExactlyInAnyOrderElementsOf(
                        fixture.participantIds().subList(4, 8)
                );
        assertReadOnlyState(fixture, 6, 1, 1);
        assertThat(matchPlanParticipantRepository.count()).isEqualTo(4);
    }

    @Test
    void noEligibleTargetCourtReturnsNormalUnavailableResponse()
            throws Exception {
        Fixture fixture = fixture(4);
        addSessionCourt(
                fixture,
                SessionCourtStatus.PLAYING,
                BASE_TIME.plusSeconds(1)
        );

        mockMvc.perform(post(ENDPOINT, fixture.sessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.reason")
                        .value("NO_ELIGIBLE_COURTS"))
                .andExpect(jsonPath("$.courtResults").isEmpty());

        assertReadOnlyState(fixture, 1, 0, 0);
    }

    private Fixture fixture(int waitingPlayerCount) {
        Venue venue = Venue.create(
                "Global Matchmaking Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        UUID venueId = venueRepository.saveAndFlush(VenueEntity.from(venue))
                .getId();
        Session session = Session.create(
                venueId,
                "Global Matchmaking Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plusSeconds(100),
                BASE_TIME.plusSeconds(10_000),
                BASE_TIME
        ).start(BASE_TIME.plusSeconds(1));
        UUID sessionId = sessionRepository.saveAndFlush(
                SessionEntity.from(session)
        ).getId();

        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < waitingPlayerCount; index++) {
            UUID playerId = uuid(1_000 + index);
            Player player = new Player(
                    playerId,
                    "Global Player " + index,
                    BASE_TIME,
                    BASE_TIME
            );
            playerRepository.saveAndFlush(PlayerEntity.from(player));
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
        return new Fixture(venueId, sessionId, List.copyOf(participantIds));
    }

    private UUID addSessionCourt(
            Fixture fixture,
            SessionCourtStatus status,
            Instant addedAt
    ) {
        Court court = Court.create(
                fixture.venueId(),
                "Global Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        UUID physicalCourtId = courtRepository.saveAndFlush(
                CourtEntity.from(court)
        ).getId();
        SessionCourt sessionCourt = SessionCourt.allocate(
                fixture.sessionId(),
                physicalCourtId,
                addedAt
        );
        sessionCourt = switch (status) {
            case AVAILABLE -> sessionCourt;
            case PLAYING -> sessionCourt.startMatch(addedAt.plusSeconds(1));
            case UNAVAILABLE -> sessionCourt.disable(addedAt.plusSeconds(1));
        };
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private void queuePlan(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
        MatchPlan plan = MatchPlan.queueManual(
                sessionId,
                sessionCourtId,
                1,
                BASE_TIME.plusSeconds(200)
        );
        matchPlanRepository.saveAndFlush(MatchPlanEntity.from(plan));
        for (int index = 0; index < participantIds.size(); index++) {
            TeamSide teamSide = index < 2 ? TeamSide.A : TeamSide.B;
            int teamSlot = index % 2 + 1;
            matchPlanParticipantRepository.saveAndFlush(
                    MatchPlanParticipantEntity.from(
                            MatchPlanParticipant.assign(
                                    plan.id(),
                                    sessionId,
                                    participantIds.get(index),
                                    teamSide,
                                    teamSlot
                            )
                    )
            );
        }
    }

    private List<UUID> recommendedParticipantIds(JsonNode response) {
        List<UUID> participantIds = new ArrayList<>();
        for (JsonNode courtResult : response.path("courtResults")) {
            if (!"RECOMMENDED".equals(courtResult.path("outcome").asText())) {
                continue;
            }
            participantIds.add(UUID.fromString(courtResult.at(
                    "/teamA/slot1/sessionParticipantId"
            ).asText()));
            participantIds.add(UUID.fromString(courtResult.at(
                    "/teamA/slot2/sessionParticipantId"
            ).asText()));
            participantIds.add(UUID.fromString(courtResult.at(
                    "/teamB/slot1/sessionParticipantId"
            ).asText()));
            participantIds.add(UUID.fromString(courtResult.at(
                    "/teamB/slot2/sessionParticipantId"
            ).asText()));
        }
        return List.copyOf(participantIds);
    }

    private void assertReadOnlyState(
            Fixture fixture,
            long expectedSessionCourts,
            long expectedMatchPlans,
            long expectedMatches
    ) {
        assertThat(sessionRepository.findById(fixture.sessionId()))
                .get()
                .extracting(SessionEntity::getStatus)
                .isEqualTo(com.sportssession.platform.session.domain
                        .SessionStatus.IN_PROGRESS);
        assertThat(participantRepository
                .findAllBySessionIdOrderByJoinedAtAscIdAsc(fixture.sessionId()))
                .extracting(SessionParticipantEntity::getStatus)
                .containsOnly(ParticipantStatus.WAITING);
        assertThat(sessionCourtRepository
                .findAllBySessionIdOrderByAddedAtAscIdAsc(fixture.sessionId()))
                .hasSize((int) expectedSessionCourts);
        assertThat(matchPlanRepository.count()).isEqualTo(expectedMatchPlans);
        assertThat(matchRepository.count()).isEqualTo(expectedMatches);
        assertThat(matchParticipantRepository.count()).isZero();
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record Fixture(
            UUID venueId,
            UUID sessionId,
            List<UUID> participantIds
    ) {
    }
}

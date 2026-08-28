package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionException;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationException;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotException;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotFailureReason;
import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import com.sportssession.platform.matchmaking.domain.RecommendedTeam;
import com.sportssession.platform.shared.api.GlobalExceptionHandler;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchmakingControllerTest {

    private static final String ENDPOINT =
            "/api/sessions/{sessionId}/courts/{sessionCourtId}"
                    + "/match-recommendations";
    private static final UUID SESSION_ID = uuid(900);
    private static final UUID SESSION_COURT_ID = uuid(901);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    private RecordingRecommendationService recommendationService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        recommendationService = new RecordingRecommendationService();
        objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                )
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MatchmakingController(recommendationService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void recommendedOutcomeMapsExactEvidenceWithoutReorderingOrRounding()
            throws Exception {
        MatchRecommendation recommendation = recommendation();
        MatchRecommendationResponse projection =
                MatchRecommendationResponse.from(recommendation);
        assertThat(projection.teamA().slot1().ratingValue()).isSameAs(
                recommendation.teamA().slot1().ratingValue()
        );
        assertThat(projection.teamA().slot1().uncertainty()).isSameAs(
                recommendation.teamA().slot1().uncertainty()
        );
        assertThat(projection.teamARatingTotal()).isSameAs(
                recommendation.teamARatingTotal()
        );
        assertThat(projection.teamBRatingTotal()).isSameAs(
                recommendation.teamBRatingTotal()
        );
        assertThat(projection.ratingDifference()).isSameAs(
                recommendation.ratingDifference()
        );
        recommendationService.willReturn(recommendation);

        MvcResult result = mockMvc.perform(post(
                        ENDPOINT,
                        SESSION_ID,
                        SESSION_COURT_ID
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.algorithmVersion")
                        .value(MatchmakingEngine.ALGORITHM_VERSION))
                .andExpect(jsonPath("$.evaluationTime")
                        .value(EVALUATION_TIME.toString()))
                .andExpect(jsonPath("$.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(SESSION_COURT_ID.toString()))
                .andExpect(jsonPath("$.sportCode").value("BADMINTON"))
                .andExpect(jsonPath("$.matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$.eligiblePlayerCount").value(5))
                .andExpect(jsonPath("$.teamA.slot1.playerId")
                        .value(uuid(1).toString()))
                .andExpect(jsonPath("$.teamA.slot1.teamSide").value("A"))
                .andExpect(jsonPath("$.teamA.slot1.teamSlot").value(1))
                .andExpect(jsonPath("$.teamA.slot2.playerId")
                        .value(uuid(2).toString()))
                .andExpect(jsonPath("$.teamA.slot2.teamSide").value("A"))
                .andExpect(jsonPath("$.teamA.slot2.teamSlot").value(2))
                .andExpect(jsonPath("$.teamB.slot1.playerId")
                        .value(uuid(3).toString()))
                .andExpect(jsonPath("$.teamB.slot1.teamSide").value("B"))
                .andExpect(jsonPath("$.teamB.slot1.teamSlot").value(1))
                .andExpect(jsonPath("$.teamB.slot2.playerId")
                        .value(uuid(4).toString()))
                .andExpect(jsonPath("$.teamB.slot2.teamSide").value("B"))
                .andExpect(jsonPath("$.teamB.slot2.teamSlot").value(2))
                .andExpect(jsonPath("$.teamA.slot1.sessionParticipantId")
                        .value(uuid(101).toString()))
                .andExpect(jsonPath("$.teamA.slot1.waitingSince")
                        .value("2026-08-28T09:00:00Z"))
                .andExpect(jsonPath("$.teamA.slot1.waitingSeconds").value(3600))
                .andExpect(jsonPath("$.teamA.slot1.ratedMatches").value(7))
                .andExpect(jsonPath("$.teamA.slot1.ratingBasis")
                        .value("PERSISTED"))
                .andExpect(jsonPath("$.oldestWaitingSince")
                        .value("2026-08-28T09:00:00Z"))
                .andReturn();

        JsonNode json = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        assertDecimal(json, "/teamA/slot1/ratingValue", "25.123456789");
        assertDecimal(json, "/teamA/slot1/uncertainty", "8.123456789");
        assertDecimal(json, "/teamA/slot2/ratingValue", "24.876543219");
        assertDecimal(json, "/teamB/slot1/ratingValue", "24.987654321");
        assertDecimal(json, "/teamB/slot2/ratingValue", "25.012345681");
        assertDecimal(json, "/teamARatingTotal", "50.000000008");
        assertDecimal(json, "/teamBRatingTotal", "50.000000002");
        assertDecimal(json, "/ratingDifference", "0.000000006");
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    @Test
    void unavailableOutcomeReturnsOkWithoutFabricatedTeams() throws Exception {
        MatchmakingUnavailable unavailable = new MatchmakingUnavailable(
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                3,
                MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
        );
        recommendationService.willReturn(unavailable);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.algorithmVersion")
                        .value(MatchmakingEngine.ALGORITHM_VERSION))
                .andExpect(jsonPath("$.evaluationTime")
                        .value(EVALUATION_TIME.toString()))
                .andExpect(jsonPath("$.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(SESSION_COURT_ID.toString()))
                .andExpect(jsonPath("$.sportCode").value("BADMINTON"))
                .andExpect(jsonPath("$.matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$.eligiblePlayerCount").value(3))
                .andExpect(jsonPath("$.reason")
                        .value("INSUFFICIENT_ELIGIBLE_PLAYERS"))
                .andExpect(jsonPath("$.teamA").doesNotExist())
                .andExpect(jsonPath("$.teamB").doesNotExist())
                .andExpect(jsonPath("$.teamARatingTotal").doesNotExist())
                .andExpect(jsonPath("$.teamBRatingTotal").doesNotExist());

        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    @ParameterizedTest
    @MethodSource("notFoundFailures")
    void snapshotNotFoundFailuresReturnProjectStandardNotFound(
            MatchmakingSessionSnapshotException failure
    ) throws Exception {
        recommendationService.willThrow(failure);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(failure.getMessage()))
                .andExpect(jsonPath("$.path").value(
                        "/api/sessions/" + SESSION_ID
                                + "/courts/" + SESSION_COURT_ID
                                + "/match-recommendations"
                ));
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    @ParameterizedTest
    @MethodSource("operationalConflicts")
    void operationalStateFailuresReturnConflict(
            MatchmakingRecommendationException failure
    ) throws Exception {
        recommendationService.willThrow(failure);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(failure.getMessage()));
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    @ParameterizedTest
    @MethodSource("recommendationIntegrityFailures")
    void waitingAndRatingBatchIntegrityFailuresReturnGenericInternalError(
            MatchmakingRecommendationException failure
    ) throws Exception {
        assertInternalError(failure);
    }

    @ParameterizedTest
    @MethodSource("ratingResolutionFailures")
    void ratingResolutionFailuresReturnGenericInternalError(
            MatchmakingRatingResolutionException failure
    ) throws Exception {
        assertInternalError(failure);
    }

    @Test
    void invalidInternalMatchmakingEvidenceReturnsGenericInternalError()
            throws Exception {
        assertInternalError(new InvalidMatchmakingInputException(
                "internal candidate identity detail"
        ));
    }

    @Test
    void malformedUuidUsesExistingBadRequestMappingWithoutCallingService()
            throws Exception {
        mockMvc.perform(post(ENDPOINT, "not-a-uuid", SESSION_COURT_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Invalid value for sessionId"));

        assertThat(recommendationService.callCount()).isZero();
    }

    private void assertInternalError(RuntimeException failure) throws Exception {
        recommendationService.willThrow(failure);

        mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error")
                        .value("Internal Server Error"))
                .andExpect(jsonPath("$.message")
                        .value("Matchmaking internal error"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.not(failure.getMessage())));
        recommendationService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    private static Stream<MatchmakingSessionSnapshotException>
    notFoundFailures() {
        return Stream.of(
                new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_NOT_FOUND,
                        SESSION_ID,
                        SESSION_COURT_ID
                ),
                new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_COURT_NOT_FOUND_FOR_SESSION,
                        SESSION_ID,
                        SESSION_COURT_ID
                )
        );
    }

    private static Stream<MatchmakingRecommendationException>
    operationalConflicts() {
        return Stream.of(
                recommendationFailure(
                        MatchmakingRecommendationFailureReason
                                .SESSION_NOT_IN_PROGRESS
                ),
                recommendationFailure(
                        MatchmakingRecommendationFailureReason
                                .SESSION_COURT_NOT_AVAILABLE
                )
        );
    }

    private static Stream<MatchmakingRecommendationException>
    recommendationIntegrityFailures() {
        return Stream.of(
                recommendationFailure(
                        MatchmakingRecommendationFailureReason
                                .WAITING_PARTICIPANT_MISSING_WAITING_SINCE
                ),
                recommendationFailure(
                        MatchmakingRecommendationFailureReason
                                .WAITING_PARTICIPANT_WAITING_SINCE_AFTER_EVALUATION_TIME
                ),
                recommendationFailure(
                        MatchmakingRecommendationFailureReason
                                .RATING_BATCH_INCOMPLETE
                )
        );
    }

    private static Stream<MatchmakingRatingResolutionException>
    ratingResolutionFailures() {
        return Stream.of(
                ratingFailure(
                        MatchmakingRatingResolutionFailureReason
                                .MISSING_INITIAL_PRIOR
                ),
                ratingFailure(
                        MatchmakingRatingResolutionFailureReason
                                .UNSUPPORTED_PERSISTED_ALGORITHM_VERSION
                )
        );
    }

    private static MatchmakingRecommendationException recommendationFailure(
            MatchmakingRecommendationFailureReason reason
    ) {
        return new MatchmakingRecommendationException(
                reason,
                "internal detail for " + reason
        );
    }

    private static MatchmakingRatingResolutionException ratingFailure(
            MatchmakingRatingResolutionFailureReason reason
    ) {
        return new MatchmakingRatingResolutionException(
                reason,
                Set.of(uuid(1)),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                "internal detail for " + reason
        );
    }

    private static MatchRecommendation recommendation() {
        RecommendedPlayer teamASlot1 = player(
                101, 1, TeamSide.A, 1,
                "2026-08-28T09:00:00Z", 3600,
                "25.123456789", "8.123456789", 7,
                RatingBasis.PERSISTED
        );
        RecommendedPlayer teamASlot2 = player(
                102, 2, TeamSide.A, 2,
                "2026-08-28T09:10:00Z", 3000,
                "24.876543219", "7.987654321", 0,
                RatingBasis.INITIAL_PRIOR
        );
        RecommendedPlayer teamBSlot1 = player(
                103, 3, TeamSide.B, 1,
                "2026-08-28T09:20:00Z", 2400,
                "24.987654321", "8.111111111", 3,
                RatingBasis.PERSISTED
        );
        RecommendedPlayer teamBSlot2 = player(
                104, 4, TeamSide.B, 2,
                "2026-08-28T09:30:00Z", 1800,
                "25.012345681", "8.222222222", 0,
                RatingBasis.INITIAL_PRIOR
        );
        RecommendedTeam teamA = new RecommendedTeam(
                TeamSide.A,
                teamASlot1,
                teamASlot2,
                decimal("50.000000008")
        );
        RecommendedTeam teamB = new RecommendedTeam(
                TeamSide.B,
                teamBSlot1,
                teamBSlot2,
                decimal("50.000000002")
        );
        return new MatchRecommendation(
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                5,
                teamA,
                teamB,
                teamA.ratingTotal(),
                teamB.ratingTotal(),
                decimal("0.000000006"),
                teamASlot1.waitingSince()
        );
    }

    private static RecommendedPlayer player(
            int participantId,
            int playerId,
            TeamSide teamSide,
            int teamSlot,
            String waitingSince,
            long waitingSeconds,
            String ratingValue,
            String uncertainty,
            int ratedMatches,
            RatingBasis ratingBasis
    ) {
        return new RecommendedPlayer(
                uuid(participantId),
                uuid(playerId),
                teamSide,
                teamSlot,
                Instant.parse(waitingSince),
                waitingSeconds,
                decimal(ratingValue),
                decimal(uncertainty),
                ratedMatches,
                ratingBasis
        );
    }

    private static void assertDecimal(
            JsonNode json,
            String pointer,
            String expected
    ) {
        assertThat(json.at(pointer).isNumber()).isTrue();
        assertThat(json.at(pointer).decimalValue())
                .isEqualByComparingTo(expected);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private static final class RecordingRecommendationService
            extends MatchmakingRecommendationService {

        private MatchmakingResult result;
        private RuntimeException failure;
        private int callCount;
        private UUID sessionId;
        private UUID sessionCourtId;

        private RecordingRecommendationService() {
            super(null, null, null, null);
        }

        @Override
        public MatchmakingResult recommend(
                UUID requestedSessionId,
                UUID requestedSessionCourtId
        ) {
            callCount++;
            sessionId = requestedSessionId;
            sessionCourtId = requestedSessionCourtId;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        private void willReturn(MatchmakingResult value) {
            result = value;
        }

        private void willThrow(RuntimeException value) {
            failure = value;
        }

        private int callCount() {
            return callCount;
        }

        private void assertCalledOnceWith(
                UUID expectedSessionId,
                UUID expectedSessionCourtId
        ) {
            assertThat(callCount).isEqualTo(1);
            assertThat(sessionId).isEqualTo(expectedSessionId);
            assertThat(sessionCourtId).isEqualTo(expectedSessionCourtId);
        }
    }
}

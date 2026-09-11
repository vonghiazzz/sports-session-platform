package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingOutcome;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingPreview;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.application.GlobalMatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotException;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotFailureReason;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.shared.api.GlobalExceptionHandler;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalMatchmakingControllerTest {

    private static final String ENDPOINT =
            "/api/sessions/{sessionId}/match-recommendations";
    private static final UUID SESSION_ID = uuid(1);
    private static final UUID FIRST_COURT_ID = uuid(10);
    private static final UUID SECOND_COURT_ID = uuid(11);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-11T03:00:00Z");

    private GlobalMatchmakingRecommendationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(GlobalMatchmakingRecommendationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new GlobalMatchmakingController(service)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        Jackson2ObjectMapperBuilder.json()
                                .featuresToDisable(
                                        SerializationFeature
                                                .WRITE_DATES_AS_TIMESTAMPS
                                )
                                .build()
                ))
                .build();
    }

    @Test
    void mapsRecommendedAndUnavailableCourtEvidence() throws Exception {
        MatchRecommendation recommendation = recommendation();
        MatchmakingUnavailable unavailable = new MatchmakingUnavailable(
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                SECOND_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                2,
                MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
        );
        when(service.preview(SESSION_ID)).thenReturn(new GlobalMatchmakingPreview(
                GlobalMatchmakingOutcome.RECOMMENDED,
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                6,
                List.of(recommendation, unavailable),
                null
        ));

        mockMvc.perform(post(ENDPOINT, SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RECOMMENDED"))
                .andExpect(jsonPath("$.orchestrationVersion").value(
                        "global-greedy-available-unplanned-v1"
                ))
                .andExpect(jsonPath("$.selectionAlgorithmVersion").value(
                        MatchmakingEngine.ALGORITHM_VERSION
                ))
                .andExpect(jsonPath("$.evaluationTime").value(
                        EVALUATION_TIME.toString()
                ))
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.initialEligiblePlayerCount").value(6))
                .andExpect(jsonPath("$.courtResults.length()").value(2))
                .andExpect(jsonPath("$.courtResults[0].outcome")
                        .value("RECOMMENDED"))
                .andExpect(jsonPath("$.courtResults[0].sessionCourtId")
                        .value(FIRST_COURT_ID.toString()))
                .andExpect(jsonPath(
                        "$.courtResults[0].teamA.slot1.sessionMatchesPlayed"
                ).value(0))
                .andExpect(jsonPath("$.courtResults[1].outcome")
                        .value("UNAVAILABLE"))
                .andExpect(jsonPath("$.courtResults[1].sessionCourtId")
                        .value(SECOND_COURT_ID.toString()))
                .andExpect(jsonPath("$.courtResults[1].eligiblePlayerCount")
                        .value(2))
                .andExpect(jsonPath("$.courtResults[1].reason").value(
                        "INSUFFICIENT_ELIGIBLE_PLAYERS"
                ))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    void zeroTargetCourtsReturnsNormalUnavailableResponse() throws Exception {
        when(service.preview(SESSION_ID)).thenReturn(new GlobalMatchmakingPreview(
                GlobalMatchmakingOutcome.UNAVAILABLE,
                GlobalMatchmakingRecommendationService.ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                EVALUATION_TIME,
                SESSION_ID,
                8,
                List.of(),
                GlobalMatchmakingUnavailableReason.NO_ELIGIBLE_COURTS
        ));

        mockMvc.perform(post(ENDPOINT, SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.courtResults").isEmpty())
                .andExpect(jsonPath("$.reason")
                        .value("NO_ELIGIBLE_COURTS"));
    }

    @Test
    void unknownSessionUsesExistingNotFoundContract() throws Exception {
        when(service.preview(SESSION_ID)).thenThrow(
                new MatchmakingSessionSnapshotException(
                        MatchmakingSessionSnapshotFailureReason
                                .SESSION_NOT_FOUND,
                        SESSION_ID,
                        null
                )
        );

        mockMvc.perform(post(ENDPOINT, SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void malformedSessionIdUsesExistingBadRequestContract() throws Exception {
        mockMvc.perform(post(ENDPOINT, "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }

    private MatchRecommendation recommendation() {
        MatchmakingResult result = new MatchmakingEngine().recommend(
                new MatchmakingContext(
                        SESSION_ID,
                        FIRST_COURT_ID,
                        SportCode.BADMINTON,
                        MatchFormat.DOUBLES,
                        EVALUATION_TIME,
                        candidates()
                )
        );
        return (MatchRecommendation) result;
    }

    private List<MatchmakingCandidate> candidates() {
        return Stream.iterate(0, index -> index + 1)
                .limit(4)
                .map(index -> new MatchmakingCandidate(
                        uuid(100 + index),
                        uuid(200 + index),
                        EVALUATION_TIME.minusSeconds(100 - index),
                        com.sportssession.platform.player.domain.SkillLevel
                                .INTERMEDIATE,
                        0,
                        new BigDecimal("25.0"),
                        new BigDecimal("8.0"),
                        0,
                        RatingBasis.INITIAL_PRIOR
                ))
                .toList();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

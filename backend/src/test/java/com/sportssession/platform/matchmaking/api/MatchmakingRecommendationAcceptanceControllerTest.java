package com.sportssession.platform.matchmaking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchResourceConflictException;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationAcceptanceException;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationAcceptanceFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationAcceptanceService;
import com.sportssession.platform.matchmaking.application.MatchmakingRecommendationService;
import com.sportssession.platform.matchmaking.application.SubmittedRecommendationEvidence;
import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.session.domain.SessionNotFoundException;
import com.sportssession.platform.shared.api.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MatchmakingRecommendationAcceptanceControllerTest {

    private static final String ENDPOINT =
            "/api/sessions/{sessionId}/courts/{sessionCourtId}"
                    + "/match-recommendations/accept";
    private static final UUID SESSION_ID = uuid(900);
    private static final UUID SESSION_COURT_ID = uuid(901);
    private static final Instant START_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    private RecordingAcceptanceService acceptanceService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        acceptanceService = new RecordingAcceptanceService();
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new MatchmakingRecommendationAcceptanceController(
                                acceptanceService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper)
                )
                .build();
    }

    @Test
    void validRequestReturnsCreatedPlayingRecommendedMatchWithoutLocation()
            throws Exception {
        acceptanceService.willReturn(startedMatch());

        mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.status").value("PLAYING"))
                .andExpect(jsonPath("$.source").value("RECOMMENDATION"))
                .andExpect(jsonPath("$.sessionId")
                        .value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(SESSION_COURT_ID.toString()))
                .andExpect(jsonPath("$.participants.length()").value(4));

        acceptanceService.assertCalledOnceWith(
                SESSION_ID,
                SESSION_COURT_ID
        );
    }

    @Test
    void malformedAssignmentCountReturnsBadRequestWithoutCallingService()
            throws Exception {
        Map<String, Object> body = Map.of(
                "algorithmVersion", "fairness-anchor-rating-sum-v1",
                "assignments", assignments().subList(0, 3)
        );

        perform(body).andExpect(status().isBadRequest());
        assertThat(acceptanceService.callCount()).isZero();
    }

    @Test
    void duplicateParticipantReturnsBadRequestWithoutCallingService()
            throws Exception {
        List<Map<String, Object>> assignments = List.of(
                assignment(101, "A", 1),
                assignment(101, "A", 2),
                assignment(103, "B", 1),
                assignment(104, "B", 2)
        );

        perform(Map.of(
                "algorithmVersion", "fairness-anchor-rating-sum-v1",
                "assignments", assignments
        )).andExpect(status().isBadRequest());
        assertThat(acceptanceService.callCount()).isZero();
    }

    @Test
    void duplicateTeamSlotReturnsBadRequestWithoutCallingService()
            throws Exception {
        List<Map<String, Object>> assignments = List.of(
                assignment(101, "A", 1),
                assignment(102, "A", 1),
                assignment(103, "B", 1),
                assignment(104, "B", 2)
        );

        perform(Map.of(
                "algorithmVersion", "fairness-anchor-rating-sum-v1",
                "assignments", assignments
        )).andExpect(status().isBadRequest());
        assertThat(acceptanceService.callCount()).isZero();
    }

    @Test
    void missingAlgorithmVersionReturnsBadRequestWithoutCallingService()
            throws Exception {
        perform(Map.of("assignments", assignments()))
                .andExpect(status().isBadRequest());
        assertThat(acceptanceService.callCount()).isZero();
    }

    @Test
    void staleRecommendationReturnsConflict() throws Exception {
        acceptanceService.willThrow(
                new MatchmakingRecommendationAcceptanceException(
                        MatchmakingRecommendationAcceptanceFailureReason
                                .RECOMMENDATION_STALE,
                        "Submitted recommendation is stale"
                )
        );

        perform(validBody())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Submitted recommendation is stale"));
    }

    @Test
    void missingSessionUsesExistingNotFoundMapping() throws Exception {
        acceptanceService.willThrow(new SessionNotFoundException(SESSION_ID));
        perform(validBody()).andExpect(status().isNotFound());
    }

    @Test
    void operationalStateConflictReturnsConflict() throws Exception {
        acceptanceService.willThrow(new MatchResourceConflictException(
                "Session Court must be AVAILABLE"
        ));
        perform(validBody()).andExpect(status().isConflict());
    }

    @Test
    void internalInvariantUsesSafeGenericError() throws Exception {
        acceptanceService.willThrow(new InvalidMatchmakingInputException(
                "sensitive internal evidence"
        ));
        perform(validBody())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message")
                        .value("Matchmaking internal error"));
    }

    @Test
    void requestContractDoesNotExposeMatchSource() {
        assertThat(Arrays.stream(
                AcceptMatchmakingRecommendationRequest.class
                        .getRecordComponents()
        ).map(RecordComponent::getName))
                .doesNotContain("source", "matchSource");
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            Object body
    ) throws Exception {
        return mockMvc.perform(post(ENDPOINT, SESSION_ID, SESSION_COURT_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> validBody() {
        return Map.of(
                "algorithmVersion", "fairness-anchor-rating-sum-v1",
                "assignments", assignments()
        );
    }

    private static List<Map<String, Object>> assignments() {
        return List.of(
                assignment(104, "B", 2),
                assignment(102, "A", 2),
                assignment(101, "A", 1),
                assignment(103, "B", 1)
        );
    }

    private static Map<String, Object> assignment(
            int participant,
            String teamSide,
            int teamSlot
    ) {
        return Map.of(
                "sessionParticipantId", uuid(participant),
                "teamSide", teamSide,
                "teamSlot", teamSlot
        );
    }

    private static StartedMatch startedMatch() {
        Match match = Match.create(
                SESSION_ID,
                SESSION_COURT_ID,
                MatchSource.RECOMMENDATION,
                START_TIME
        ).start(START_TIME);
        return new StartedMatch(
                match,
                List.of(
                        MatchParticipant.assign(
                                match.id(), uuid(101), TeamSide.A, 1
                        ),
                        MatchParticipant.assign(
                                match.id(), uuid(102), TeamSide.A, 2
                        ),
                        MatchParticipant.assign(
                                match.id(), uuid(103), TeamSide.B, 1
                        ),
                        MatchParticipant.assign(
                                match.id(), uuid(104), TeamSide.B, 2
                        )
                )
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private static final class RecordingAcceptanceService
            extends MatchmakingRecommendationAcceptanceService {
        private StartedMatch result;
        private RuntimeException failure;
        private int callCount;
        private UUID sessionId;
        private UUID sessionCourtId;

        private RecordingAcceptanceService() {
            super(
                    new MatchmakingRecommendationService(
                            null, null, null, null
                    ),
                    new MatchService(null, null, null, null)
            );
        }

        @Override
        public StartedMatch acceptAndStart(
                UUID requestedSessionId,
                UUID requestedSessionCourtId,
                SubmittedRecommendationEvidence evidence
        ) {
            callCount++;
            sessionId = requestedSessionId;
            sessionCourtId = requestedSessionCourtId;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        private void willReturn(StartedMatch value) {
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

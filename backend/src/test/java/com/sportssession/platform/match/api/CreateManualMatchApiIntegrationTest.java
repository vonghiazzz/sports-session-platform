package com.sportssession.platform.match.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CreateManualMatchApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @BeforeEach
    void cleanDatabase() {
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
    void createValidManualMatchReturnsCreatedRepresentation() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);

        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.matchesPattern(
                                ".*/api/sessions/[0-9a-f-]{36}/matches/[0-9a-f-]{36}"
                        )
                ))
                .andExpect(jsonPath("$.sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(fixture.sessionCourtId().toString()))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.resultVersion").value(0))
                .andExpect(jsonPath("$.winnerTeam").doesNotExist())
                .andExpect(jsonPath("$.teamAScore").doesNotExist())
                .andExpect(jsonPath("$.teamBScore").doesNotExist())
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void persistsExactlyFourParticipantsWithRequiredTeamSlots() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);

        MvcResult result = createManualMatch(
                fixture.sessionId(),
                validRequest(fixture)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andExpect(jsonPath("$.participants[0].teamSide").value("A"))
                .andExpect(jsonPath("$.participants[0].teamSlot").value(1))
                .andExpect(jsonPath("$.participants[1].teamSide").value("A"))
                .andExpect(jsonPath("$.participants[1].teamSlot").value(2))
                .andExpect(jsonPath("$.participants[2].teamSide").value("B"))
                .andExpect(jsonPath("$.participants[2].teamSlot").value(1))
                .andExpect(jsonPath("$.participants[3].teamSide").value("B"))
                .andExpect(jsonPath("$.participants[3].teamSlot").value(2))
                .andReturn();

        UUID matchId = responseId(result);
        assertThat(matchParticipantRepository
                .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(matchId))
                .hasSize(4);
    }

    @Test
    void creationDoesNotReserveParticipantsOrSessionCourt() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);

        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isCreated());

        assertThat(fixture.participantIds())
                .allSatisfy(participantId -> assertThat(
                        sessionParticipantRepository.findById(participantId)
                                .orElseThrow()
                                .getStatus()
                ).isEqualTo(ParticipantStatus.WAITING));
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow()
                .getStatus()).isEqualTo(SessionCourtStatus.AVAILABLE);
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);

        createManualMatch(UUID.randomUUID(), validRequest(fixture))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSessionCourtReturnsNotFound() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        CreateManualMatchRequest request = new CreateManualMatchRequest(
                UUID.randomUUID(),
                validRequest(fixture).participants()
        );

        createManualMatch(fixture.sessionId(), request)
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownSessionParticipantReturnsNotFoundAndPersistsNothing() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(fixture).participants()
        );
        assignments.set(3, assignment(UUID.randomUUID(), TeamSide.B, 2));

        createManualMatch(
                fixture.sessionId(),
                new CreateManualMatchRequest(fixture.sessionCourtId(), assignments)
        )
                .andExpect(status().isNotFound());

        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
    }

    @Test
    void sessionNotInProgressReturnsConflict() throws Exception {
        RuntimeFixture fixture = createFixture(false, SessionCourtStatus.AVAILABLE);

        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Manual Match can be created only while Session is IN_PROGRESS"
                ));
    }

    @Test
    void sessionCourtFromAnotherSessionReturnsConflict() throws Exception {
        RuntimeFixture first = createFixture(true, SessionCourtStatus.AVAILABLE);
        RuntimeFixture second = createFixture(true, SessionCourtStatus.AVAILABLE);
        CreateManualMatchRequest request = new CreateManualMatchRequest(
                second.sessionCourtId(),
                validRequest(first).participants()
        );

        createManualMatch(first.sessionId(), request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Session Court belongs to a different Session"));
    }

    @Test
    void unavailableSessionCourtReturnsConflict() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.UNAVAILABLE);

        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Session Court must be AVAILABLE to create a Manual Match"
                ));
    }

    @Test
    void participantFromAnotherSessionReturnsConflict() throws Exception {
        RuntimeFixture first = createFixture(true, SessionCourtStatus.AVAILABLE);
        RuntimeFixture second = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(first).participants()
        );
        assignments.set(0, assignment(
                second.participantIds().getFirst(),
                TeamSide.A,
                1
        ));

        createManualMatch(first.sessionId(), new CreateManualMatchRequest(
                first.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Session Participant belongs to a different Session: "
                                + second.participantIds().getFirst()
                ));
    }

    @ParameterizedTest
    @EnumSource(
            value = ParticipantStatus.class,
            names = {"REGISTERED", "PAUSED", "LEFT", "PLAYING"}
    )
    void participantThatIsNotWaitingReturnsConflict(
            ParticipantStatus participantStatus
    ) throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        UUID participantId = createParticipant(
                fixture.sessionId(),
                participantStatus
        );
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(fixture).participants()
        );
        assignments.set(0, assignment(participantId, TeamSide.A, 1));

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Session Participant must be WAITING: " + participantId));
    }

    @Test
    void fewerThanFourAssignmentsReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments =
                validRequest(fixture).participants().subList(0, 3);

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreThanFourAssignmentsReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(fixture).participants()
        );
        assignments.add(assignment(
                createParticipant(fixture.sessionId(), ParticipantStatus.WAITING),
                TeamSide.A,
                1
        ));

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nullParticipantAssignmentReturnsBadRequestAndPersistsNothing()
            throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<Object> assignments = new ArrayList<>();
        assignments.add(null);
        assignments.add(rawAssignment(
                fixture.participantIds().get(1),
                "A",
                2
        ));
        assignments.add(rawAssignment(
                fixture.participantIds().get(2),
                "B",
                1
        ));
        assignments.add(rawAssignment(
                fixture.participantIds().get(3),
                "B",
                2
        ));
        Map<String, Object> request = Map.of(
                "sessionCourtId", fixture.sessionCourtId(),
                "participants", assignments
        );

        mockMvc.perform(post(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors['participants[0]']")
                        .value("participant assignment is required"));

        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow()
                .getStatus()).isEqualTo(SessionCourtStatus.AVAILABLE);
        assertThat(fixture.participantIds())
                .allSatisfy(participantId -> assertThat(
                        sessionParticipantRepository.findById(participantId)
                                .orElseThrow()
                                .getStatus()
                ).isEqualTo(ParticipantStatus.WAITING));
    }

    @Test
    void duplicateSessionParticipantReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(fixture).participants()
        );
        assignments.set(1, assignment(
                fixture.participantIds().getFirst(),
                TeamSide.A,
                2
        ));

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Participant assignments must use unique sessionParticipantIds"
                ));
    }

    @Test
    void teamAWithIncorrectCountReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = List.of(
                assignment(fixture.participantIds().get(0), TeamSide.A, 1),
                assignment(fixture.participantIds().get(1), TeamSide.A, 2),
                assignment(fixture.participantIds().get(2), TeamSide.A, 1),
                assignment(fixture.participantIds().get(3), TeamSide.B, 1)
        );

        assertInvalidTeamComposition(fixture, assignments);
    }

    @Test
    void teamBWithIncorrectCountReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = List.of(
                assignment(fixture.participantIds().get(0), TeamSide.A, 1),
                assignment(fixture.participantIds().get(1), TeamSide.B, 1),
                assignment(fixture.participantIds().get(2), TeamSide.B, 2),
                assignment(fixture.participantIds().get(3), TeamSide.B, 1)
        );

        assertInvalidTeamComposition(fixture, assignments);
    }

    @Test
    void duplicateTeamSlotReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = List.of(
                assignment(fixture.participantIds().get(0), TeamSide.A, 1),
                assignment(fixture.participantIds().get(1), TeamSide.A, 1),
                assignment(fixture.participantIds().get(2), TeamSide.B, 1),
                assignment(fixture.participantIds().get(3), TeamSide.B, 2)
        );

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Participant assignments must not duplicate a team slot"
                ));
    }

    @Test
    void invalidTeamSlotReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<MatchParticipantRequest> assignments = new ArrayList<>(
                validRequest(fixture).participants()
        );
        assignments.set(0, new MatchParticipantRequest(
                fixture.participantIds().getFirst(),
                TeamSide.A,
                3
        ));

        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTeamSideReturnsBadRequest() throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);
        List<Map<String, Object>> assignments = List.of(
                rawAssignment(fixture.participantIds().get(0), "C", 1),
                rawAssignment(fixture.participantIds().get(1), "A", 2),
                rawAssignment(fixture.participantIds().get(2), "B", 1),
                rawAssignment(fixture.participantIds().get(3), "B", 2)
        );
        Map<String, Object> request = Map.of(
                "sessionCourtId", fixture.sessionCourtId(),
                "participants", assignments
        );

        mockMvc.perform(post(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void multipleCreatedMatchesMayReuseWaitingParticipantsAndAvailableCourt()
            throws Exception {
        RuntimeFixture fixture = createFixture(true, SessionCourtStatus.AVAILABLE);

        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isCreated());
        createManualMatch(fixture.sessionId(), validRequest(fixture))
                .andExpect(status().isCreated());

        assertThat(matchRepository.count()).isEqualTo(2);
        assertThat(matchRepository.findAll())
                .allSatisfy(entity -> assertThat(entity.getStatus())
                        .isEqualTo(MatchStatus.CREATED));
        assertThat(matchParticipantRepository.count()).isEqualTo(8);
        assertThat(sessionCourtRepository.findById(fixture.sessionCourtId())
                .orElseThrow()
                .getStatus()).isEqualTo(SessionCourtStatus.AVAILABLE);
        assertThat(fixture.participantIds())
                .allSatisfy(participantId -> assertThat(
                        sessionParticipantRepository.findById(participantId)
                                .orElseThrow()
                                .getStatus()
                ).isEqualTo(ParticipantStatus.WAITING));
    }

    private void assertInvalidTeamComposition(
            RuntimeFixture fixture,
            List<MatchParticipantRequest> assignments
    ) throws Exception {
        createManualMatch(fixture.sessionId(), new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                assignments
        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Teams A and B must each contain exactly 2 Participants"
                ));
    }

    private RuntimeFixture createFixture(
            boolean inProgress,
            SessionCourtStatus courtStatus
    ) {
        Instant now = Instant.now();
        Venue venue = Venue.create("Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();

        Court court = Court.create(
                venueId,
                "Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository
                .saveAndFlush(CourtEntity.from(court))
                .getId();

        Session session = Session.create(
                venueId,
                "Manual Match Session",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        );
        if (inProgress) {
            session = session.start(now.plusSeconds(1));
        }
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();

        SessionCourt sessionCourt = SessionCourt.allocate(sessionId, courtId, now);
        if (courtStatus == SessionCourtStatus.UNAVAILABLE) {
            sessionCourt = sessionCourt.disable(now.plusSeconds(1));
        }
        UUID sessionCourtId = sessionCourtRepository
                .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                .getId();

        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            participantIds.add(createParticipant(
                    sessionId,
                    ParticipantStatus.WAITING
            ));
        }

        return new RuntimeFixture(sessionId, sessionCourtId, participantIds);
    }

    private UUID createParticipant(
            UUID sessionId,
            ParticipantStatus status
    ) {
        Instant now = Instant.now();
        Player player = Player.create("Player " + UUID.randomUUID(), now);
        UUID playerId = playerRepository
                .saveAndFlush(PlayerEntity.from(player))
                .getId();

        SessionParticipant registered = SessionParticipant.register(
                sessionId,
                playerId,
                now
        );
        SessionParticipant participant = switch (status) {
            case REGISTERED -> registered;
            case WAITING -> registered.checkIn(now.plusSeconds(1));
            case PAUSED -> registered.checkIn(now.plusSeconds(1))
                    .pause(now.plusSeconds(2));
            case LEFT -> registered.checkIn(now.plusSeconds(1))
                    .leave(now.plusSeconds(2));
            case PLAYING -> playingParticipant(
                    registered.checkIn(now.plusSeconds(1)),
                    now.plusSeconds(2)
            );
        };

        return sessionParticipantRepository
                .saveAndFlush(SessionParticipantEntity.from(participant))
                .getId();
    }

    private SessionParticipant playingParticipant(
            SessionParticipant waiting,
            Instant updatedAt
    ) {
        return new SessionParticipant(
                waiting.id(),
                waiting.sessionId(),
                waiting.playerId(),
                ParticipantStatus.PLAYING,
                waiting.joinedAt(),
                waiting.checkedInAt(),
                null,
                null,
                waiting.totalPausedSeconds(),
                null,
                waiting.version(),
                waiting.createdAt(),
                updatedAt
        );
    }

    private CreateManualMatchRequest validRequest(RuntimeFixture fixture) {
        return new CreateManualMatchRequest(
                fixture.sessionCourtId(),
                List.of(
                        assignment(fixture.participantIds().get(0), TeamSide.A, 1),
                        assignment(fixture.participantIds().get(1), TeamSide.A, 2),
                        assignment(fixture.participantIds().get(2), TeamSide.B, 1),
                        assignment(fixture.participantIds().get(3), TeamSide.B, 2)
                )
        );
    }

    private MatchParticipantRequest assignment(
            UUID participantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        return new MatchParticipantRequest(participantId, teamSide, teamSlot);
    }

    private Map<String, Object> rawAssignment(
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

    private ResultActions createManualMatch(
            UUID sessionId,
            CreateManualMatchRequest request
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/sessions/{sessionId}/matches",
                        sessionId
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID responseId(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(
                result.getResponse().getContentAsString()
        );
        return UUID.fromString(body.get("id").asText());
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
        private RuntimeFixture {
            participantIds = List.copyOf(participantIds);
        }
    }
}

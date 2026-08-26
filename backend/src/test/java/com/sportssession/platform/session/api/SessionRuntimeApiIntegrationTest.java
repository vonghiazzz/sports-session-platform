package com.sportssession.platform.session.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SessionRuntimeApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

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

    @BeforeEach
    void cleanDatabase() {
        sessionCourtRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void createSessionSucceeds() throws Exception {
        UUID venueId = createVenue("Venue A", true);

        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(venueId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        ".*/api/sessions/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.title").value("Evening Badminton"))
                .andExpect(jsonPath("$.sport").value("BADMINTON"))
                .andExpect(jsonPath("$.matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.startedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(0))
                .andReturn();

        assertThat(sessionRepository.findById(responseId(result))).isPresent();
    }

    @Test
    void getSessionSucceeds() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        mockMvc.perform(get("/api/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("PLANNED"));
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        UUID missingSessionId = UUID.randomUUID();

        mockMvc.perform(get("/api/sessions/{sessionId}", missingSessionId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Session not found: " + missingSessionId));
    }

    @Test
    void invalidPlannedTimeRangeIsRejected() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(
                                venueId,
                                "Invalid Session",
                                SportCode.BADMINTON,
                                MatchFormat.DOUBLES,
                                start,
                                start))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("plannedEndAt must be after plannedStartAt"));

        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void createSessionForInactiveVenueIsRejected() throws Exception {
        UUID venueId = createVenue("Inactive Venue", false);

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(venueId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot create a Session for inactive Venue: " + venueId));
    }

    @Test
    void startPlannedSessionSucceeds() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        mockMvc.perform(post("/api/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void startingSessionTwiceIsRejected() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));

        mockMvc.perform(post("/api/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Session cannot start from status IN_PROGRESS"));
    }

    @Test
    void completeInProgressSessionSucceeds() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));

        mockMvc.perform(post("/api/sessions/{sessionId}/complete", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void completePlannedSessionIsRejected() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        mockMvc.perform(post("/api/sessions/{sessionId}/complete", sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Session cannot complete from status PLANNED"));
    }

    @Test
    void cancelPlannedSessionSucceeds() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        mockMvc.perform(post("/api/sessions/{sessionId}/cancel", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
    }

    @Test
    void terminalSessionCannotStartAgain() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));
        performAction("/api/sessions/{sessionId}/cancel", sessionId)
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/sessions/{sessionId}/start", sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Session cannot start from status CANCELLED"));
    }

    @Test
    void addParticipantCreatesRegisteredParticipant() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));
        UUID playerId = createPlayer("Player A");

        MvcResult result = addParticipant(sessionId, playerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.playerId").value(playerId.toString()))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.checkedInAt").doesNotExist())
                .andReturn();

        assertThat(participantRepository.findById(responseId(result))).isPresent();
    }

    @Test
    void duplicatePlayerInSameSessionIsRejected() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));
        UUID playerId = createPlayer("Player A");
        createParticipant(sessionId, playerId);

        addParticipant(sessionId, playerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        assertThat(participantRepository.count()).isEqualTo(1);
    }

    @Test
    void samePlayerCanJoinDifferentSessions() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID firstSessionId = createSession(venueId);
        UUID secondSessionId = createSession(venueId);
        UUID playerId = createPlayer("Player A");

        createParticipant(firstSessionId, playerId);
        createParticipant(secondSessionId, playerId);

        assertThat(participantRepository.count()).isEqualTo(2);
    }

    @Test
    void listParticipantsReturnsOnlySessionParticipants() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID firstSessionId = createSession(venueId);
        UUID secondSessionId = createSession(venueId);
        createParticipant(firstSessionId, createPlayer("Player A"));
        createParticipant(firstSessionId, createPlayer("Player B"));
        createParticipant(secondSessionId, createPlayer("Player C"));

        mockMvc.perform(get("/api/sessions/{sessionId}/participants", firstSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionId").value(firstSessionId.toString()))
                .andExpect(jsonPath("$[1].sessionId").value(firstSessionId.toString()));
    }

    @Test
    void checkInRegisteredParticipantTransitionsToWaiting() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createParticipant(sessionId, createPlayer("Player A"));

        checkIn(sessionId, participantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.checkedInAt").isNotEmpty())
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());
    }

    @Test
    void checkInBeforeSessionStartIsRejected() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));
        UUID participantId = createParticipant(sessionId, createPlayer("Player A"));

        checkIn(sessionId, participantId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Participants can check in only while Session is IN_PROGRESS"));
    }

    @Test
    void waitingParticipantCanPause() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createWaitingParticipant(sessionId);

        pause(sessionId, participantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.waitingSince").doesNotExist())
                .andExpect(jsonPath("$.pausedAt").isNotEmpty());
    }

    @Test
    void pausedParticipantCanResume() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createPausedParticipant(sessionId);

        resume(sessionId, participantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.pausedAt").doesNotExist())
                .andExpect(jsonPath("$.waitingSince").isNotEmpty());
    }

    @Test
    void resumeResetsWaitingSince() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createParticipant(sessionId, createPlayer("Player A"));
        MvcResult checkedIn = checkIn(sessionId, participantId)
                .andExpect(status().isOk())
                .andReturn();
        Instant originalWaitingSince = responseInstant(checkedIn, "waitingSince");
        pause(sessionId, participantId).andExpect(status().isOk());

        MvcResult resumed = resume(sessionId, participantId)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(responseInstant(resumed, "waitingSince"))
                .isAfter(originalWaitingSince);
    }

    @Test
    void waitingParticipantCanLeave() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createWaitingParticipant(sessionId);

        leave(sessionId, participantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT"))
                .andExpect(jsonPath("$.leftAt").isNotEmpty())
                .andExpect(jsonPath("$.waitingSince").doesNotExist());
    }

    @Test
    void pausedParticipantCanLeave() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createPausedParticipant(sessionId);

        leave(sessionId, participantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEFT"))
                .andExpect(jsonPath("$.pausedAt").doesNotExist());
    }

    @Test
    void completedSessionRejectsParticipantRuntimeMutation() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createWaitingParticipant(sessionId);
        performAction("/api/sessions/{sessionId}/complete", sessionId)
                .andExpect(status().isOk());

        pause(sessionId, participantId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot modify runtime state while Session is COMPLETED"));
    }

    @Test
    void leftParticipantCannotResumeOrCheckIn() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createPausedParticipant(sessionId);
        leave(sessionId, participantId).andExpect(status().isOk());

        resume(sessionId, participantId).andExpect(status().isConflict());
        checkIn(sessionId, participantId).andExpect(status().isConflict());
    }

    @Test
    void noApiAllowsArbitraryPlayingParticipantState() throws Exception {
        UUID sessionId = createStartedSession(createVenue("Venue A", true));
        UUID participantId = createParticipant(sessionId, createPlayer("Player A"));

        mockMvc.perform(patch(
                        "/api/sessions/{sessionId}/participants/{participantId}/check-in",
                        sessionId,
                        participantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PLAYING\"}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownParticipantReturnsNotFound() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        pause(sessionId, UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    @Test
    void addValidCourtCreatesAvailableSessionCourt() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID courtId = createCourt(venueId, "Court 1", true);

        MvcResult result = addCourt(sessionId, courtId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.courtId").value(courtId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn();

        assertThat(sessionCourtRepository.findById(responseId(result))).isPresent();
    }

    @Test
    void duplicateCourtAllocationIsRejected() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID courtId = createCourt(venueId, "Court 1", true);
        createSessionCourt(sessionId, courtId);

        addCourt(sessionId, courtId)
                .andExpect(status().isConflict());

        assertThat(sessionCourtRepository.count()).isEqualTo(1);
    }

    @Test
    void courtFromDifferentVenueIsRejected() throws Exception {
        UUID sessionVenueId = createVenue("Venue A", true);
        UUID otherVenueId = createVenue("Venue B", true);
        UUID sessionId = createSession(sessionVenueId);
        UUID courtId = createCourt(otherVenueId, "Court 1", true);

        addCourt(sessionId, courtId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Court belongs to a different Venue than Session"));
    }

    @Test
    void inactiveCourtIsRejected() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID courtId = createCourt(venueId, "Inactive Court", false);

        addCourt(sessionId, courtId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot allocate inactive Court: " + courtId));
    }

    @Test
    void courtFromVenueThatBecameInactiveIsRejected() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID courtId = createCourt(venueId, "Court 1", true);
        jdbcTemplate.update("UPDATE venues SET active = false WHERE id = ?", venueId);

        addCourt(sessionId, courtId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot allocate a Court from inactive Venue: " + venueId));
    }

    @Test
    void availableSessionCourtCanBeDisabled() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID sessionCourtId = createSessionCourt(
                sessionId, createCourt(venueId, "Court 1", true));

        disable(sessionId, sessionCourtId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void unavailableSessionCourtCanBeEnabled() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID sessionCourtId = createSessionCourt(
                sessionId, createCourt(venueId, "Court 1", true));
        disable(sessionId, sessionCourtId).andExpect(status().isOk());

        enable(sessionId, sessionCourtId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void cancelledSessionRejectsCourtRuntimeMutation() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID sessionCourtId = createSessionCourt(
                sessionId, createCourt(venueId, "Court 1", true));
        performAction("/api/sessions/{sessionId}/cancel", sessionId)
                .andExpect(status().isOk());

        disable(sessionId, sessionCourtId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Cannot modify runtime state while Session is CANCELLED"));
    }

    @Test
    void invalidDisableAndEnableTransitionsAreRejected() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID sessionId = createSession(venueId);
        UUID sessionCourtId = createSessionCourt(
                sessionId, createCourt(venueId, "Court 1", true));

        enable(sessionId, sessionCourtId).andExpect(status().isConflict());
        disable(sessionId, sessionCourtId).andExpect(status().isOk());
        disable(sessionId, sessionCourtId).andExpect(status().isConflict());
    }

    @Test
    void listSessionCourtsReturnsOnlyAllocatedCourts() throws Exception {
        UUID venueId = createVenue("Venue A", true);
        UUID firstSessionId = createSession(venueId);
        UUID secondSessionId = createSession(venueId);
        createSessionCourt(firstSessionId, createCourt(venueId, "Court 1", true));
        createSessionCourt(firstSessionId, createCourt(venueId, "Court 2", true));
        createSessionCourt(secondSessionId, createCourt(venueId, "Court 3", true));

        mockMvc.perform(get("/api/sessions/{sessionId}/courts", firstSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sessionId").value(firstSessionId.toString()))
                .andExpect(jsonPath("$[1].sessionId").value(firstSessionId.toString()));
    }

    @Test
    void unknownSessionCourtReturnsNotFound() throws Exception {
        UUID sessionId = createSession(createVenue("Venue A", true));

        disable(sessionId, UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    @Test
    void physicalCourtHasNoGlobalRuntimeStatus() throws Exception {
        UUID courtId = createCourt(createVenue("Venue A", true), "Court 1", true);

        mockMvc.perform(get("/api/courts/{courtId}", courtId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    private UUID createVenue(String name, boolean active) {
        Instant now = Instant.now();
        Venue venue = Venue.create(name, null, active, now);
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId, String name, boolean active) {
        Court court = Court.create(
                venueId, name, SportCode.BADMINTON, active, Instant.now());
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createPlayer(String displayName) {
        Player player = Player.create(displayName, Instant.now());
        return playerRepository.saveAndFlush(PlayerEntity.from(player)).getId();
    }

    private UUID createSession(UUID venueId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sessionJson(venueId)))
                .andExpect(status().isCreated())
                .andReturn();
        return responseId(result);
    }

    private UUID createStartedSession(UUID venueId) throws Exception {
        UUID sessionId = createSession(venueId);
        performAction("/api/sessions/{sessionId}/start", sessionId)
                .andExpect(status().isOk());
        return sessionId;
    }

    private UUID createParticipant(UUID sessionId, UUID playerId) throws Exception {
        return responseId(addParticipant(sessionId, playerId)
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID createWaitingParticipant(UUID sessionId) throws Exception {
        UUID participantId = createParticipant(sessionId, createPlayer("Player " + UUID.randomUUID()));
        checkIn(sessionId, participantId).andExpect(status().isOk());
        return participantId;
    }

    private UUID createPausedParticipant(UUID sessionId) throws Exception {
        UUID participantId = createWaitingParticipant(sessionId);
        pause(sessionId, participantId).andExpect(status().isOk());
        return participantId;
    }

    private UUID createSessionCourt(UUID sessionId, UUID courtId) throws Exception {
        return responseId(addCourt(sessionId, courtId)
                .andExpect(status().isCreated())
                .andReturn());
    }

    private org.springframework.test.web.servlet.ResultActions addParticipant(
            UUID sessionId,
            UUID playerId
    ) throws Exception {
        return mockMvc.perform(post("/api/sessions/{sessionId}/participants", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddParticipantRequest(playerId))));
    }

    private org.springframework.test.web.servlet.ResultActions addCourt(
            UUID sessionId,
            UUID courtId
    ) throws Exception {
        return mockMvc.perform(post("/api/sessions/{sessionId}/courts", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AddSessionCourtRequest(courtId))));
    }

    private org.springframework.test.web.servlet.ResultActions checkIn(
            UUID sessionId,
            UUID participantId
    ) throws Exception {
        return performParticipantAction(sessionId, participantId, "check-in");
    }

    private org.springframework.test.web.servlet.ResultActions pause(
            UUID sessionId,
            UUID participantId
    ) throws Exception {
        return performParticipantAction(sessionId, participantId, "pause");
    }

    private org.springframework.test.web.servlet.ResultActions resume(
            UUID sessionId,
            UUID participantId
    ) throws Exception {
        return performParticipantAction(sessionId, participantId, "resume");
    }

    private org.springframework.test.web.servlet.ResultActions leave(
            UUID sessionId,
            UUID participantId
    ) throws Exception {
        return performParticipantAction(sessionId, participantId, "leave");
    }

    private org.springframework.test.web.servlet.ResultActions performParticipantAction(
            UUID sessionId,
            UUID participantId,
            String action
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/sessions/{sessionId}/participants/{participantId}/" + action,
                sessionId,
                participantId));
    }

    private org.springframework.test.web.servlet.ResultActions disable(
            UUID sessionId,
            UUID sessionCourtId
    ) throws Exception {
        return performCourtAction(sessionId, sessionCourtId, "disable");
    }

    private org.springframework.test.web.servlet.ResultActions enable(
            UUID sessionId,
            UUID sessionCourtId
    ) throws Exception {
        return performCourtAction(sessionId, sessionCourtId, "enable");
    }

    private org.springframework.test.web.servlet.ResultActions performCourtAction(
            UUID sessionId,
            UUID sessionCourtId,
            String action
    ) throws Exception {
        return mockMvc.perform(post(
                "/api/sessions/{sessionId}/courts/{sessionCourtId}/" + action,
                sessionId,
                sessionCourtId));
    }

    private org.springframework.test.web.servlet.ResultActions performAction(
            String path,
            UUID sessionId
    ) throws Exception {
        return mockMvc.perform(post(path, sessionId));
    }

    private String sessionJson(UUID venueId) throws Exception {
        Instant start = Instant.now().plus(1, ChronoUnit.HOURS);
        return objectMapper.writeValueAsString(new CreateSessionRequest(
                venueId,
                "Evening Badminton",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                start,
                start.plus(2, ChronoUnit.HOURS)));
    }

    private UUID responseId(MvcResult result) throws Exception {
        return UUID.fromString(responseJson(result).get("id").asText());
    }

    private Instant responseInstant(MvcResult result, String fieldName) throws Exception {
        return Instant.parse(responseJson(result).get(fieldName).asText());
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}

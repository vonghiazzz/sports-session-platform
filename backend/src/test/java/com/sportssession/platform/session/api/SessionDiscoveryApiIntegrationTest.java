package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import com.sportssession.platform.venue.domain.Venue;
import com.sportssession.platform.venue.infrastructure.VenueEntity;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SessionDiscoveryApiIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void cleanDatabase() {
        sessionRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void emptyDatabaseReturnsEmptySessionCollection() throws Exception {
        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void listsFlatSessionMetadataAcrossVenuesAndKeepsSingleReadWorking()
            throws Exception {
        UUID firstVenueId = createVenue("Venue A");
        UUID secondVenueId = createVenue("Venue B");
        UUID firstSessionId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID secondSessionId = UUID.fromString(
                "10000000-0000-0000-0000-000000000002");
        persistSession(
                firstSessionId,
                firstVenueId,
                "Session A",
                SessionStatus.PLANNED,
                Instant.parse("2026-09-15T11:00:00Z")
        );
        persistSession(
                secondSessionId,
                secondVenueId,
                "Session B",
                SessionStatus.PLANNED,
                Instant.parse("2026-09-16T11:00:00Z")
        );

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(secondSessionId.toString()))
                .andExpect(jsonPath("$[0].venueId").value(secondVenueId.toString()))
                .andExpect(jsonPath("$[0].title").value("Session B"))
                .andExpect(jsonPath("$[0].sport").value("BADMINTON"))
                .andExpect(jsonPath("$[0].matchFormat").value("DOUBLES"))
                .andExpect(jsonPath("$[0].plannedStartAt")
                        .value("2026-09-16T11:00:00Z"))
                .andExpect(jsonPath("$[0].plannedEndAt")
                        .value("2026-09-16T13:00:00Z"))
                .andExpect(jsonPath("$[0].status").value("PLANNED"))
                .andExpect(jsonPath("$[0].participants").doesNotExist())
                .andExpect(jsonPath("$[0].courts").doesNotExist())
                .andExpect(jsonPath("$[0].matches").doesNotExist())
                .andExpect(jsonPath("$[0].personalAccessToken").doesNotExist());

        mockMvc.perform(get("/api/sessions/{sessionId}", firstSessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstSessionId.toString()));
    }

    @Test
    void ordersActivePlannedAndTerminalSessionsByOperationalPriority()
            throws Exception {
        UUID venueId = createVenue("Venue A");
        UUID cancelledId = UUID.fromString(
                "20000000-0000-0000-0000-000000000001");
        UUID completedId = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        UUID plannedId = UUID.fromString(
                "20000000-0000-0000-0000-000000000003");
        UUID inProgressId = UUID.fromString(
                "20000000-0000-0000-0000-000000000004");

        persistSession(
                cancelledId, venueId, "Cancelled", SessionStatus.CANCELLED,
                Instant.parse("2026-09-18T11:00:00Z")
        );
        persistSession(
                completedId, venueId, "Completed", SessionStatus.COMPLETED,
                Instant.parse("2026-09-19T11:00:00Z")
        );
        persistSession(
                plannedId, venueId, "Planned", SessionStatus.PLANNED,
                Instant.parse("2026-09-14T11:00:00Z")
        );
        persistSession(
                inProgressId, venueId, "In progress", SessionStatus.IN_PROGRESS,
                Instant.parse("2026-09-13T11:00:00Z")
        );

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(inProgressId.toString()))
                .andExpect(jsonPath("$[1].id").value(plannedId.toString()))
                .andExpect(jsonPath("$[2].id").value(completedId.toString()))
                .andExpect(jsonPath("$[3].id").value(cancelledId.toString()));
    }

    @Test
    void ordersSamePriorityByPlannedStartDescendingThenUuidAscending()
            throws Exception {
        UUID venueId = createVenue("Venue A");
        UUID smallerId = UUID.fromString(
                "30000000-0000-0000-0000-000000000001");
        UUID largerId = UUID.fromString(
                "30000000-0000-0000-0000-000000000002");
        UUID olderId = UUID.fromString(
                "30000000-0000-0000-0000-000000000003");
        Instant recentStart = Instant.parse("2026-09-20T11:00:00Z");

        persistSession(
                largerId, venueId, "Same time larger UUID", SessionStatus.PLANNED,
                recentStart
        );
        persistSession(
                olderId, venueId, "Older", SessionStatus.PLANNED,
                Instant.parse("2026-09-19T11:00:00Z")
        );
        persistSession(
                smallerId, venueId, "Same time smaller UUID", SessionStatus.PLANNED,
                recentStart
        );

        mockMvc.perform(get("/api/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(smallerId.toString()))
                .andExpect(jsonPath("$[1].id").value(largerId.toString()))
                .andExpect(jsonPath("$[2].id").value(olderId.toString()));
    }

    private UUID createVenue(String name) {
        Venue venue = Venue.create(name, null, true, CREATED_AT);
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private void persistSession(
            UUID id,
            UUID venueId,
            String title,
            SessionStatus status,
            Instant plannedStartAt
    ) {
        Instant startedAt = switch (status) {
            case IN_PROGRESS, COMPLETED -> plannedStartAt;
            case PLANNED, CANCELLED -> null;
        };
        Instant completedAt = status == SessionStatus.COMPLETED
                ? plannedStartAt.plusSeconds(3_600)
                : null;
        Instant cancelledAt = status == SessionStatus.CANCELLED
                ? plannedStartAt.plusSeconds(1_800)
                : null;
        Session session = new Session(
                id,
                venueId,
                title,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                plannedStartAt,
                plannedStartAt.plusSeconds(7_200),
                status,
                startedAt,
                completedAt,
                cancelledAt,
                0,
                CREATED_AT,
                CREATED_AT
        );
        sessionRepository.saveAndFlush(SessionEntity.from(session));
    }
}

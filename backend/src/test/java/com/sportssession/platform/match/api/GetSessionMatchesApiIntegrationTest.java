package com.sportssession.platform.match.api;

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
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GetSessionMatchesApiIntegrationTest extends PostgreSqlIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-08-30T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

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
    void existingSessionWithoutMatchesReturnsEmptyList() throws Exception {
        RuntimeFixture fixture = createFixture();

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void createdManualMatchIsReturned() throws Exception {
        RuntimeFixture fixture = createFixture();
        Match match = persistMatch(
                fixture,
                MatchStatus.CREATED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(10),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(match.id().toString()))
                .andExpect(jsonPath("$[0].status").value("CREATED"))
                .andExpect(jsonPath("$[0].source").value("MANUAL"))
                .andExpect(jsonPath("$[0].startedAt").doesNotExist());
    }

    @Test
    void playingMatchReturnsRuntimeIdentityAndFourAssignments()
            throws Exception {
        RuntimeFixture fixture = createFixture();
        Match match = persistMatch(
                fixture,
                MatchStatus.PLAYING,
                MatchSource.RECOMMENDATION,
                BASE_TIME.plusSeconds(20),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(match.id().toString()))
                .andExpect(jsonPath("$[0].sessionId")
                        .value(fixture.sessionId().toString()))
                .andExpect(jsonPath("$[0].sessionCourtId")
                        .value(fixture.sessionCourtId().toString()))
                .andExpect(jsonPath("$[0].status").value("PLAYING"))
                .andExpect(jsonPath("$[0].source").value("RECOMMENDATION"))
                .andExpect(jsonPath("$[0].participants.length()").value(4));
    }

    @Test
    void completedMatchReturnsResultAndResultVersion() throws Exception {
        RuntimeFixture fixture = createFixture();
        Match match = persistMatch(
                fixture,
                MatchStatus.COMPLETED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(30),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(match.id().toString()))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].winnerTeam").value("A"))
                .andExpect(jsonPath("$[0].teamAScore").value(21))
                .andExpect(jsonPath("$[0].teamBScore").value(18))
                .andExpect(jsonPath("$[0].resultVersion").value(1))
                .andExpect(jsonPath("$[0].completedAt").isNotEmpty());
    }

    @Test
    void cancelledMatchIsReturned() throws Exception {
        RuntimeFixture fixture = createFixture();
        Match match = persistMatch(
                fixture,
                MatchStatus.CANCELLED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(40),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(match.id().toString()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].winnerTeam").doesNotExist())
                .andExpect(jsonPath("$[0].resultVersion").value(0))
                .andExpect(jsonPath("$[0].cancelledAt").isNotEmpty());
    }

    @Test
    void multipleMatchesUseCreatedAtThenMatchIdOrdering() throws Exception {
        RuntimeFixture fixture = createFixture();
        UUID lowerId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001"
        );
        UUID higherId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002"
        );
        UUID earliestId = UUID.fromString(
                "ffffffff-ffff-ffff-ffff-ffffffffffff"
        );

        persistMatch(
                fixture,
                MatchStatus.CREATED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(60),
                higherId
        );
        persistMatch(
                fixture,
                MatchStatus.CANCELLED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(50),
                earliestId
        );
        persistMatch(
                fixture,
                MatchStatus.PLAYING,
                MatchSource.RECOMMENDATION,
                BASE_TIME.plusSeconds(60),
                lowerId
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(earliestId.toString()))
                .andExpect(jsonPath("$[1].id").value(lowerId.toString()))
                .andExpect(jsonPath("$[2].id").value(higherId.toString()));
    }

    @Test
    void participantsUseA1A2B1B2Ordering() throws Exception {
        RuntimeFixture fixture = createFixture();
        persistMatch(
                fixture,
                MatchStatus.CREATED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(70),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participants[0].teamSide")
                        .value("A"))
                .andExpect(jsonPath("$[0].participants[0].teamSlot").value(1))
                .andExpect(jsonPath("$[0].participants[1].teamSide")
                        .value("A"))
                .andExpect(jsonPath("$[0].participants[1].teamSlot").value(2))
                .andExpect(jsonPath("$[0].participants[2].teamSide")
                        .value("B"))
                .andExpect(jsonPath("$[0].participants[2].teamSlot").value(1))
                .andExpect(jsonPath("$[0].participants[3].teamSide")
                        .value("B"))
                .andExpect(jsonPath("$[0].participants[3].teamSlot").value(2));
    }

    @Test
    void matchesFromAnotherSessionAreExcluded() throws Exception {
        RuntimeFixture requested = createFixture();
        RuntimeFixture other = createFixture();
        Match included = persistMatch(
                requested,
                MatchStatus.CREATED,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(80),
                UUID.randomUUID()
        );
        persistMatch(
                other,
                MatchStatus.PLAYING,
                MatchSource.RECOMMENDATION,
                BASE_TIME.plusSeconds(1),
                UUID.randomUUID()
        );

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        requested.sessionId()
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(included.id().toString()))
                .andExpect(jsonPath("$[0].sessionId")
                        .value(requested.sessionId().toString()));
    }

    @Test
    void nonexistentSessionReturnsNotFoundApiError() throws Exception {
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        sessionId
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Session not found: " + sessionId))
                .andExpect(jsonPath("$.path")
                        .value("/api/sessions/" + sessionId + "/matches"));
    }

    @Test
    void getDoesNotMutateMatchCourtOrParticipants() throws Exception {
        RuntimeFixture fixture = createFixture();
        Match match = persistMatch(
                fixture,
                MatchStatus.PLAYING,
                MatchSource.MANUAL,
                BASE_TIME.plusSeconds(90),
                UUID.randomUUID()
        );
        applyPlayingRuntime(fixture, match.startedAt());

        MatchStatus matchStatusBefore = matchRepository.findById(match.id())
                .orElseThrow()
                .getStatus();
        long matchVersionBefore = matchRepository.findById(match.id())
                .orElseThrow()
                .getVersion();
        SessionCourtEntity courtBefore = sessionCourtRepository
                .findById(fixture.sessionCourtId())
                .orElseThrow();
        SessionCourtStatus courtStatusBefore = courtBefore.getStatus();
        long courtVersionBefore = courtBefore.getVersion();
        List<ParticipantState> participantStatesBefore = participantStates(fixture);

        mockMvc.perform(get(
                        "/api/sessions/{sessionId}/matches",
                        fixture.sessionId()
                ))
                .andExpect(status().isOk());

        MatchEntity matchAfter = matchRepository.findById(match.id())
                .orElseThrow();
        SessionCourtEntity courtAfter = sessionCourtRepository
                .findById(fixture.sessionCourtId())
                .orElseThrow();
        assertThat(matchAfter.getStatus()).isEqualTo(matchStatusBefore);
        assertThat(matchAfter.getVersion()).isEqualTo(matchVersionBefore);
        assertThat(courtAfter.getStatus()).isEqualTo(courtStatusBefore);
        assertThat(courtAfter.getVersion()).isEqualTo(courtVersionBefore);
        assertThat(participantStates(fixture))
                .containsExactlyElementsOf(participantStatesBefore);
    }

    private RuntimeFixture createFixture() {
        Instant now = BASE_TIME.minus(1, ChronoUnit.HOURS);
        Venue venue = Venue.create(
                "Venue " + UUID.randomUUID(),
                null,
                true,
                now
        );
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
                "Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        ).start(now.plusSeconds(1));
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();

        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                now.plusSeconds(2)
        );
        UUID sessionCourtId = sessionCourtRepository
                .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                .getId();

        List<UUID> participantIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Player player = Player.create(
                    "Player " + UUID.randomUUID(),
                    now.plusSeconds(3 + index)
            );
            UUID playerId = playerRepository
                    .saveAndFlush(PlayerEntity.from(player))
                    .getId();
            SessionParticipant participant = SessionParticipant.register(
                    sessionId,
                    playerId,
                    now.plusSeconds(10 + index)
            ).checkIn(now.plusSeconds(20 + index));
            participantIds.add(sessionParticipantRepository
                    .saveAndFlush(SessionParticipantEntity.from(participant))
                    .getId());
        }

        return new RuntimeFixture(sessionId, sessionCourtId, participantIds);
    }

    private Match persistMatch(
            RuntimeFixture fixture,
            MatchStatus status,
            MatchSource source,
            Instant createdAt,
            UUID matchId
    ) {
        Match created = Match.create(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                source,
                createdAt
        );
        created = withId(created, matchId);
        Match match = switch (status) {
            case CREATED -> created;
            case PLAYING -> created.start(createdAt.plusSeconds(1));
            case COMPLETED -> created
                    .start(createdAt.plusSeconds(1))
                    .complete(
                            new MatchResult(TeamSide.A, 21, 18),
                            createdAt.plusSeconds(2)
                    );
            case CANCELLED -> created.cancel(createdAt.plusSeconds(1));
        };

        matchRepository.saveAndFlush(MatchEntity.from(match));
        List<MatchParticipant> participants = List.of(
                MatchParticipant.assign(
                        match.id(),
                        fixture.participantIds().get(3),
                        TeamSide.B,
                        2
                ),
                MatchParticipant.assign(
                        match.id(),
                        fixture.participantIds().get(1),
                        TeamSide.A,
                        2
                ),
                MatchParticipant.assign(
                        match.id(),
                        fixture.participantIds().get(2),
                        TeamSide.B,
                        1
                ),
                MatchParticipant.assign(
                        match.id(),
                        fixture.participantIds().get(0),
                        TeamSide.A,
                        1
                )
        );
        matchParticipantRepository.saveAllAndFlush(
                participants.stream()
                        .map(MatchParticipantEntity::from)
                        .toList()
        );
        return match;
    }

    private Match withId(Match match, UUID matchId) {
        return new Match(
                matchId,
                match.sessionId(),
                match.sessionCourtId(),
                match.status(),
                match.source(),
                match.result(),
                match.resultVersion(),
                match.createdAt(),
                match.startedAt(),
                match.completedAt(),
                match.cancelledAt(),
                match.updatedAt(),
                match.version()
        );
    }

    private void applyPlayingRuntime(
            RuntimeFixture fixture,
            Instant startedAt
    ) {
        SessionCourtEntity courtEntity = sessionCourtRepository
                .findById(fixture.sessionCourtId())
                .orElseThrow();
        courtEntity.applyRuntimeState(
                courtEntity.toDomain().startMatch(startedAt)
        );
        sessionCourtRepository.saveAndFlush(courtEntity);

        fixture.participantIds().forEach(participantId -> {
            SessionParticipantEntity participantEntity =
                    sessionParticipantRepository
                            .findById(participantId)
                            .orElseThrow();
            participantEntity.applyRuntimeState(
                    participantEntity.toDomain().startMatch(startedAt)
            );
            sessionParticipantRepository.saveAndFlush(participantEntity);
        });
    }

    private List<ParticipantState> participantStates(RuntimeFixture fixture) {
        return fixture.participantIds().stream()
                .map(participantId -> sessionParticipantRepository
                        .findById(participantId)
                        .orElseThrow())
                .map(participant -> new ParticipantState(
                        participant.getId(),
                        participant.getStatus(),
                        participant.getWaitingSince(),
                        participant.getVersion()
                ))
                .toList();
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

    private record ParticipantState(
            UUID id,
            ParticipantStatus status,
            Instant waitingSince,
            long version
    ) {
    }
}

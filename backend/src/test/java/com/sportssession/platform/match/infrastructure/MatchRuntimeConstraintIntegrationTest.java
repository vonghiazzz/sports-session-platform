package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchRuntimeConstraintIntegrationTest extends PostgreSqlIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void duplicateSessionParticipantInOneMatchIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createMatch(fixture, false);
        UUID sessionParticipantId = createSessionParticipant(fixture.sessionId());

        saveMatchParticipant(
                matchId, sessionParticipantId, TeamSide.A, 1
        );

        assertThatThrownBy(() -> saveMatchParticipant(
                matchId, sessionParticipantId, TeamSide.B, 1
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining(
                        "uk_match_participants_match_session_participant"
                );
    }

    @Test
    void duplicateTeamSlotInOneMatchIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createMatch(fixture, false);

        saveMatchParticipant(
                matchId,
                createSessionParticipant(fixture.sessionId()),
                TeamSide.A,
                1
        );

        assertThatThrownBy(() -> saveMatchParticipant(
                matchId,
                createSessionParticipant(fixture.sessionId()),
                TeamSide.A,
                1
        ))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_match_participants_match_team_slot");
    }

    @Test
    void invalidTeamSideIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createMatch(fixture, false);
        UUID sessionParticipantId = createSessionParticipant(fixture.sessionId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO match_participants (
                    id, match_id, session_participant_id, team_side, team_slot
                ) VALUES (?, ?, ?, 'C', 1)
                """, UUID.randomUUID(), matchId, sessionParticipantId))
                .rootCause()
                .hasMessageContaining("chk_match_participants_team_side");
    }

    @Test
    void invalidTeamSlotIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();
        UUID matchId = createMatch(fixture, false);
        UUID sessionParticipantId = createSessionParticipant(fixture.sessionId());

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO match_participants (
                    id, match_id, session_participant_id, team_side, team_slot
                ) VALUES (?, ?, ?, 'A', 3)
                """, UUID.randomUUID(), matchId, sessionParticipantId))
                .rootCause()
                .hasMessageContaining("chk_match_participants_team_slot");
    }

    @Test
    void invalidMatchStatusAndSourceAreRejected() {
        RuntimeFixture fixture = createRuntimeFixture();

        assertThatThrownBy(() -> insertRawMatch(
                fixture,
                "UNKNOWN",
                "MANUAL",
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null
        ))
                .rootCause()
                .hasMessageContaining("chk_matches_status");

        assertThatThrownBy(() -> insertRawMatch(
                fixture,
                "CREATED",
                "IMPORT",
                null,
                null,
                null,
                0,
                0,
                null,
                null,
                null
        ))
                .rootCause()
                .hasMessageContaining("chk_matches_source");
    }

    @Test
    void negativeResultVersionIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();

        assertThatThrownBy(() -> insertRawMatch(
                fixture,
                "CREATED",
                "MANUAL",
                null,
                null,
                null,
                -1,
                0,
                null,
                null,
                null
        ))
                .rootCause()
                .hasMessageContaining("chk_matches_result_version_non_negative");
    }

    @Test
    void negativeVersionIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();

        assertThatThrownBy(() -> insertRawMatch(
                fixture,
                "CREATED",
                "MANUAL",
                null,
                null,
                null,
                0,
                -1,
                null,
                null,
                null
        ))
                .rootCause()
                .hasMessageContaining("chk_matches_version_non_negative");
    }

    @Test
    void twoPlayingMatchesCannotUseTheSameSessionCourt() {
        RuntimeFixture fixture = createRuntimeFixture();
        createMatch(fixture, true);

        assertThatThrownBy(() -> createMatch(fixture, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("uk_matches_playing_session_court");
    }

    @Test
    void multipleNonPlayingMatchesMayUseTheSameSessionCourt() {
        RuntimeFixture fixture = createRuntimeFixture();

        createMatch(fixture, false);
        createMatch(fixture, false);

        assertThat(matchRepository.count()).isEqualTo(2);
    }

    @Test
    void invalidWinnerScoreCombinationIsRejected() {
        RuntimeFixture fixture = createRuntimeFixture();
        Instant startedAt = Instant.now();

        assertThatThrownBy(() -> insertRawMatch(
                fixture,
                "COMPLETED",
                "MANUAL",
                "A",
                10,
                21,
                1,
                0,
                startedAt,
                startedAt.plusSeconds(300),
                null
        ))
                .rootCause()
                .hasMessageContaining("chk_matches_winner_score_consistency");
    }

    private RuntimeFixture createRuntimeFixture() {
        Instant now = Instant.now();
        Venue venue = Venue.create("Venue A", null, true, now);
        UUID venueId = venueRepository
                .saveAndFlush(VenueEntity.from(venue))
                .getId();

        Court court = Court.create(
                venueId,
                "Court 1",
                SportCode.BADMINTON,
                true,
                now
        );
        UUID courtId = courtRepository
                .saveAndFlush(CourtEntity.from(court))
                .getId();

        Session session = Session.create(
                venueId,
                "Evening Badminton",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now
        );
        UUID sessionId = sessionRepository
                .saveAndFlush(SessionEntity.from(session))
                .getId();

        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                now
        );
        UUID sessionCourtId = sessionCourtRepository
                .saveAndFlush(SessionCourtEntity.from(sessionCourt))
                .getId();

        return new RuntimeFixture(sessionId, sessionCourtId);
    }

    private UUID createSessionParticipant(UUID sessionId) {
        Instant now = Instant.now();
        Player player = Player.create("Player " + UUID.randomUUID(), now);
        UUID playerId = playerRepository
                .saveAndFlush(PlayerEntity.from(player))
                .getId();

        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                now
        );
        return sessionParticipantRepository
                .saveAndFlush(SessionParticipantEntity.from(participant))
                .getId();
    }

    private UUID createMatch(RuntimeFixture fixture, boolean playing) {
        Instant now = Instant.now();
        Match match = Match.create(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                MatchSource.MANUAL,
                now
        );
        if (playing) {
            match = match.start(now.plusSeconds(1));
        }
        return matchRepository
                .saveAndFlush(MatchEntity.from(match))
                .getId();
    }

    private void saveMatchParticipant(
            UUID matchId,
            UUID sessionParticipantId,
            TeamSide teamSide,
            int teamSlot
    ) {
        MatchParticipant participant = MatchParticipant.assign(
                matchId,
                sessionParticipantId,
                teamSide,
                teamSlot
        );
        matchParticipantRepository.saveAndFlush(
                MatchParticipantEntity.from(participant)
        );
    }

    private void insertRawMatch(
            RuntimeFixture fixture,
            String status,
            String source,
            String winnerTeam,
            Integer teamAScore,
            Integer teamBScore,
            int resultVersion,
            long version,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
        OffsetDateTime databaseNow = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO matches (
                    id,
                    session_id,
                    session_court_id,
                    status,
                    source,
                    winner_team,
                    team_a_score,
                    team_b_score,
                    result_version,
                    created_at,
                    started_at,
                    completed_at,
                    cancelled_at,
                    updated_at,
                    version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                fixture.sessionId(),
                fixture.sessionCourtId(),
                status,
                source,
                winnerTeam,
                teamAScore,
                teamBScore,
                resultVersion,
                databaseNow,
                toDatabaseTime(startedAt),
                toDatabaseTime(completedAt),
                toDatabaseTime(cancelledAt),
                databaseNow,
                version
        );
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private record RuntimeFixture(UUID sessionId, UUID sessionCourtId) {
    }
}

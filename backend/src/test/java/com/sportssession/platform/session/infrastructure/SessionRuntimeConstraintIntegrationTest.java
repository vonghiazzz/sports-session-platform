package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionParticipant;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionRuntimeConstraintIntegrationTest extends PostgreSqlIntegrationTest {

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
    void uniqueSessionAndPlayerConstraintIsEnforced() {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId);
        UUID playerId = createPlayer();
        Instant now = Instant.now();

        participantRepository.saveAndFlush(SessionParticipantEntity.from(
                SessionParticipant.register(sessionId, playerId, now)));

        assertThatThrownBy(() -> participantRepository.saveAndFlush(
                SessionParticipantEntity.from(
                        SessionParticipant.register(sessionId, playerId, now))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueSessionAndCourtConstraintIsEnforced() {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId);
        UUID courtId = createCourt(venueId);
        Instant now = Instant.now();

        sessionCourtRepository.saveAndFlush(SessionCourtEntity.from(
                SessionCourt.allocate(sessionId, courtId, now)));

        assertThatThrownBy(() -> sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(SessionCourt.allocate(sessionId, courtId, now))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidParticipantStateAndTimestampCombinationIsRejected() {
        UUID sessionId = createSession(createVenue());
        UUID playerId = createPlayer();
        Instant now = Instant.now();
        var databaseTime = now.atOffset(ZoneOffset.UTC);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO session_participants (
                    id,
                    session_id,
                    player_id,
                    status,
                    joined_at,
                    checked_in_at,
                    waiting_since,
                    paused_at,
                    total_paused_seconds,
                    left_at,
                    version,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 'WAITING', ?, NULL, NULL, NULL, 0, NULL, 0, ?, ?)
                """, UUID.randomUUID(), sessionId, playerId,
                        databaseTime, databaseTime, databaseTime))
                .rootCause()
                .hasMessageContaining("chk_session_participants_state_timestamps");
    }

    private UUID createVenue() {
        Venue venue = Venue.create("Venue A", null, true, Instant.now());
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId) {
        Court court = Court.create(
                venueId, "Court 1", SportCode.BADMINTON, true, Instant.now());
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createPlayer() {
        Player player = Player.create("Player A", Instant.now());
        return playerRepository.saveAndFlush(PlayerEntity.from(player)).getId();
    }

    private UUID createSession(UUID venueId) {
        Instant now = Instant.now();
        Session session = Session.create(
                venueId,
                "Evening Badminton",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS),
                now);
        return sessionRepository.saveAndFlush(SessionEntity.from(session)).getId();
    }
}

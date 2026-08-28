package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.matchmaking.application.MatchmakingSessionParticipantSnapshot;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshot;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotException;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionSnapshotReader;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingSessionSnapshotReaderIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-08-28T03:00:00Z");

    @Autowired
    private MatchmakingSessionSnapshotReader snapshotReader;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionCourtRepository sessionCourtRepository;

    @Autowired
    private SessionParticipantRepository participantRepository;

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
        sessionCourtRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void returnsAuthoritativeSessionCourtAndAllParticipantStatesReadOnly() {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId, SessionStatus.IN_PROGRESS);
        UUID sessionCourtId = createSessionCourt(
                sessionId,
                createCourt(venueId),
                SessionCourtStatus.AVAILABLE
        );
        createParticipant(sessionId, 5, ParticipantStatus.LEFT);
        createParticipant(sessionId, 3, ParticipantStatus.PLAYING);
        createParticipant(sessionId, 1, ParticipantStatus.REGISTERED);
        Instant waitingSince = createParticipant(
                sessionId,
                2,
                ParticipantStatus.WAITING
        ).waitingSince();
        createParticipant(sessionId, 4, ParticipantStatus.PAUSED);
        Counts before = counts();

        MatchmakingSessionSnapshot snapshot = snapshotReader.load(
                sessionId,
                sessionCourtId
        );

        assertThat(snapshot.sessionId()).isEqualTo(sessionId);
        assertThat(snapshot.sportCode()).isEqualTo(SportCode.BADMINTON);
        assertThat(snapshot.matchFormat()).isEqualTo(MatchFormat.DOUBLES);
        assertThat(snapshot.sessionStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(snapshot.sessionCourtId()).isEqualTo(sessionCourtId);
        assertThat(snapshot.sessionCourtStatus())
                .isEqualTo(SessionCourtStatus.AVAILABLE);
        assertThat(snapshot.participants())
                .extracting(MatchmakingSessionParticipantSnapshot::playerId)
                .containsExactly(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5));
        assertThat(snapshot.participants())
                .extracting(
                        MatchmakingSessionParticipantSnapshot::participantStatus
                )
                .containsExactly(
                        ParticipantStatus.REGISTERED,
                        ParticipantStatus.WAITING,
                        ParticipantStatus.PLAYING,
                        ParticipantStatus.PAUSED,
                        ParticipantStatus.LEFT
                );
        assertThat(snapshot.participants().get(1).waitingSince())
                .isEqualTo(waitingSince);
        assertThat(snapshot.participants())
                .filteredOn(participant -> participant.participantStatus()
                        != ParticipantStatus.WAITING)
                .extracting(MatchmakingSessionParticipantSnapshot::waitingSince)
                .containsOnlyNulls();
        assertThatThrownBy(() -> snapshot.participants().add(
                snapshot.participants().getFirst()
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void missingSessionFailsWithConsumerOwnedReason() {
        UUID sessionId = UUID.randomUUID();
        UUID sessionCourtId = UUID.randomUUID();

        assertThatThrownBy(() -> snapshotReader.load(sessionId, sessionCourtId))
                .isInstanceOfSatisfying(
                        MatchmakingSessionSnapshotException.class,
                        exception -> {
                            assertThat(exception.reason()).isEqualTo(
                                    MatchmakingSessionSnapshotFailureReason
                                            .SESSION_NOT_FOUND
                            );
                            assertThat(exception.sessionId()).isEqualTo(sessionId);
                            assertThat(exception.sessionCourtId())
                                    .isEqualTo(sessionCourtId);
                        }
                );
    }

    @Test
    void missingCourtForSessionFailsWithConsumerOwnedReason() {
        UUID sessionId = createSession(createVenue(), SessionStatus.PLANNED);
        UUID sessionCourtId = UUID.randomUUID();

        assertCourtNotFoundForSession(sessionId, sessionCourtId);
    }

    @Test
    void courtBelongingToAnotherSessionIsNotExposed() {
        UUID venueId = createVenue();
        UUID requestedSessionId = createSession(
                venueId,
                SessionStatus.IN_PROGRESS
        );
        UUID owningSessionId = createSession(venueId, SessionStatus.IN_PROGRESS);
        UUID otherSessionCourtId = createSessionCourt(
                owningSessionId,
                createCourt(venueId),
                SessionCourtStatus.AVAILABLE
        );

        assertCourtNotFoundForSession(requestedSessionId, otherSessionCourtId);
    }

    @ParameterizedTest
    @EnumSource(
            value = SessionStatus.class,
            names = {"PLANNED", "COMPLETED", "CANCELLED"}
    )
    void terminalAndNotStartedSessionStatesAreProjected(SessionStatus status) {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId, status);
        UUID sessionCourtId = createSessionCourt(
                sessionId,
                createCourt(venueId),
                SessionCourtStatus.AVAILABLE
        );

        MatchmakingSessionSnapshot snapshot = snapshotReader.load(
                sessionId,
                sessionCourtId
        );

        assertThat(snapshot.sessionStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(
            value = SessionCourtStatus.class,
            names = {"PLAYING", "UNAVAILABLE"}
    )
    void nonAvailableCourtStatesAreProjected(SessionCourtStatus status) {
        UUID venueId = createVenue();
        UUID sessionId = createSession(venueId, SessionStatus.IN_PROGRESS);
        UUID sessionCourtId = createSessionCourt(
                sessionId,
                createCourt(venueId),
                status
        );

        MatchmakingSessionSnapshot snapshot = snapshotReader.load(
                sessionId,
                sessionCourtId
        );

        assertThat(snapshot.sessionCourtStatus()).isEqualTo(status);
    }

    private void assertCourtNotFoundForSession(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        assertThatThrownBy(() -> snapshotReader.load(sessionId, sessionCourtId))
                .isInstanceOfSatisfying(
                        MatchmakingSessionSnapshotException.class,
                        exception -> {
                            assertThat(exception.reason()).isEqualTo(
                                    MatchmakingSessionSnapshotFailureReason
                                            .SESSION_COURT_NOT_FOUND_FOR_SESSION
                            );
                            assertThat(exception.sessionId()).isEqualTo(sessionId);
                            assertThat(exception.sessionCourtId())
                                    .isEqualTo(sessionCourtId);
                        }
                );
    }

    private UUID createVenue() {
        Venue venue = Venue.create(
                "Matchmaking Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId) {
        Court court = Court.create(
                venueId,
                "Matchmaking Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createSession(UUID venueId, SessionStatus status) {
        Session session = Session.create(
                venueId,
                "Matchmaking Session " + UUID.randomUUID(),
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                BASE_TIME.plus(1, ChronoUnit.HOURS),
                BASE_TIME.plus(3, ChronoUnit.HOURS),
                BASE_TIME
        );
        session = switch (status) {
            case PLANNED -> session;
            case IN_PROGRESS -> session.start(BASE_TIME.plusSeconds(1));
            case COMPLETED -> session.start(BASE_TIME.plusSeconds(1))
                    .complete(BASE_TIME.plusSeconds(2));
            case CANCELLED -> session.cancel(BASE_TIME.plusSeconds(1));
        };
        return sessionRepository.saveAndFlush(SessionEntity.from(session)).getId();
    }

    private UUID createSessionCourt(
            UUID sessionId,
            UUID courtId,
            SessionCourtStatus status
    ) {
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                BASE_TIME.plusSeconds(10)
        );
        sessionCourt = switch (status) {
            case AVAILABLE -> sessionCourt;
            case PLAYING -> sessionCourt.startMatch(BASE_TIME.plusSeconds(11));
            case UNAVAILABLE -> sessionCourt.disable(BASE_TIME.plusSeconds(11));
        };
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private SessionParticipant createParticipant(
            UUID sessionId,
            int playerNumber,
            ParticipantStatus status
    ) {
        UUID playerId = uuid(playerNumber);
        Player player = new Player(
                playerId,
                "Player " + playerNumber,
                BASE_TIME,
                BASE_TIME
        );
        playerRepository.saveAndFlush(PlayerEntity.from(player));
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                BASE_TIME.plusSeconds(20)
        );
        participant = switch (status) {
            case REGISTERED -> participant;
            case WAITING -> participant.checkIn(BASE_TIME.plusSeconds(21));
            case PLAYING -> participant.checkIn(BASE_TIME.plusSeconds(21))
                    .startMatch(BASE_TIME.plusSeconds(22));
            case PAUSED -> participant.checkIn(BASE_TIME.plusSeconds(21))
                    .pause(BASE_TIME.plusSeconds(22));
            case LEFT -> participant.leave(BASE_TIME.plusSeconds(21));
        };
        participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        );
        return participant;
    }

    private Counts counts() {
        return new Counts(
                sessionRepository.count(),
                sessionCourtRepository.count(),
                participantRepository.count(),
                playerRepository.count()
        );
    }

    private UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record Counts(
            long sessions,
            long sessionCourts,
            long participants,
            long players
    ) {
    }
}

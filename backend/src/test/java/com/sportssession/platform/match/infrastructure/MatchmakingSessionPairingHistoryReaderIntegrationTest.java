package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionPairingHistoryReader;
import com.sportssession.platform.matchmaking.domain.CompletedMatchPairing;
import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchmakingSessionPairingHistoryReaderIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-12T02:00:00Z");

    @Autowired
    private MatchmakingSessionPairingHistoryReader reader;
    @Autowired
    private MatchParticipantRepository matchParticipantRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private SessionCourtRepository sessionCourtRepository;
    @Autowired
    private SessionParticipantRepository participantRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private VenueRepository venueRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @BeforeEach
    void cleanDatabase() {
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void readsOnlyCompletedPairingsForRequestedSessionWithoutMutation() {
        Fixture current = createFixture(1, 8);
        Fixture other = createFixture(2, 4);
        UUID completedId = uuid(9101);

        saveMatch(
                current,
                completedId,
                MatchStatus.COMPLETED,
                NOW.plusSeconds(20),
                List.of(0, 1, 2, 3)
        );
        saveMatch(
                current,
                uuid(9102),
                MatchStatus.CREATED,
                NOW.plusSeconds(21),
                List.of(0, 2, 1, 3)
        );
        saveMatch(
                current,
                uuid(9103),
                MatchStatus.PLAYING,
                NOW.plusSeconds(22),
                List.of(0, 2, 1, 3)
        );
        saveMatch(
                current,
                uuid(9104),
                MatchStatus.CANCELLED,
                NOW.plusSeconds(23),
                List.of(0, 2, 1, 3)
        );
        saveMatch(
                other,
                uuid(9201),
                MatchStatus.COMPLETED,
                NOW.plusSeconds(30),
                List.of(0, 1, 2, 3)
        );
        long matchRowsBefore = matchRepository.count();
        long participantRowsBefore = matchParticipantRepository.count();

        MatchmakingSessionPairingHistory history =
                reader.readCompletedPairingHistory(current.sessionId());

        assertThat(history.sessionId()).isEqualTo(current.sessionId());
        assertThat(history.completedMatches())
                .extracting(CompletedMatchPairing::matchId)
                .containsExactly(completedId);
        UUID first = current.participantIds().get(0);
        UUID second = current.participantIds().get(1);
        UUID third = current.participantIds().get(2);
        UUID fourth = current.participantIds().get(3);
        assertThat(history.teammateMatchCount(first, second)).isEqualTo(1);
        assertThat(history.teammateMatchCount(third, fourth)).isEqualTo(1);
        assertThat(history.teammateMatchCount(first, third)).isZero();
        assertThat(history.opponentMatchCount(first, third)).isEqualTo(1);
        assertThat(history.opponentMatchCount(first, fourth)).isEqualTo(1);
        assertThat(history.opponentMatchCount(first, second)).isZero();
        assertThat(matchRepository.count()).isEqualTo(matchRowsBefore);
        assertThat(matchParticipantRepository.count())
                .isEqualTo(participantRowsBefore);
    }

    @Test
    void ordersLatestAnchorMatchByCompletedAtThenMatchId() {
        Fixture fixture = createFixture(3, 8);
        UUID olderLargerId = uuid(9303);
        UUID newerSmallerId = uuid(9301);
        UUID newerLargerId = uuid(9302);

        saveMatch(
                fixture,
                olderLargerId,
                MatchStatus.COMPLETED,
                NOW.plusSeconds(20),
                List.of(0, 1, 2, 3)
        );
        saveMatch(
                fixture,
                newerSmallerId,
                MatchStatus.COMPLETED,
                NOW.plusSeconds(30),
                List.of(0, 2, 4, 5)
        );
        saveMatch(
                fixture,
                newerLargerId,
                MatchStatus.COMPLETED,
                NOW.plusSeconds(30),
                List.of(0, 3, 6, 7)
        );

        MatchmakingSessionPairingHistory history =
                reader.readCompletedPairingHistory(fixture.sessionId());

        assertThat(history.completedMatches())
                .extracting(CompletedMatchPairing::matchId)
                .containsExactly(
                        newerLargerId,
                        newerSmallerId,
                        olderLargerId
                );
        assertThat(history.isImmediateQuartetRepeat(
                fixture.participantIds().get(0),
                participantIds(fixture, 0, 3, 6, 7)
        )).isTrue();
        assertThat(history.isImmediateQuartetRepeat(
                fixture.participantIds().get(0),
                participantIds(fixture, 0, 2, 4, 5)
        )).isFalse();
    }

    private void saveMatch(
            Fixture fixture,
            UUID matchId,
            MatchStatus status,
            Instant terminalTime,
            List<Integer> participantIndexes
    ) {
        Match created = new Match(
                matchId,
                fixture.sessionId(),
                fixture.sessionCourtId(),
                MatchStatus.CREATED,
                MatchSource.RECOMMENDATION,
                null,
                0,
                NOW.plusSeconds(2),
                null,
                null,
                null,
                NOW.plusSeconds(2),
                0
        );
        Match match = switch (status) {
            case CREATED -> created;
            case PLAYING -> created.start(NOW.plusSeconds(3));
            case COMPLETED -> created.start(NOW.plusSeconds(3)).complete(
                    new MatchResult(TeamSide.A, 21, 15),
                    terminalTime
            );
            case CANCELLED -> created.start(NOW.plusSeconds(3))
                    .cancel(terminalTime);
        };
        matchRepository.saveAndFlush(MatchEntity.from(match));
        for (int slot = 0; slot < participantIndexes.size(); slot++) {
            TeamSide side = slot < 2 ? TeamSide.A : TeamSide.B;
            int teamSlot = slot % 2 + 1;
            UUID participantId = fixture.participantIds().get(
                    participantIndexes.get(slot)
            );
            matchParticipantRepository.saveAndFlush(
                    MatchParticipantEntity.from(MatchParticipant.assign(
                            match.id(),
                            participantId,
                            side,
                            teamSlot
                    ))
            );
        }
    }

    private Fixture createFixture(int number, int participantCount) {
        Venue venue = Venue.create(
                "Pairing Venue " + number,
                null,
                true,
                NOW
        );
        UUID venueId = venueRepository.saveAndFlush(
                VenueEntity.from(venue)
        ).getId();
        Court court = Court.create(
                venueId,
                "Pairing Court " + number,
                SportCode.BADMINTON,
                true,
                NOW
        );
        UUID courtId = courtRepository.saveAndFlush(
                CourtEntity.from(court)
        ).getId();
        Session session = Session.create(
                venueId,
                "Pairing Session " + number,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                NOW.plus(1, ChronoUnit.HOURS),
                NOW.plus(3, ChronoUnit.HOURS),
                NOW
        ).start(NOW.plusSeconds(1));
        UUID sessionId = sessionRepository.saveAndFlush(
                SessionEntity.from(session)
        ).getId();
        SessionCourt sessionCourt = SessionCourt.allocate(
                sessionId,
                courtId,
                NOW.plusSeconds(1)
        );
        UUID sessionCourtId = sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
        List<UUID> participantIds = java.util.stream.IntStream
                .range(0, participantCount)
                .mapToObj(index -> createParticipant(
                        sessionId,
                        number * 100 + index
                ))
                .toList();
        return new Fixture(sessionId, sessionCourtId, participantIds);
    }

    private UUID createParticipant(UUID sessionId, int number) {
        UUID playerId = UUID.nameUUIDFromBytes(
                ("pairing-player-" + number).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );
        playerRepository.saveAndFlush(PlayerEntity.from(new Player(
                playerId,
                "Pairing Player " + number,
                NOW,
                NOW
        )));
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                NOW.plusSeconds(1)
        ).checkIn(NOW.plusSeconds(2));
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private static List<UUID> participantIds(
            Fixture fixture,
            int... indexes
    ) {
        return java.util.Arrays.stream(indexes)
                .mapToObj(index -> fixture.participantIds().get(index))
                .toList();
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record Fixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
    }
}

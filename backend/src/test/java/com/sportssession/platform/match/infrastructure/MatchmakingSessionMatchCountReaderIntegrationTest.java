package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionMatchCountReader;
import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantRepository;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.session.domain.ParticipantStatus;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingSessionMatchCountReaderIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-10T02:00:00Z");

    @Autowired
    private MatchmakingSessionMatchCountReader reader;
    @Autowired
    private MatchPlanParticipantRepository planParticipantRepository;
    @Autowired
    private MatchPlanRepository planRepository;
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
        planParticipantRepository.deleteAll();
        planRepository.deleteAll();
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
    void returnsZeroForEveryRequestedParticipantWithoutCompletedMatches() {
        Fixture fixture = createFixture(1);

        Map<UUID, Integer> result = reader.readCompletedMatchCounts(
                fixture.sessionId(),
                fixture.participantIds()
        );

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                fixture.participantIds().get(0), 0,
                fixture.participantIds().get(1), 0
        ));
        assertThatThrownBy(() -> result.put(
                fixture.participantIds().getFirst(),
                99
        )).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void countsOnlyDistinctCompletedMatchesInTheRequestedSession() {
        Fixture current = createFixture(1);
        Fixture other = createFixture(10);
        UUID first = current.participantIds().getFirst();
        UUID second = current.participantIds().getLast();

        saveMatch(current, first, MatchStatus.COMPLETED);
        saveMatch(current, first, MatchStatus.COMPLETED);
        saveMatch(current, first, MatchStatus.CREATED);
        saveMatch(current, first, MatchStatus.PLAYING);
        saveMatch(current, first, MatchStatus.CANCELLED);
        saveMatch(other, other.participantIds().getFirst(), MatchStatus.COMPLETED);

        // V3 does not have a composite cross-Session FK. The reader's join
        // deliberately rejects this malformed relationship.
        saveMatch(current, other.participantIds().getLast(), MatchStatus.COMPLETED);

        Map<UUID, Integer> result = reader.readCompletedMatchCounts(
                current.sessionId(),
                List.of(first, second, other.participantIds().getLast())
        );

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                first, 2,
                second, 0,
                other.participantIds().getLast(), 0
        ));
    }

    @Test
    void matchPlansDoNotCountUntilTheirActualMatchCompletes() {
        Fixture fixture = createFixture(1);
        UUID participantId = fixture.participantIds().getFirst();

        MatchPlan queued = MatchPlan.queueRecommendation(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                1,
                NOW
        );
        MatchPlanEntity planEntity = planRepository.saveAndFlush(
                MatchPlanEntity.from(queued)
        );
        planParticipantRepository.saveAndFlush(
                MatchPlanParticipantEntity.from(MatchPlanParticipant.assign(
                        queued.id(),
                        fixture.sessionId(),
                        participantId,
                        TeamSide.A,
                        1
                ))
        );

        assertThat(readOne(fixture, participantId)).isZero();

        Match playing = saveMatch(fixture, participantId, MatchStatus.PLAYING);
        MatchPlan started = queued.start(playing.id(), NOW.plusSeconds(3));
        planEntity.apply(started);
        planRepository.saveAndFlush(planEntity);

        assertThat(readOne(fixture, participantId)).isZero();

        Match completed = playing.complete(
                new MatchResult(TeamSide.A, 21, 17),
                NOW.plusSeconds(4)
        );
        MatchEntity matchEntity = matchRepository.findById(playing.id())
                .orElseThrow();
        matchEntity.applyRuntimeState(completed);
        matchRepository.saveAndFlush(matchEntity);

        assertThat(readOne(fixture, participantId)).isEqualTo(1);
    }

    private int readOne(Fixture fixture, UUID participantId) {
        return reader.readCompletedMatchCounts(
                fixture.sessionId(),
                List.of(participantId)
        ).get(participantId);
    }

    private Match saveMatch(
            Fixture fixture,
            UUID participantId,
            MatchStatus status
    ) {
        Match match = Match.create(
                fixture.sessionId(),
                fixture.sessionCourtId(),
                MatchSource.RECOMMENDATION,
                NOW
        );
        match = switch (status) {
            case CREATED -> match;
            case PLAYING -> match.start(NOW.plusSeconds(1));
            case COMPLETED -> match.start(NOW.plusSeconds(1)).complete(
                    new MatchResult(TeamSide.A, 21, 15),
                    NOW.plusSeconds(2)
            );
            case CANCELLED -> match.start(NOW.plusSeconds(1))
                    .cancel(NOW.plusSeconds(2));
        };
        matchRepository.saveAndFlush(MatchEntity.from(match));
        matchParticipantRepository.saveAndFlush(
                MatchParticipantEntity.from(MatchParticipant.assign(
                        match.id(),
                        participantId,
                        TeamSide.A,
                        1
                ))
        );
        return match;
    }

    private Fixture createFixture(int number) {
        Venue venue = Venue.create(
                "Count Venue " + number,
                null,
                true,
                NOW
        );
        UUID venueId = venueRepository.saveAndFlush(
                VenueEntity.from(venue)
        ).getId();
        Court court = Court.create(
                venueId,
                "Count Court " + number,
                SportCode.BADMINTON,
                true,
                NOW
        );
        UUID courtId = courtRepository.saveAndFlush(
                CourtEntity.from(court)
        ).getId();
        Session session = Session.create(
                venueId,
                "Count Session " + number,
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

        UUID first = createParticipant(sessionId, number * 100 + 1);
        UUID second = createParticipant(sessionId, number * 100 + 2);
        return new Fixture(sessionId, sessionCourtId, List.of(first, second));
    }

    private UUID createParticipant(UUID sessionId, int number) {
        UUID playerId = UUID.nameUUIDFromBytes(("count-player-" + number)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Player player = new Player(
                playerId,
                "Count Player " + number,
                NOW,
                NOW
        );
        playerRepository.saveAndFlush(PlayerEntity.from(player));
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                NOW.plusSeconds(1)
        ).checkIn(NOW.plusSeconds(2));
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private record Fixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> participantIds
    ) {
    }
}

package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
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
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.convention.TestBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingRecommendationServiceIntegrationTest
        extends PostgreSqlIntegrationTest {

    private static final Instant BASE_TIME =
            Instant.parse("2026-08-28T09:00:00Z");
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T10:00:00Z");

    @TestBean(name = "clock", enforceOverride = true)
    private Clock clock;

    @Autowired
    private MatchmakingRecommendationService recommendationService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MatchParticipantRepository matchParticipantRepository;

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

    @Autowired
    private PlayerRatingRepository playerRatingRepository;

    @Autowired
    private RatingEventRepository ratingEventRepository;

    private static Clock clock() {
        return Clock.fixed(EVALUATION_TIME, ZoneOffset.UTC);
    }

    @BeforeEach
    void cleanDatabase() {
        ratingEventRepository.deleteAll();
        playerRatingRepository.deleteAll();
        matchParticipantRepository.deleteAll();
        matchRepository.deleteAll();
        sessionCourtRepository.deleteAll();
        participantRepository.deleteAll();
        sessionRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void realPipelineReturnsMixedBasisRecommendationWithoutWrites() {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.IN_PROGRESS,
                SessionCourtStatus.AVAILABLE,
                true
        );
        UUID persistedPlayerId = fixture.waitingPlayerIds().getFirst();
        createPersistedRating(persistedPlayerId);
        PersistedState before = persistedState(fixture);

        MatchmakingResult result = recommendationService.recommend(
                fixture.sessionId(),
                fixture.sessionCourtId()
        );

        assertThat(result).isInstanceOf(MatchRecommendation.class);
        MatchRecommendation recommendation = (MatchRecommendation) result;
        assertThat(recommendation.sessionId()).isEqualTo(fixture.sessionId());
        assertThat(recommendation.sessionCourtId())
                .isEqualTo(fixture.sessionCourtId());
        assertThat(recommendation.algorithmVersion()).isEqualTo(
                MatchmakingEngine.ALGORITHM_VERSION
        );
        assertThat(recommendation.evaluationTime()).isEqualTo(EVALUATION_TIME);
        assertThat(recommendation.eligiblePlayerCount()).isEqualTo(4);
        assertThat(recommendedParticipantIds(recommendation))
                .containsExactlyInAnyOrderElementsOf(fixture.waitingParticipantIds())
                .doesNotHaveDuplicates()
                .doesNotContain(fixture.nonWaitingParticipantId());
        assertThat(recommendedRatingBases(recommendation))
                .contains(RatingBasis.PERSISTED, RatingBasis.INITIAL_PRIOR);
        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(playerRatingRepository.findAll())
                .singleElement()
                .satisfies(rating -> assertThat(rating.getPlayerId())
                        .isEqualTo(persistedPlayerId));
        assertThat(ratingEventRepository.count()).isZero();
        assertThat(matchRepository.count()).isZero();
        assertThat(matchParticipantRepository.count()).isZero();
    }

    @Test
    void plannedSessionFailsWithoutAnyRuntimeOrRatingWrites() {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.PLANNED,
                SessionCourtStatus.AVAILABLE,
                false
        );
        PersistedState before = persistedState(fixture);

        assertThatThrownBy(() -> recommendationService.recommend(
                fixture.sessionId(),
                fixture.sessionCourtId()
        )).isInstanceOfSatisfying(
                MatchmakingRecommendationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(
                        MatchmakingRecommendationFailureReason
                                .SESSION_NOT_IN_PROGRESS
                )
        );

        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(matchRepository.count()).isZero();
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void unavailableCourtFailsWithoutAnyRuntimeOrRatingWrites() {
        RuntimeFixture fixture = createRuntimeFixture(
                SessionStatus.IN_PROGRESS,
                SessionCourtStatus.UNAVAILABLE,
                false
        );
        PersistedState before = persistedState(fixture);

        assertThatThrownBy(() -> recommendationService.recommend(
                fixture.sessionId(),
                fixture.sessionCourtId()
        )).isInstanceOfSatisfying(
                MatchmakingRecommendationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(
                        MatchmakingRecommendationFailureReason
                                .SESSION_COURT_NOT_AVAILABLE
                )
        );

        assertThat(persistedState(fixture)).isEqualTo(before);
        assertThat(matchRepository.count()).isZero();
        assertThat(playerRatingRepository.count()).isZero();
        assertThat(ratingEventRepository.count()).isZero();
    }

    @Test
    void springContextProvidesOneEngineAndOneClockBean() {
        assertThat(applicationContext.getBeansOfType(MatchmakingEngine.class))
                .hasSize(1);
        assertThat(applicationContext.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(recommendationService).isNotNull();
        assertThat(clock.instant()).isEqualTo(EVALUATION_TIME);
    }

    private RuntimeFixture createRuntimeFixture(
            SessionStatus sessionStatus,
            SessionCourtStatus courtStatus,
            boolean includeNonWaitingParticipant
    ) {
        UUID venueId = createVenue();
        UUID courtId = createCourt(venueId);
        UUID sessionId = createSession(venueId, sessionStatus);
        UUID sessionCourtId = createSessionCourt(
                sessionId,
                courtId,
                courtStatus
        );
        List<UUID> waitingPlayerIds = new ArrayList<>();
        List<UUID> waitingParticipantIds = new ArrayList<>();
        for (int index = 1; index <= 4; index++) {
            UUID playerId = createPlayerWithProfile(index, SkillLevel.GOOD);
            waitingPlayerIds.add(playerId);
            waitingParticipantIds.add(createParticipant(
                    sessionId,
                    playerId,
                    ParticipantStatus.WAITING,
                    index
            ));
        }
        UUID nonWaitingParticipantId = null;
        if (includeNonWaitingParticipant) {
            UUID nonWaitingPlayerId = createPlayerWithProfile(
                    5,
                    SkillLevel.WEAK
            );
            nonWaitingParticipantId = createParticipant(
                    sessionId,
                    nonWaitingPlayerId,
                    ParticipantStatus.REGISTERED,
                    5
            );
        }
        return new RuntimeFixture(
                sessionId,
                sessionCourtId,
                List.copyOf(waitingPlayerIds),
                List.copyOf(waitingParticipantIds),
                nonWaitingParticipantId
        );
    }

    private UUID createVenue() {
        Venue venue = Venue.create(
                "Step 3 Venue " + UUID.randomUUID(),
                null,
                true,
                BASE_TIME
        );
        return venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
    }

    private UUID createCourt(UUID venueId) {
        Court court = Court.create(
                venueId,
                "Step 3 Court " + UUID.randomUUID(),
                SportCode.BADMINTON,
                true,
                BASE_TIME
        );
        return courtRepository.saveAndFlush(CourtEntity.from(court)).getId();
    }

    private UUID createSession(UUID venueId, SessionStatus status) {
        Session session = Session.create(
                venueId,
                "Step 3 Session " + UUID.randomUUID(),
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
                BASE_TIME.plusSeconds(3)
        );
        sessionCourt = switch (status) {
            case AVAILABLE -> sessionCourt;
            case PLAYING -> sessionCourt.startMatch(BASE_TIME.plusSeconds(4));
            case UNAVAILABLE -> sessionCourt.disable(BASE_TIME.plusSeconds(4));
        };
        return sessionCourtRepository.saveAndFlush(
                SessionCourtEntity.from(sessionCourt)
        ).getId();
    }

    private UUID createPlayerWithProfile(int number, SkillLevel skillLevel) {
        UUID playerId = uuid(number);
        Player player = new Player(
                playerId,
                "Step 3 Player " + number,
                BASE_TIME,
                BASE_TIME
        );
        playerRepository.saveAndFlush(PlayerEntity.from(player));
        PlayerSportProfile profile = PlayerSportProfile.create(
                playerId,
                SportCode.BADMINTON,
                skillLevel,
                BASE_TIME
        );
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(profile));
        return playerId;
    }

    private UUID createParticipant(
            UUID sessionId,
            UUID playerId,
            ParticipantStatus status,
            int order
    ) {
        SessionParticipant participant = SessionParticipant.register(
                sessionId,
                playerId,
                BASE_TIME.plusSeconds(10L + order)
        );
        participant = switch (status) {
            case REGISTERED -> participant;
            case WAITING -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            );
            case PLAYING -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            ).startMatch(BASE_TIME.plusSeconds(30L + order));
            case PAUSED -> participant.checkIn(
                    BASE_TIME.plusSeconds(20L + order)
            ).pause(BASE_TIME.plusSeconds(30L + order));
            case LEFT -> participant.leave(BASE_TIME.plusSeconds(20L + order));
        };
        return participantRepository.saveAndFlush(
                SessionParticipantEntity.from(participant)
        ).getId();
    }

    private void createPersistedRating(UUID playerId) {
        playerRatingRepository.saveAndFlush(PlayerRatingEntity.initialize(
                playerId,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                SkillLevel.INTERMEDIATE_PLUS,
                new RatingState(31.123456789, 6.987654321),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                BASE_TIME
        ));
    }

    private PersistedState persistedState(RuntimeFixture fixture) {
        SessionEntity session = sessionRepository.findById(fixture.sessionId())
                .orElseThrow();
        SessionCourtEntity court = sessionCourtRepository.findById(
                fixture.sessionCourtId()
        ).orElseThrow();
        List<ParticipantState> participants = participantRepository
                .findAllBySessionIdOrderByPlayerIdAscIdAsc(fixture.sessionId())
                .stream()
                .map(participant -> new ParticipantState(
                        participant.getId(),
                        participant.getStatus(),
                        participant.getWaitingSince(),
                        participant.toDomain().updatedAt(),
                        participant.getVersion()
                ))
                .toList();
        List<PersistedRatingState> ratings = playerRatingRepository.findAll()
                .stream()
                .map(rating -> new PersistedRatingState(
                        rating.getId(),
                        rating.getPlayerId(),
                        rating.getRatingValue(),
                        rating.getUncertainty(),
                        rating.getRatedMatches(),
                        rating.getUpdatedAt(),
                        rating.getVersion()
                ))
                .toList();
        return new PersistedState(
                session.getStatus(),
                session.toDomain().updatedAt(),
                session.getVersion(),
                court.getStatus(),
                court.toDomain().updatedAt(),
                court.getVersion(),
                participants,
                ratings,
                matchRepository.count(),
                ratingEventRepository.count()
        );
    }

    private List<UUID> recommendedParticipantIds(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1().sessionParticipantId(),
                recommendation.teamA().slot2().sessionParticipantId(),
                recommendation.teamB().slot1().sessionParticipantId(),
                recommendation.teamB().slot2().sessionParticipantId()
        );
    }

    private List<RatingBasis> recommendedRatingBases(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1().ratingBasis(),
                recommendation.teamA().slot2().ratingBasis(),
                recommendation.teamB().slot1().ratingBasis(),
                recommendation.teamB().slot2().ratingBasis()
        );
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record RuntimeFixture(
            UUID sessionId,
            UUID sessionCourtId,
            List<UUID> waitingPlayerIds,
            List<UUID> waitingParticipantIds,
            UUID nonWaitingParticipantId
    ) {
    }

    private record ParticipantState(
            UUID id,
            ParticipantStatus status,
            Instant waitingSince,
            Instant updatedAt,
            long version
    ) {
    }

    private record PersistedRatingState(
            UUID id,
            UUID playerId,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            Instant updatedAt,
            long version
    ) {
    }

    private record PersistedState(
            SessionStatus sessionStatus,
            Instant sessionUpdatedAt,
            long sessionVersion,
            SessionCourtStatus courtStatus,
            Instant courtUpdatedAt,
            long courtVersion,
            List<ParticipantState> participants,
            List<PersistedRatingState> ratings,
            long matches,
            long ratingEvents
    ) {
    }
}

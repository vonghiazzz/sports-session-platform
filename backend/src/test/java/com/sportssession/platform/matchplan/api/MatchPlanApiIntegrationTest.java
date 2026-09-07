package com.sportssession.platform.matchplan.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.matchplan.application.CreateMatchPlanCommand;
import com.sportssession.platform.matchplan.application.MatchPlanAssignment;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import com.sportssession.platform.matchplan.application.StartedMatchPlan;
import com.sportssession.platform.matchplan.application.UpdateMatchPlanCommand;
import com.sportssession.platform.matchplan.domain.InvalidMatchPlanRequestException;
import com.sportssession.platform.matchplan.domain.MatchPlanConflictException;
import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantRepository;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.session.application.SessionService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MatchPlanApiIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired private MatchPlanService service;
    @Autowired private SessionService sessionService;
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MatchPlanParticipantRepository planParticipantRepository;
    @Autowired private MatchPlanRepository planRepository;
    @Autowired private MatchParticipantRepository matchParticipantRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private SessionCourtRepository sessionCourtRepository;
    @Autowired private SessionParticipantRepository sessionParticipantRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private PlayerSportProfileRepository profileRepository;
    @Autowired private PlayerRepository playerRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        planParticipantRepository.deleteAll();
        planRepository.deleteAll();
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
    void createAllowsAllPlanningStatusesAndDoesNotReserveResources() {
        Fixture fixture = fixture(
                List.of(
                        SessionCourtStatus.AVAILABLE,
                        SessionCourtStatus.PLAYING,
                        SessionCourtStatus.UNAVAILABLE
                ),
                List.of(
                        ParticipantStatus.REGISTERED,
                        ParticipantStatus.WAITING,
                        ParticipantStatus.PLAYING,
                        ParticipantStatus.PAUSED,

                        ParticipantStatus.REGISTERED,
                        ParticipantStatus.WAITING,
                        ParticipantStatus.PLAYING,
                        ParticipantStatus.PAUSED,

                        ParticipantStatus.REGISTERED,
                        ParticipantStatus.WAITING,
                        ParticipantStatus.PLAYING,
                        ParticipantStatus.PAUSED
                )
        );

        List<ParticipantStatus> beforeParticipants =
                participantStatuses(fixture.participantIds());

        List<SessionCourtStatus> beforeCourts =
                courtStatuses(fixture.courtIds());

        for (int courtIndex = 0; courtIndex < fixture.courtIds().size(); courtIndex++) {
            int participantStart = courtIndex * 4;

            MatchPlanDetails plan = create(
                    fixture.sessionId(),
                    fixture.courtIds().get(courtIndex),
                    fixture.participantIds().subList(
                            participantStart,
                            participantStart + 4
                    )
            );

            assertThat(plan.plan().status())
                    .isEqualTo(MatchPlanStatus.QUEUED);

            assertThat(plan.plan().source())
                    .isEqualTo(MatchSource.MANUAL);

            assertThat(plan.plan().queuePosition())
                    .isEqualTo(1);
        }

        assertThat(participantStatuses(fixture.participantIds()))
                .isEqualTo(beforeParticipants);

        assertThat(courtStatuses(fixture.courtIds()))
                .isEqualTo(beforeCourts);

        assertThat(matchRepository.count()).isZero();
    }

    @Test
    void leftParticipantAndDuplicateIdentityAreRejected() {
        Fixture fixture = fixture(
                List.of(SessionCourtStatus.AVAILABLE),
                List.of(ParticipantStatus.LEFT, ParticipantStatus.WAITING,
                        ParticipantStatus.WAITING, ParticipantStatus.WAITING)
        );
        assertThatThrownBy(() -> create(
                fixture.sessionId(), fixture.courtIds().getFirst(),
                fixture.participantIds()
        )).isInstanceOf(MatchPlanConflictException.class);

        List<UUID> duplicate = List.of(
                fixture.participantIds().get(1), fixture.participantIds().get(1),
                fixture.participantIds().get(2), fixture.participantIds().get(3)
        );
        assertThatThrownBy(() -> create(
                fixture.sessionId(), fixture.courtIds().getFirst(), duplicate
        )).isInstanceOf(InvalidMatchPlanRequestException.class);
        assertThat(planRepository.count()).isZero();
    }

    @Test
    void queuesAppendPerCourtAndQueuedParticipantCannotAppearInAnotherPlan() {
        Fixture fixture = waitingFixture(2, 12);

        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );
        MatchPlanDetails second = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );
        MatchPlanDetails otherCourt = create(
                fixture, 1, fixture.participantIds().subList(8, 12)
        );

        assertThat(first.plan().queuePosition()).isEqualTo(1);
        assertThat(second.plan().queuePosition()).isEqualTo(2);
        assertThat(otherCourt.plan().queuePosition()).isEqualTo(1);

        assertThatThrownBy(() -> create(
                fixture,
                0,
                fixture.participantIds().subList(0, 4)
        )).isInstanceOf(MatchPlanConflictException.class);

        assertThatThrownBy(() -> create(
                fixture,
                1,
                fixture.participantIds().subList(0, 4)
        )).isInstanceOf(MatchPlanConflictException.class);

        assertThat(service.list(fixture.sessionId())).hasSize(3);
    }

    @Test
    void queuedPlanCanReplaceCompositionWithoutConflictingWithItself() {
        Fixture fixture = waitingFixture(1, 8);

        MatchPlanDetails plan = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );

        List<UUID> replacement = List.of(
                fixture.participantIds().get(0),
                fixture.participantIds().get(1),
                fixture.participantIds().get(4),
                fixture.participantIds().get(5)
        );

        MatchPlanDetails updated = service.update(
                new UpdateMatchPlanCommand(
                        plan.plan().id(),
                        assignments(replacement)
                )
        );

        assertThat(updated.plan().queuePosition()).isEqualTo(1);
        assertThat(updated.participants())
                .extracting(participant ->
                        participant.sessionParticipantId())
                .containsExactlyElementsOf(replacement);

        service.cancel(plan.plan().id());

        assertThatThrownBy(() -> service.update(
                new UpdateMatchPlanCommand(
                        plan.plan().id(),
                        assignments(fixture.participantIds().subList(0, 4))
                )
        )).isInstanceOf(MatchPlanConflictException.class);
    }

    @Test
    void queuedPlanEditRejectsParticipantOwnedByAnotherQueuedPlan() {
        Fixture fixture = waitingFixture(1, 12);

        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );

        MatchPlanDetails second = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );

        List<UUID> invalidReplacement = List.of(
                fixture.participantIds().get(8),
                fixture.participantIds().get(9),
                fixture.participantIds().get(10),
                fixture.participantIds().get(4)
        );

        assertThatThrownBy(() -> service.update(
                new UpdateMatchPlanCommand(
                        first.plan().id(),
                        assignments(invalidReplacement)
                )
        )).isInstanceOf(MatchPlanConflictException.class);

        MatchPlanDetails persistedFirst = service.list(fixture.sessionId())
                .stream()
                .filter(details ->
                        details.plan().id().equals(first.plan().id()))
                .findFirst()
                .orElseThrow();

        assertThat(persistedFirst.participants())
                .extracting(participant ->
                        participant.sessionParticipantId())
                .containsExactlyElementsOf(
                        fixture.participantIds().subList(0, 4)
                );

        assertThat(second.plan().queuePosition()).isEqualTo(2);
    }

    @Test
    void cancelledPlanReleasesParticipantsForFutureQueueing() {
        Fixture fixture = waitingFixture(2, 4);

        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds()
        );

        service.cancel(first.plan().id());

        MatchPlanDetails second = create(
                fixture, 1, fixture.participantIds()
        );

        assertThat(second.plan().queuePosition()).isEqualTo(1);
        assertThat(second.participants())
                .extracting(participant ->
                        participant.sessionParticipantId())
                .containsExactlyElementsOf(fixture.participantIds());
    }

    @Test
    void moveCompactsSourceAndAppendsToUnavailableDestination() {
        Fixture fixture = fixture(
                List.of(SessionCourtStatus.AVAILABLE,
                        SessionCourtStatus.UNAVAILABLE),
                waitingStatuses(12)
        );
        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );
        MatchPlanDetails moved = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );
        MatchPlanDetails destination = create(
                fixture, 1, fixture.participantIds().subList(8, 12)
        );

        MatchPlanDetails result = service.move(
                first.plan().id(), fixture.courtIds().get(1)
        );
        assertThat(result.plan().sessionCourtId())
                .isEqualTo(fixture.courtIds().get(1));
        assertThat(result.plan().queuePosition()).isEqualTo(2);
        assertThat(plan(moved).queuePosition()).isEqualTo(1);
        assertThat(plan(destination).queuePosition()).isEqualTo(1);
    }

    @Test
    void reorderMaintainsCompactDeterministicPositions() {
        Fixture fixture = waitingFixture(1, 12);

        MatchPlanDetails one = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );

        MatchPlanDetails two = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );

        MatchPlanDetails three = create(
                fixture, 0, fixture.participantIds().subList(8, 12)
        );

        service.reorder(three.plan().id(), 1);

        assertThat(plan(three).queuePosition()).isEqualTo(1);
        assertThat(plan(one).queuePosition()).isEqualTo(2);
        assertThat(plan(two).queuePosition()).isEqualTo(3);

        service.reorder(three.plan().id(), 3);

        assertThat(plan(one).queuePosition()).isEqualTo(1);
        assertThat(plan(two).queuePosition()).isEqualTo(2);
        assertThat(plan(three).queuePosition()).isEqualTo(3);
    }

    @Test
    void cancelCompactsQueueWithoutMutatingRuntimeResources() {
        Fixture fixture = waitingFixture(1, 8);

        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );

        MatchPlanDetails second = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );

        MatchPlanDetails cancelled =
                service.cancel(first.plan().id());

        assertThat(cancelled.plan().status())
                .isEqualTo(MatchPlanStatus.CANCELLED);

        assertThat(cancelled.plan().queuePosition())
                .isNull();

        assertThat(plan(second).queuePosition())
                .isEqualTo(1);

        assertResources(
                fixture,
                SessionCourtStatus.AVAILABLE,
                ParticipantStatus.WAITING
        );

        assertThat(matchRepository.count()).isZero();
    }

    @Test
    void courtDisableAndEnablePreserveQueuedPlans() {
        Fixture fixture = waitingFixture(1, 4);
        MatchPlanDetails plan = create(fixture, 0, fixture.participantIds());

        sessionService.disableCourt(
                fixture.sessionId(), fixture.courtIds().getFirst()
        );
        assertThat(plan(plan).status()).isEqualTo(MatchPlanStatus.QUEUED);
        sessionService.enableCourt(
                fixture.sessionId(), fixture.courtIds().getFirst()
        );
        assertThat(plan(plan).status()).isEqualTo(MatchPlanStatus.QUEUED);
    }

    @Test
    void onlyQueueHeadCanStart() {
        Fixture fixture = waitingFixture(1, 8);
        create(fixture, 0, fixture.participantIds().subList(0, 4));
        MatchPlanDetails second = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );

        assertThatThrownBy(() -> service.start(second.plan().id()))
                .isInstanceOf(MatchPlanConflictException.class);
        assertThat(plan(second).status()).isEqualTo(MatchPlanStatus.QUEUED);
        assertResources(fixture, SessionCourtStatus.AVAILABLE,
                ParticipantStatus.WAITING);
    }

    @Test
    void startRejectsUnavailableOrPlayingCourtAndNonWaitingParticipant() {
        Fixture unavailable = fixture(
                List.of(SessionCourtStatus.UNAVAILABLE), waitingStatuses(4)
        );
        MatchPlanDetails unavailablePlan = create(
                unavailable, 0, unavailable.participantIds()
        );
        assertThatThrownBy(() -> service.start(unavailablePlan.plan().id()))
                .isInstanceOf(MatchPlanConflictException.class);

        cleanDatabase();
        Fixture playing = fixture(
                List.of(SessionCourtStatus.PLAYING), waitingStatuses(4)
        );
        MatchPlanDetails playingPlan = create(
                playing, 0, playing.participantIds()
        );
        assertThatThrownBy(() -> service.start(playingPlan.plan().id()))
                .isInstanceOf(MatchPlanConflictException.class);

        cleanDatabase();
        Fixture registered = fixture(
                List.of(SessionCourtStatus.AVAILABLE),
                List.of(ParticipantStatus.REGISTERED, ParticipantStatus.WAITING,
                        ParticipantStatus.WAITING, ParticipantStatus.WAITING)
        );
        MatchPlanDetails registeredPlan = create(
                registered, 0, registered.participantIds()
        );
        assertThatThrownBy(() -> service.start(registeredPlan.plan().id()))
                .isInstanceOf(com.sportssession.platform.match.domain
                        .MatchResourceConflictException.class);
        assertThat(plan(registeredPlan).status()).isEqualTo(MatchPlanStatus.QUEUED);
        assertThat(matchRepository.count()).isZero();
    }

    @Test
    void successfulStartCreatesPlayingMatchAndCompactsQueueAtomically() {
        Fixture fixture = waitingFixture(1, 8);
        MatchPlanDetails first = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );
        MatchPlanDetails second = create(
                fixture, 0, fixture.participantIds().subList(4, 8)
        );

        StartedMatchPlan result = service.start(first.plan().id());
        Match actualMatch = result.match().match();

        assertThat(result.matchPlan().plan().status())
                .isEqualTo(MatchPlanStatus.STARTED);
        assertThat(result.matchPlan().plan().startedMatchId())
                .isEqualTo(actualMatch.id());
        assertThat(result.matchPlan().plan().startedAt())
                .isEqualTo(actualMatch.startedAt());
        assertThat(actualMatch.status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(actualMatch.source()).isEqualTo(MatchSource.MANUAL);
        assertThat(result.match().participants()).hasSize(4);
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(SessionCourtStatus.PLAYING);
        assertThat(participantStatuses(
                fixture.participantIds().subList(0, 4)
        )).containsOnly(ParticipantStatus.PLAYING);
        assertThat(participantStatuses(
                fixture.participantIds().subList(4, 8)
        )).containsOnly(ParticipantStatus.WAITING);
        assertThat(plan(second).queuePosition()).isEqualTo(1);
        assertThatThrownBy(() -> service.cancel(first.plan().id()))
                .isInstanceOf(MatchPlanConflictException.class);
    }

    @Test
    void queuedPlansDoNotBlockSessionCompletionAndCanBeCancelledAfterward() {
        Fixture fixture = waitingFixture(1, 4);
        MatchPlanDetails plan = create(fixture, 0, fixture.participantIds());

        assertThat(sessionService.completeSession(fixture.sessionId()).status())
                .isEqualTo(com.sportssession.platform.session.domain.SessionStatus.COMPLETED);
        assertThatThrownBy(() -> service.start(plan.plan().id()))
                .isInstanceOf(MatchPlanConflictException.class);
        assertThat(service.cancel(plan.plan().id()).plan().status())
                .isEqualTo(MatchPlanStatus.CANCELLED);
    }

    @Test
    void publicApiCreatesListsEditsMovesReordersCancelsAndStarts() throws Exception {
        Fixture fixture = waitingFixture(2, 8);
        String request = objectMapper.writeValueAsString(
                new CreateMatchPlanRequest(requestAssignments(
                        fixture.participantIds().subList(0, 4)
                ))
        );
        String body = mockMvc.perform(post(
                        "/api/sessions/{sessionId}/courts/{courtId}/match-plans",
                        fixture.sessionId(), fixture.courtIds().getFirst()
                ).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.containsString("/api/match-plans/")))
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1))
                .andExpect(jsonPath("$.participants.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        UUID planId = UUID.fromString(objectMapper.readTree(body).get("id").asText());

        mockMvc.perform(get("/api/sessions/{sessionId}/match-plans",
                        fixture.sessionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId.toString()));

        String update = objectMapper.writeValueAsString(
                new UpdateMatchPlanRequest(requestAssignments(
                        fixture.participantIds().subList(4, 8)
                ))
        );
        mockMvc.perform(put("/api/match-plans/{planId}", planId)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].sessionParticipantId")
                        .value(fixture.participantIds().get(4).toString()));

        mockMvc.perform(post("/api/match-plans/{planId}/move", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MoveMatchPlanRequest(fixture.courtIds().get(1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCourtId")
                        .value(fixture.courtIds().get(1).toString()));
        mockMvc.perform(post("/api/match-plans/{planId}/reorder", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPosition\":1}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/match-plans/{planId}/start", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchPlan.status").value("STARTED"))
                .andExpect(jsonPath("$.match.status").value("PLAYING"));

        MatchPlanDetails cancellable = create(
                fixture, 0, fixture.participantIds().subList(0, 4)
        );
        mockMvc.perform(post("/api/match-plans/{planId}/cancel",
                        cancellable.plan().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void concurrentCreatesOnSameCourtProduceUniqueCompactPositions()
            throws Exception {
        Fixture fixture = waitingFixture(1, 8);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MatchPlanDetails> first = executor.submit(() -> createAfterGate(
                    fixture, fixture.participantIds().subList(0, 4), ready, start
            ));
            Future<MatchPlanDetails> second = executor.submit(() -> createAfterGate(
                    fixture, fixture.participantIds().subList(4, 8), ready, start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(service.list(fixture.sessionId()))
                .extracting(details -> details.plan().queuePosition())
                .containsExactly(1, 2);
    }

    private MatchPlanDetails createAfterGate(
            Fixture fixture,
            List<UUID> participantIds,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("start gate timeout");
        }
        return create(fixture, 0, participantIds);
    }

    private MatchPlanDetails create(
            Fixture fixture,
            int courtIndex,
            List<UUID> participantIds
    ) {
        return create(
                fixture.sessionId(), fixture.courtIds().get(courtIndex),
                participantIds
        );
    }

    private MatchPlanDetails create(
            UUID sessionId,
            UUID courtId,
            List<UUID> participantIds
    ) {
        return service.create(new CreateMatchPlanCommand(
                sessionId, courtId, assignments(participantIds)
        ));
    }

    private List<MatchPlanAssignment> assignments(List<UUID> ids) {
        return List.of(
                new MatchPlanAssignment(ids.get(0), TeamSide.A, 1),
                new MatchPlanAssignment(ids.get(1), TeamSide.A, 2),
                new MatchPlanAssignment(ids.get(2), TeamSide.B, 1),
                new MatchPlanAssignment(ids.get(3), TeamSide.B, 2)
        );
    }

    private List<MatchPlanParticipantRequest> requestAssignments(List<UUID> ids) {
        return assignments(ids).stream()
                .map(assignment -> new MatchPlanParticipantRequest(
                        assignment.sessionParticipantId(),
                        assignment.teamSide(), assignment.teamSlot()
                ))
                .toList();
    }

    private Fixture waitingFixture(int courts, int participants) {
        return fixture(
                java.util.Collections.nCopies(
                        courts, SessionCourtStatus.AVAILABLE
                ),
                waitingStatuses(participants)
        );
    }

    private List<ParticipantStatus> waitingStatuses(int count) {
        return java.util.Collections.nCopies(count, ParticipantStatus.WAITING);
    }

    private Fixture fixture(
            List<SessionCourtStatus> courtStatuses,
            List<ParticipantStatus> participantStatuses
    ) {
        Instant now = Instant.now();
        Venue venue = Venue.create("Plan Venue " + UUID.randomUUID(), null, true, now);
        UUID venueId = venueRepository.saveAndFlush(VenueEntity.from(venue)).getId();
        Session session = Session.create(
                venueId, "Match Plan Session", SportCode.BADMINTON,
                MatchFormat.DOUBLES, now.plus(1, ChronoUnit.HOURS),
                now.plus(3, ChronoUnit.HOURS), now
        ).start(now);
        UUID sessionId = sessionRepository.saveAndFlush(
                SessionEntity.from(session)
        ).getId();

        List<UUID> courtIds = new ArrayList<>();
        for (SessionCourtStatus status : courtStatuses) {
            Court court = Court.create(
                    venueId, "Court " + UUID.randomUUID(),
                    SportCode.BADMINTON, true, now
            );
            UUID courtId = courtRepository.saveAndFlush(
                    CourtEntity.from(court)
            ).getId();
            SessionCourt sessionCourt = SessionCourt.allocate(
                    sessionId, courtId, now.plusSeconds(2)
            );
            sessionCourt = switch (status) {
                case AVAILABLE -> sessionCourt;
                case PLAYING -> sessionCourt.startMatch(now.plusSeconds(3));
                case UNAVAILABLE -> sessionCourt.disable(now.plusSeconds(3));
            };
            courtIds.add(sessionCourtRepository.saveAndFlush(
                    SessionCourtEntity.from(sessionCourt)
            ).getId());
        }

        List<UUID> participantIds = new ArrayList<>();
        for (ParticipantStatus status : participantStatuses) {
            Player player = Player.create("Player " + UUID.randomUUID(), now);
            UUID playerId = playerRepository.saveAndFlush(
                    PlayerEntity.from(player)
            ).getId();
            SessionParticipant participant = SessionParticipant.register(
                    sessionId, playerId, now
            );
            participant = switch (status) {
                case REGISTERED -> participant;
                case WAITING -> participant.checkIn(now.plusSeconds(2));
                case PLAYING -> participant.checkIn(now.plusSeconds(2))
                        .startMatch(now.plusSeconds(3));
                case PAUSED -> participant.checkIn(now.plusSeconds(2))
                        .pause(now.plusSeconds(3));
                case LEFT -> participant.leave(now.plusSeconds(2));
            };
            participantIds.add(sessionParticipantRepository.saveAndFlush(
                    SessionParticipantEntity.from(participant)
            ).getId());
        }
        return new Fixture(sessionId, courtIds, participantIds);
    }

    private com.sportssession.platform.matchplan.domain.MatchPlan plan(
            MatchPlanDetails details
    ) {
        return planRepository.findById(details.plan().id())
                .orElseThrow().toDomain();
    }

    private SessionCourt court(UUID id) {
        return sessionCourtRepository.findById(id).orElseThrow().toDomain();
    }

    private List<ParticipantStatus> participantStatuses(List<UUID> ids) {
        return ids.stream()
                .map(id -> sessionParticipantRepository.findById(id)
                        .orElseThrow().getStatus())
                .toList();
    }

    private List<SessionCourtStatus> courtStatuses(List<UUID> ids) {
        return ids.stream().map(id -> court(id).status()).toList();
    }

    private void assertResources(
            Fixture fixture,
            SessionCourtStatus courtStatus,
            ParticipantStatus participantStatus
    ) {
        assertThat(court(fixture.courtIds().getFirst()).status())
                .isEqualTo(courtStatus);
        assertThat(participantStatuses(fixture.participantIds()))
                .containsOnly(participantStatus);
    }

    private record Fixture(
            UUID sessionId,
            List<UUID> courtIds,
            List<UUID> participantIds
    ) {
        private Fixture {
            courtIds = List.copyOf(courtIds);
            participantIds = List.copyOf(participantIds);
        }
    }
}

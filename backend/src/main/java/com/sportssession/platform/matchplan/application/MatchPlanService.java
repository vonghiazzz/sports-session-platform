package com.sportssession.platform.matchplan.application;

import com.sportssession.platform.match.application.CreateAndStartPlannedMatchCommand;
import com.sportssession.platform.match.application.ManualMatchParticipantAssignment;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.StartedMatch;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.matchplan.domain.InvalidMatchPlanRequestException;
import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanConflictException;
import com.sportssession.platform.matchplan.domain.MatchPlanNotFoundException;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;
import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantEntity;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanParticipantRepository;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
import com.sportssession.platform.session.application.SessionRuntimeLookup;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchPlanService {

    private static final Set<TeamSlot> REQUIRED_TEAM_SLOTS = Set.of(
            new TeamSlot(TeamSide.A, 1),
            new TeamSlot(TeamSide.A, 2),
            new TeamSlot(TeamSide.B, 1),
            new TeamSlot(TeamSide.B, 2)
    );

    private final MatchPlanRepository planRepository;
    private final MatchPlanParticipantRepository participantRepository;
    private final SessionRuntimeLookup sessionRuntimeLookup;
    private final MatchService matchService;
    private final Clock clock;

    public MatchPlanService(
            MatchPlanRepository planRepository,
            MatchPlanParticipantRepository participantRepository,
            SessionRuntimeLookup sessionRuntimeLookup,
            MatchService matchService,
            Clock clock
    ) {
        this.planRepository = planRepository;
        this.participantRepository = participantRepository;
        this.sessionRuntimeLookup = sessionRuntimeLookup;
        this.matchService = matchService;
        this.clock = clock;
    }

    @Transactional
    public MatchPlanDetails create(CreateMatchPlanCommand command) {
        return create(
                command.sessionId(), command.sessionCourtId(),
                command.participants(), MatchSource.MANUAL
        );
    }

    @Transactional
    public MatchPlanDetails createRecommended(
            CreateRecommendedMatchPlanCommand command
    ) {
        return create(
                command.sessionId(), command.sessionCourtId(),
                command.participants(), MatchSource.RECOMMENDATION
        );
    }

    private MatchPlanDetails create(
            UUID sessionId,
            UUID sessionCourtId,
            List<MatchPlanAssignment> requestedAssignments,
            MatchSource source
    ) {
        List<MatchPlanAssignment> assignments = validateStructure(
                requestedAssignments
        );
        requireInProgress(sessionRuntimeLookup.requireSessionForUpdate(
                sessionId
        ));
        sessionRuntimeLookup.requireScopedSessionCourtForUpdate(
                sessionId, sessionCourtId
        );
        validatePlanningParticipants(
                sessionId, assignments,
                source == MatchSource.RECOMMENDATION
        );

        requireNotQueuedElsewhere(
                sessionId,
                assignments,
                null
        );

        List<MatchPlanEntity> queue = queueForUpdate(sessionCourtId);

        Instant now = clock.instant();
        MatchPlan plan = source == MatchSource.RECOMMENDATION
                ? MatchPlan.queueRecommendation(
                        sessionId, sessionCourtId, queue.size() + 1, now
                )
                : MatchPlan.queueManual(
                        sessionId, sessionCourtId, queue.size() + 1, now
                );
        MatchPlanEntity entity = planRepository.saveAndFlush(
                MatchPlanEntity.from(plan)
        );
        List<MatchPlanParticipant> participants = saveAssignments(
                plan, assignments
        );
        return new MatchPlanDetails(entity.toDomain(), participants);
    }

    @Transactional(readOnly = true)
    public List<MatchPlanDetails> list(UUID sessionId) {
        sessionRuntimeLookup.requireSession(sessionId);
        return planRepository
                .findAllBySessionIdOrderBySessionCourtIdAscQueuePositionAscCreatedAtAscIdAsc(
                        sessionId
                )
                .stream()
                .map(this::details)
                .toList();
    }

    @Transactional
    public MatchPlanDetails update(UpdateMatchPlanCommand command) {
        List<MatchPlanAssignment> assignments = validateStructure(
                command.participants()
        );
        MatchPlanEntity entity = requirePlanForUpdate(command.matchPlanId());
        MatchPlan plan = requireQueued(entity.toDomain(), "edit");
        requireInProgress(sessionRuntimeLookup.requireSessionForUpdate(
                plan.sessionId()
        ));
        validatePlanningParticipants(plan.sessionId(), assignments, false);

        requireNotQueuedElsewhere(
                plan.sessionId(),
                assignments,
                plan.id()
        );

        participantRepository.deleteAllByMatchPlanId(plan.id());
        participantRepository.flush();
        entity.apply(plan.edit(clock.instant()));
        planRepository.flush();
        List<MatchPlanParticipant> participants = saveAssignments(
                plan, assignments
        );
        return new MatchPlanDetails(entity.toDomain(), participants);
    }

    @Transactional
    public MatchPlanDetails move(UUID planId, UUID targetSessionCourtId) {
        if (targetSessionCourtId == null) {
            throw new InvalidMatchPlanRequestException(
                    "targetSessionCourtId is required"
            );
        }
        MatchPlanEntity entity = requirePlanForUpdate(planId);
        MatchPlan plan = requireQueued(entity.toDomain(), "move");
        requireInProgress(sessionRuntimeLookup.requireSessionForUpdate(
                plan.sessionId()
        ));
        lockCourtsInOrder(
                plan.sessionId(), plan.sessionCourtId(), targetSessionCourtId
        );
        if (plan.sessionCourtId().equals(targetSessionCourtId)) {
            return details(entity);
        }

        List<MatchPlanEntity> sourceQueue = queueForUpdate(
                plan.sessionCourtId()
        );
        List<MatchPlanEntity> targetQueue = queueForUpdate(
                targetSessionCourtId
        );
        Instant now = clock.instant();
        entity.apply(plan.move(
                targetSessionCourtId, targetQueue.size() + 1, now
        ));
        planRepository.flush();
        compactQueue(sourceQueue, plan.id(), now);
        return details(entity);
    }

    @Transactional
    public MatchPlanDetails reorder(UUID planId, int targetPosition) {
        MatchPlanEntity entity = requirePlanForUpdate(planId);
        MatchPlan plan = requireQueued(entity.toDomain(), "reorder");
        requireInProgress(sessionRuntimeLookup.requireSessionForUpdate(
                plan.sessionId()
        ));
        sessionRuntimeLookup.requireScopedSessionCourtForUpdate(
                plan.sessionId(), plan.sessionCourtId()
        );
        List<MatchPlanEntity> queue = queueForUpdate(plan.sessionCourtId());
        if (targetPosition < 1 || targetPosition > queue.size()) {
            throw new InvalidMatchPlanRequestException(
                    "targetPosition must be within the active Court queue"
            );
        }
        int currentPosition = plan.queuePosition();
        if (currentPosition == targetPosition) {
            return details(entity);
        }

        Instant now = clock.instant();
        entity.apply(plan.reorder(queue.size() + 1, now));
        planRepository.flush();

        List<MatchPlanEntity> affected = new ArrayList<>(queue.stream()
                .filter(candidate -> !candidate.getId().equals(plan.id()))
                .filter(candidate -> targetPosition < currentPosition
                        ? candidate.getQueuePosition() >= targetPosition
                        && candidate.getQueuePosition() < currentPosition
                        : candidate.getQueuePosition() > currentPosition
                        && candidate.getQueuePosition() <= targetPosition)
                .toList());
        if (targetPosition < currentPosition) {
            affected.sort(Comparator.comparingInt(
                    MatchPlanEntity::getQueuePosition
            ).reversed());
        } else {
            affected.sort(Comparator.comparingInt(
                    MatchPlanEntity::getQueuePosition
            ));
        }
        for (MatchPlanEntity candidate : affected) {
            int shiftedPosition = candidate.getQueuePosition()
                    + (targetPosition < currentPosition ? 1 : -1);
            candidate.apply(candidate.toDomain().reorder(
                    shiftedPosition, now
            ));
            planRepository.flush();
        }
        entity.apply(entity.toDomain().reorder(targetPosition, now));
        planRepository.flush();
        return details(entity);
    }

    @Transactional
    public MatchPlanDetails cancel(UUID planId) {
        MatchPlanEntity entity = requirePlanForUpdate(planId);
        MatchPlan plan = requireQueued(entity.toDomain(), "cancel");
        sessionRuntimeLookup.requireScopedSessionCourtForUpdate(
                plan.sessionId(), plan.sessionCourtId()
        );
        List<MatchPlanEntity> queue = queueForUpdate(plan.sessionCourtId());
        Instant now = clock.instant();
        entity.apply(plan.cancel(now));
        planRepository.flush();
        compactQueue(queue, plan.id(), now);
        return details(entity);
    }

    @Transactional
    public StartedMatchPlan start(UUID planId) {
        MatchPlanEntity entity = requirePlanForUpdate(planId);
        MatchPlan plan = requireQueued(entity.toDomain(), "start");
        requireInProgress(sessionRuntimeLookup.requireSessionForUpdate(
                plan.sessionId()
        ));
        var court = sessionRuntimeLookup.requireScopedSessionCourtForUpdate(
                plan.sessionId(), plan.sessionCourtId()
        );
        if (court.status()
                != com.sportssession.platform.session.domain.SessionCourtStatus.AVAILABLE) {
            throw new MatchPlanConflictException(
                    "Session Court must be AVAILABLE to Start MatchPlan"
            );
        }
        List<MatchPlanEntity> queue = queueForUpdate(plan.sessionCourtId());
        if (queue.isEmpty() || !queue.getFirst().getId().equals(plan.id())
                || plan.queuePosition() != 1) {
            throw new MatchPlanConflictException(
                    "Only the first MatchPlan in a Court queue can Start"
            );
        }

        List<MatchPlanParticipant> composition = loadComposition(plan.id());
        validatePersistedComposition(composition);
        StartedMatch startedMatch = matchService.createAndStartPlannedMatch(
                new CreateAndStartPlannedMatchCommand(
                        plan.sessionId(), plan.sessionCourtId(), plan.source(),
                        composition.stream()
                                .map(assignment ->
                                        new ManualMatchParticipantAssignment(
                                                assignment.sessionParticipantId(),
                                                assignment.teamSide(),
                                                assignment.teamSlot()
                                        ))
                                .toList()
                )
        );

        Instant startedAt = startedMatch.match().startedAt();
        entity.apply(plan.start(startedMatch.match().id(), startedAt));
        planRepository.flush();
        compactQueue(queue, plan.id(), startedAt);
        return new StartedMatchPlan(
                new MatchPlanDetails(entity.toDomain(), composition),
                startedMatch
        );
    }

    private void validatePlanningParticipants(
            UUID sessionId,
            List<MatchPlanAssignment> assignments,
            boolean requireWaiting
    ) {
        List<UUID> ids = assignments.stream()
                .map(MatchPlanAssignment::sessionParticipantId)
                .sorted()
                .toList();
        List<SessionParticipant> participants = sessionRuntimeLookup
                .requireSessionParticipantsForUpdate(sessionId, ids);
        for (SessionParticipant participant : participants) {
            if (!participant.sessionId().equals(sessionId)) {
                throw new MatchPlanConflictException(
                        "Session Participant belongs to a different Session: "
                                + participant.id()
                );
            }
            if (participant.status() == ParticipantStatus.LEFT) {
                throw new MatchPlanConflictException(
                        "LEFT Session Participant cannot be planned: "
                                + participant.id()
                );
            }
            if (requireWaiting
                    && participant.status() != ParticipantStatus.WAITING) {
                throw new MatchPlanConflictException(
                        "Recommended MatchPlan Participant must be WAITING: "
                                + participant.id()
                );
            }
        }
    }

    private List<MatchPlanAssignment> validateStructure(
            List<MatchPlanAssignment> assignments
    ) {
        if (assignments.size() != 4) {
            throw new InvalidMatchPlanRequestException(
                    "A MatchPlan requires exactly 4 participant assignments"
            );
        }
        Set<UUID> ids = new HashSet<>();
        Set<TeamSlot> slots = new HashSet<>();
        for (MatchPlanAssignment assignment : assignments) {
            if (assignment == null || assignment.sessionParticipantId() == null
                    || assignment.teamSide() == null) {
                throw new InvalidMatchPlanRequestException(
                        "Every MatchPlan assignment must be complete"
                );
            }
            if (assignment.teamSlot() != 1 && assignment.teamSlot() != 2) {
                throw new InvalidMatchPlanRequestException(
                        "teamSlot must be 1 or 2"
                );
            }
            if (!ids.add(assignment.sessionParticipantId())) {
                throw new InvalidMatchPlanRequestException(
                        "MatchPlan assignments must use unique sessionParticipantIds"
                );
            }
            if (!slots.add(new TeamSlot(
                    assignment.teamSide(), assignment.teamSlot()
            ))) {
                throw new InvalidMatchPlanRequestException(
                        "MatchPlan assignments must not duplicate a team slot"
                );
            }
        }
        if (!slots.equals(REQUIRED_TEAM_SLOTS)) {
            throw new InvalidMatchPlanRequestException(
                    "Teams A and B must each contain exactly slots 1 and 2"
            );
        }
        return assignments.stream()
                .sorted(Comparator.comparing(MatchPlanAssignment::teamSide)
                        .thenComparingInt(MatchPlanAssignment::teamSlot))
                .toList();
    }

    private void requireNotQueuedElsewhere(
            UUID sessionId,
            List<MatchPlanAssignment> assignments,
            UUID excludedPlanId
    ) {
        Set<UUID> requestedParticipantIds = Set.copyOf(
                assignments.stream()
                        .map(MatchPlanAssignment::sessionParticipantId)
                        .toList()
        );

        List<UUID> queuedParticipantIds = excludedPlanId == null
                ? planRepository.findParticipantIdsBySessionAndStatus(
                sessionId,
                MatchPlanStatus.QUEUED
        )
                : planRepository
                .findParticipantIdsBySessionAndStatusExcludingPlan(
                        sessionId,
                        MatchPlanStatus.QUEUED,
                        excludedPlanId
                );

        queuedParticipantIds.stream()
                .filter(requestedParticipantIds::contains)
                .sorted()
                .findFirst()
                .ifPresent(participantId -> {
                    throw new MatchPlanConflictException(
                            "Session Participant is already assigned to "
                                    + "another QUEUED MatchPlan: "
                                    + participantId
                    );
                });
    }

    private void validatePersistedComposition(
            List<MatchPlanParticipant> participants
    ) {
        try {
            validateStructure(participants.stream()
                    .map(participant -> new MatchPlanAssignment(
                            participant.sessionParticipantId(),
                            participant.teamSide(), participant.teamSlot()
                    ))
                    .toList());
        } catch (InvalidMatchPlanRequestException exception) {
            throw new MatchPlanConflictException(
                    "Persisted MatchPlan composition is not startable"
            );
        }
    }

    private List<MatchPlanParticipant> saveAssignments(
            MatchPlan plan,
            List<MatchPlanAssignment> assignments
    ) {
        return participantRepository.saveAllAndFlush(assignments.stream()
                        .map(assignment -> MatchPlanParticipant.assign(
                                plan.id(), plan.sessionId(),
                                assignment.sessionParticipantId(),
                                assignment.teamSide(), assignment.teamSlot()
                        ))
                        .map(MatchPlanParticipantEntity::from)
                        .toList())
                .stream()
                .map(MatchPlanParticipantEntity::toDomain)
                .sorted(Comparator.comparing(MatchPlanParticipant::teamSide)
                        .thenComparingInt(MatchPlanParticipant::teamSlot))
                .toList();
    }

    private void compactQueue(
            List<MatchPlanEntity> originalQueue,
            UUID removedPlanId,
            Instant now
    ) {
        List<MatchPlanEntity> remaining = originalQueue.stream()
                .filter(candidate -> !candidate.getId().equals(removedPlanId))
                .sorted(Comparator.comparingInt(
                        MatchPlanEntity::getQueuePosition
                ))
                .toList();
        for (int index = 0; index < remaining.size(); index++) {
            MatchPlanEntity candidate = remaining.get(index);
            int expectedPosition = index + 1;
            if (candidate.getQueuePosition() != expectedPosition) {
                candidate.apply(candidate.toDomain().reorder(
                        expectedPosition, now
                ));
                planRepository.flush();
            }
        }
    }

    private void lockCourtsInOrder(
            UUID sessionId,
            UUID firstCourtId,
            UUID secondCourtId
    ) {
        List<UUID> courtIds = List.of(firstCourtId, secondCourtId)
                .stream()
                .distinct()
                .sorted()
                .toList();
        courtIds.forEach(courtId ->
                sessionRuntimeLookup.requireScopedSessionCourtForUpdate(
                        sessionId, courtId
                ));
    }

    private List<MatchPlanEntity> queueForUpdate(UUID courtId) {
        return planRepository.findQueueForUpdate(
                courtId, MatchPlanStatus.QUEUED
        );
    }

    private MatchPlanEntity requirePlanForUpdate(UUID planId) {
        return planRepository.findByIdForUpdate(planId)
                .orElseThrow(() -> new MatchPlanNotFoundException(planId));
    }

    private MatchPlan requireQueued(MatchPlan plan, String action) {
        if (plan.status() != MatchPlanStatus.QUEUED) {
            throw new MatchPlanConflictException(
                    "MatchPlan cannot " + action + " from status "
                            + plan.status()
            );
        }
        return plan;
    }

    private void requireInProgress(Session session) {
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new MatchPlanConflictException(
                    "MatchPlan operation requires Session IN_PROGRESS"
            );
        }
    }

    private MatchPlanDetails details(MatchPlanEntity entity) {
        return new MatchPlanDetails(
                entity.toDomain(), loadComposition(entity.getId())
        );
    }

    private List<MatchPlanParticipant> loadComposition(UUID planId) {
        return participantRepository
                .findAllByMatchPlanIdOrderByTeamSideAscTeamSlotAsc(planId)
                .stream()
                .map(MatchPlanParticipantEntity::toDomain)
                .toList();
    }

    private record TeamSlot(TeamSide side, int slot) {
    }
}

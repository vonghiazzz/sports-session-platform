package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.InvalidManualMatchRequestException;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchNotFoundException;
import com.sportssession.platform.match.domain.MatchResourceConflictException;
import com.sportssession.platform.match.domain.MatchResult;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.match.infrastructure.MatchEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantEntity;
import com.sportssession.platform.match.infrastructure.MatchParticipantRepository;
import com.sportssession.platform.match.infrastructure.MatchRepository;
import com.sportssession.platform.session.application.SessionRuntimeLookup;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchService {

    private static final Set<TeamSlot> REQUIRED_TEAM_SLOTS = Set.of(
            new TeamSlot(TeamSide.A, 1),
            new TeamSlot(TeamSide.A, 2),
            new TeamSlot(TeamSide.B, 1),
            new TeamSlot(TeamSide.B, 2)
    );

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final SessionRuntimeLookup sessionRuntimeLookup;
    private final Clock clock;

    public MatchService(
            MatchRepository matchRepository,
            MatchParticipantRepository matchParticipantRepository,
            SessionRuntimeLookup sessionRuntimeLookup,
            Clock clock
    ) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.sessionRuntimeLookup = sessionRuntimeLookup;
        this.clock = clock;
    }

    @Transactional
    public CreatedManualMatch createManualMatch(CreateManualMatchCommand command) {
        List<ValidatedAssignment> assignments = validateStructure(
                command.participants().stream()
                        .map(this::validatedAssignment)
                        .toList(),
                "A Manual Match requires exactly 4 participant assignments"
        );

        Session session = sessionRuntimeLookup.requireSession(command.sessionId());
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new MatchResourceConflictException(
                    "Manual Match can be created only while Session is IN_PROGRESS"
            );
        }

        SessionCourt sessionCourt = sessionRuntimeLookup.requireSessionCourt(
                command.sessionId(),
                command.sessionCourtId()
        );
        if (!sessionCourt.sessionId().equals(command.sessionId())) {
            throw new MatchResourceConflictException(
                    "Session Court belongs to a different Session"
            );
        }
        if (sessionCourt.status() != SessionCourtStatus.AVAILABLE) {
            throw new MatchResourceConflictException(
                    "Session Court must be AVAILABLE to create a Manual Match"
            );
        }

        for (ValidatedAssignment assignment : assignments) {
            SessionParticipant participant =
                    sessionRuntimeLookup.requireSessionParticipant(
                            command.sessionId(),
                            assignment.sessionParticipantId()
                    );

            if (!participant.sessionId().equals(command.sessionId())) {
                throw new MatchResourceConflictException(
                        "Session Participant belongs to a different Session: "
                                + participant.id()
                );
            }
            if (participant.status() != ParticipantStatus.WAITING) {
                throw new MatchResourceConflictException(
                        "Session Participant must be WAITING: " + participant.id()
                );
            }
        }

        Instant now = clock.instant();
        Match match = Match.create(
                command.sessionId(),
                command.sessionCourtId(),
                MatchSource.MANUAL,
                now
        );

        MatchEntity persistedMatch = matchRepository.saveAndFlush(
                MatchEntity.from(match)
        );

        List<MatchParticipantEntity> participantEntities = assignments.stream()
                .map(assignment -> MatchParticipant.assign(
                        match.id(),
                        assignment.sessionParticipantId(),
                        assignment.teamSide(),
                        assignment.teamSlot()
                ))
                .map(MatchParticipantEntity::from)
                .toList();

        List<MatchParticipant> persistedParticipants =
                matchParticipantRepository
                        .saveAllAndFlush(participantEntities)
                        .stream()
                        .map(MatchParticipantEntity::toDomain)
                        .toList();

        return new CreatedManualMatch(
                persistedMatch.toDomain(),
                persistedParticipants
        );
    }

    @Transactional
    public StartedMatch createAndStartRecommendedMatch(
            CreateAndStartRecommendedMatchCommand command
    ) {
        List<ValidatedAssignment> assignments = validateStructure(
                command.participants().stream()
                        .map(this::validatedAssignment)
                        .toList(),
                "A Recommended Match requires exactly 4 participant assignments"
        );

        Session session = sessionRuntimeLookup.requireSessionForUpdate(
                command.sessionId()
        );
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new MatchResourceConflictException(
                    "Recommended Match can Start only while Session is IN_PROGRESS"
            );
        }

        SessionCourt sessionCourt = sessionRuntimeLookup
                .requireScopedSessionCourtForUpdate(
                        command.sessionId(),
                        command.sessionCourtId()
                );
        if (sessionCourt.status() != SessionCourtStatus.AVAILABLE) {
            throw new MatchResourceConflictException(
                    "Session Court must be AVAILABLE to Start Recommended Match"
            );
        }

        List<UUID> participantIds = assignments.stream()
                .map(ValidatedAssignment::sessionParticipantId)
                .sorted()
                .toList();
        List<SessionParticipant> participants = sessionRuntimeLookup
                .requireSessionParticipantsForUpdate(
                        command.sessionId(),
                        participantIds
                );
        Set<UUID> selectedParticipantIds = new HashSet<>(participantIds);
        Set<UUID> lockedParticipantIds = new HashSet<>();
        participants.forEach(participant ->
                lockedParticipantIds.add(participant.id()));
        if (participants.size() != 4
                || !lockedParticipantIds.equals(selectedParticipantIds)) {
            throw new MatchResourceConflictException(
                    "Recommended Match Participants could not be locked exactly"
            );
        }
        for (SessionParticipant participant : participants) {
            if (!participant.sessionId().equals(command.sessionId())) {
                throw new MatchResourceConflictException(
                        "Session Participant belongs to a different Session: "
                                + participant.id()
                );
            }
            if (participant.status() != ParticipantStatus.WAITING) {
                throw new MatchResourceConflictException(
                        "Session Participant must be WAITING to Start Recommended Match: "
                                + participant.id()
                );
            }
        }

        Instant startTime = clock.instant();
        Match startedMatch = Match.create(
                command.sessionId(),
                command.sessionCourtId(),
                MatchSource.RECOMMENDATION,
                startTime
        ).start(startTime);
        SessionCourt playingCourt = sessionCourt.startMatch(startTime);
        List<SessionParticipant> playingParticipants = participants.stream()
                .map(participant -> participant.startMatch(startTime))
                .toList();

        MatchEntity persistedMatch = matchRepository.saveAndFlush(
                MatchEntity.from(startedMatch)
        );
        List<MatchParticipantEntity> participantEntities = assignments.stream()
                .map(assignment -> MatchParticipant.assign(
                        startedMatch.id(),
                        assignment.sessionParticipantId(),
                        assignment.teamSide(),
                        assignment.teamSlot()
                ))
                .map(MatchParticipantEntity::from)
                .toList();
        List<MatchParticipant> persistedParticipants =
                matchParticipantRepository
                        .saveAllAndFlush(participantEntities)
                        .stream()
                        .map(MatchParticipantEntity::toDomain)
                        .toList();

        sessionRuntimeLookup.applyMatchRuntimeState(
                playingCourt,
                playingParticipants
        );
        matchRepository.flush();

        return new StartedMatch(
                persistedMatch.toDomain(),
                persistedParticipants
        );
    }

    @Transactional
    public StartedMatch startMatch(UUID matchId) {
        MatchEntity matchEntity = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        Match match = matchEntity.toDomain();

        if (match.status() != MatchStatus.CREATED) {
            throw new MatchResourceConflictException(
                    "Match must be CREATED to Start: " + match.id()
            );
        }

        Session session = sessionRuntimeLookup
                .requireSessionForUpdate(match.sessionId());
        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new MatchResourceConflictException(
                    "Match can Start only while Session is IN_PROGRESS"
            );
        }

        SessionCourt sessionCourt = sessionRuntimeLookup
                .requireSessionCourtForUpdate(
                        match.sessionId(),
                        match.sessionCourtId()
                );
        if (!sessionCourt.sessionId().equals(match.sessionId())) {
            throw new MatchResourceConflictException(
                    "Session Court belongs to a different Session"
            );
        }
        if (sessionCourt.status() != SessionCourtStatus.AVAILABLE) {
            throw new MatchResourceConflictException(
                    "Session Court must be AVAILABLE to Start Match"
            );
        }

        List<MatchParticipant> composition = validatePersistedComposition(
                matchParticipantRepository
                        .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(match.id())
                        .stream()
                        .map(MatchParticipantEntity::toDomain)
                        .toList()
        );

        List<UUID> participantIds = composition.stream()
                .map(MatchParticipant::sessionParticipantId)
                .sorted()
                .toList();
        List<SessionParticipant> participants = sessionRuntimeLookup
                .requireSessionParticipantsForUpdate(
                        match.sessionId(),
                        participantIds
                );

        for (SessionParticipant participant : participants) {
            if (!participant.sessionId().equals(match.sessionId())) {
                throw new MatchResourceConflictException(
                        "Session Participant belongs to a different Session: "
                                + participant.id()
                );
            }
            if (participant.status() != ParticipantStatus.WAITING) {
                throw new MatchResourceConflictException(
                        "Session Participant must be WAITING to Start Match: "
                                + participant.id()
                );
            }
        }

        Instant startTime = clock.instant();
        Match startedMatch = match.start(startTime);
        SessionCourt playingCourt = sessionCourt.startMatch(startTime);
        List<SessionParticipant> playingParticipants = participants.stream()
                .map(participant -> participant.startMatch(startTime))
                .toList();

        matchEntity.applyRuntimeState(startedMatch);
        sessionRuntimeLookup.applyMatchRuntimeState(
                playingCourt,
                playingParticipants
        );
        matchRepository.flush();

        return new StartedMatch(matchEntity.toDomain(), composition);
    }

    @Transactional
    public ResolvedMatch completeMatch(CompleteMatchCommand command) {
        MatchEntity matchEntity = matchRepository
                .findByIdForUpdate(command.matchId())
                .orElseThrow(() -> new MatchNotFoundException(command.matchId()));
        Match match = matchEntity.toDomain();

        if (match.status() != MatchStatus.PLAYING) {
            throw new MatchResourceConflictException(
                    "Match must be PLAYING to Complete: " + match.id()
            );
        }

        LockedPlayingResources resources = lockPlayingResources(
                match,
                "Complete"
        );
        MatchResult result = new MatchResult(
                command.winnerTeam(),
                command.teamAScore(),
                command.teamBScore()
        );
        Instant completionTime = clock.instant();

        Match completedMatch = match.complete(result, completionTime);
        SessionCourt availableCourt = resources.sessionCourt()
                .releaseFromMatch(completionTime);
        List<SessionParticipant> waitingParticipants = resources.participants()
                .stream()
                .map(participant -> participant.releaseFromMatch(completionTime))
                .toList();

        matchEntity.applyRuntimeState(completedMatch);
        sessionRuntimeLookup.applyMatchRuntimeState(
                availableCourt,
                waitingParticipants
        );
        matchRepository.flush();

        return new ResolvedMatch(
                matchEntity.toDomain(),
                resources.composition()
        );
    }

    @Transactional
    public ResolvedMatch cancelMatch(UUID matchId) {
        MatchEntity matchEntity = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
        Match match = matchEntity.toDomain();

        if (match.status() == MatchStatus.CREATED) {
            Instant cancellationTime = clock.instant();
            Match cancelledMatch = match.cancel(cancellationTime);
            List<MatchParticipant> composition = loadComposition(match.id());

            matchEntity.applyRuntimeState(cancelledMatch);
            matchRepository.flush();

            return new ResolvedMatch(
                    matchEntity.toDomain(),
                    composition
            );
        }

        if (match.status() != MatchStatus.PLAYING) {
            throw new MatchResourceConflictException(
                    "Match cannot Cancel from status " + match.status()
            );
        }

        LockedPlayingResources resources = lockPlayingResources(
                match,
                "Cancel"
        );
        Instant cancellationTime = clock.instant();

        Match cancelledMatch = match.cancel(cancellationTime);
        SessionCourt availableCourt = resources.sessionCourt()
                .releaseFromMatch(cancellationTime);
        List<SessionParticipant> waitingParticipants = resources.participants()
                .stream()
                .map(participant -> participant.releaseFromMatch(cancellationTime))
                .toList();

        matchEntity.applyRuntimeState(cancelledMatch);
        sessionRuntimeLookup.applyMatchRuntimeState(
                availableCourt,
                waitingParticipants
        );
        matchRepository.flush();

        return new ResolvedMatch(
                matchEntity.toDomain(),
                resources.composition()
        );
    }

    private LockedPlayingResources lockPlayingResources(
            Match match,
            String action
    ) {
        SessionCourt sessionCourt = sessionRuntimeLookup
                .requireSessionCourtForUpdate(
                        match.sessionId(),
                        match.sessionCourtId()
                );
        if (!sessionCourt.sessionId().equals(match.sessionId())) {
            throw new MatchResourceConflictException(
                    "Session Court belongs to a different Session"
            );
        }
        if (sessionCourt.status() != SessionCourtStatus.PLAYING) {
            throw new MatchResourceConflictException(
                    "Session Court must be PLAYING to " + action + " Match"
            );
        }

        List<MatchParticipant> composition = validatePersistedComposition(
                loadComposition(match.id()),
                "Persisted Match composition is not valid for "
                        + action + " Match"
        );
        List<UUID> participantIds = composition.stream()
                .map(MatchParticipant::sessionParticipantId)
                .sorted()
                .toList();
        List<SessionParticipant> participants = sessionRuntimeLookup
                .requireSessionParticipantsForUpdate(
                        match.sessionId(),
                        participantIds
                );

        for (SessionParticipant participant : participants) {
            if (!participant.sessionId().equals(match.sessionId())) {
                throw new MatchResourceConflictException(
                        "Session Participant belongs to a different Session: "
                                + participant.id()
                );
            }
            if (participant.status() != ParticipantStatus.PLAYING) {
                throw new MatchResourceConflictException(
                        "Session Participant must be PLAYING to "
                                + action + " Match: " + participant.id()
                );
            }
        }

        return new LockedPlayingResources(
                sessionCourt,
                participants,
                composition
        );
    }

    private List<MatchParticipant> loadComposition(UUID matchId) {
        return matchParticipantRepository
                .findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(matchId)
                .stream()
                .map(MatchParticipantEntity::toDomain)
                .toList();
    }

    private List<ValidatedAssignment> validateStructure(
            List<ValidatedAssignment> assignments,
            String countFailureMessage
    ) {
        if (assignments.size() != 4) {
            throw new InvalidManualMatchRequestException(
                    countFailureMessage
            );
        }

        Set<UUID> participantIds = new HashSet<>();
        Set<TeamSlot> teamSlots = new HashSet<>();
        int teamACount = 0;
        int teamBCount = 0;
        boolean duplicateTeamSlot = false;

        for (ValidatedAssignment assignment : assignments) {
            if (assignment == null
                    || assignment.sessionParticipantId() == null
                    || assignment.teamSide() == null) {
                throw new InvalidManualMatchRequestException(
                        "Every participant assignment must be complete"
                );
            }
            if (assignment.teamSlot() != 1 && assignment.teamSlot() != 2) {
                throw new InvalidManualMatchRequestException(
                        "teamSlot must be 1 or 2"
                );
            }
            if (!participantIds.add(assignment.sessionParticipantId())) {
                throw new InvalidManualMatchRequestException(
                        "Participant assignments must use unique sessionParticipantIds"
                );
            }
            if (assignment.teamSide() == TeamSide.A) {
                teamACount++;
            } else {
                teamBCount++;
            }
            if (!teamSlots.add(new TeamSlot(
                    assignment.teamSide(),
                    assignment.teamSlot()
            ))) {
                duplicateTeamSlot = true;
            }
        }

        if (teamACount != 2 || teamBCount != 2) {
            throw new InvalidManualMatchRequestException(
                    "Teams A and B must each contain exactly 2 Participants"
            );
        }
        if (duplicateTeamSlot) {
            throw new InvalidManualMatchRequestException(
                    "Participant assignments must not duplicate a team slot"
            );
        }
        if (!teamSlots.equals(REQUIRED_TEAM_SLOTS)) {
            throw new InvalidManualMatchRequestException(
                    "Teams A and B must each contain exactly slots 1 and 2"
            );
        }

        return assignments.stream()
                .sorted(Comparator
                        .comparing(ValidatedAssignment::teamSide)
                        .thenComparingInt(
                                ValidatedAssignment::teamSlot
                        ))
                .toList();
    }

    private ValidatedAssignment validatedAssignment(
            ManualMatchParticipantAssignment assignment
    ) {
        return assignment == null
                ? null
                : new ValidatedAssignment(
                assignment.sessionParticipantId(),
                assignment.teamSide(),
                assignment.teamSlot()
        );
    }

    private ValidatedAssignment validatedAssignment(
            RecommendedMatchParticipantAssignment assignment
    ) {
        return assignment == null
                ? null
                : new ValidatedAssignment(
                assignment.sessionParticipantId(),
                assignment.teamSide(),
                assignment.teamSlot()
        );
    }

    private List<MatchParticipant> validatePersistedComposition(
            List<MatchParticipant> participants
    ) {
        return validatePersistedComposition(
                participants,
                "Persisted Match composition is not startable"
        );
    }

    private List<MatchParticipant> validatePersistedComposition(
            List<MatchParticipant> participants,
            String conflictMessage
    ) {
        if (participants.size() != 4) {
            throw invalidPersistedComposition(conflictMessage);
        }

        Set<UUID> participantIds = new HashSet<>();
        Set<TeamSlot> teamSlots = new HashSet<>();
        int teamACount = 0;
        int teamBCount = 0;

        for (MatchParticipant participant : participants) {
            if (!participantIds.add(participant.sessionParticipantId())
                    || !teamSlots.add(new TeamSlot(
                    participant.teamSide(),
                    participant.teamSlot()
            ))) {
                throw invalidPersistedComposition(conflictMessage);
            }

            if (participant.teamSide() == TeamSide.A) {
                teamACount++;
            } else {
                teamBCount++;
            }
        }

        if (teamACount != 2
                || teamBCount != 2
                || !teamSlots.equals(REQUIRED_TEAM_SLOTS)) {
            throw invalidPersistedComposition(conflictMessage);
        }

        return participants.stream()
                .sorted(Comparator
                        .comparing(MatchParticipant::teamSide)
                        .thenComparingInt(MatchParticipant::teamSlot))
                .toList();
    }

    private MatchResourceConflictException invalidPersistedComposition(
            String message
    ) {
        return new MatchResourceConflictException(message);
    }

    private record TeamSlot(TeamSide teamSide, int teamSlot) {
    }

    private record ValidatedAssignment(
            UUID sessionParticipantId,
            TeamSide teamSide,
            int teamSlot
    ) {
    }

    private record LockedPlayingResources(
            SessionCourt sessionCourt,
            List<SessionParticipant> participants,
            List<MatchParticipant> composition
    ) {
    }
}

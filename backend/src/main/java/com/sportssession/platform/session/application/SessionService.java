package com.sportssession.platform.session.application;

import com.sportssession.platform.player.application.PlayerLookup;
import com.sportssession.platform.player.domain.PlayerNotFoundException;
import com.sportssession.platform.session.domain.DuplicateSessionCourtException;
import com.sportssession.platform.session.domain.DuplicateSessionParticipantException;
import com.sportssession.platform.session.domain.InvalidBuddyPairRequestException;
import com.sportssession.platform.session.domain.InvalidSessionStateException;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionBuddyPair;
import com.sportssession.platform.session.domain.SessionBuddyPairNotFoundException;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtNotFoundException;
import com.sportssession.platform.session.domain.SessionNotFoundException;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionParticipantNotFoundException;
import com.sportssession.platform.session.domain.SessionResourceConflictException;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.session.infrastructure.SessionCourtEntity;
import com.sportssession.platform.session.infrastructure.SessionCourtRepository;
import com.sportssession.platform.session.infrastructure.SessionEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantEntity;
import com.sportssession.platform.session.infrastructure.SessionParticipantRepository;
import com.sportssession.platform.session.infrastructure.SessionRepository;
import com.sportssession.platform.venue.application.CourtLookup;
import com.sportssession.platform.venue.application.CourtSnapshot;
import com.sportssession.platform.venue.application.VenueLookup;
import com.sportssession.platform.venue.application.VenueSnapshot;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final String PARTICIPANT_UNIQUE_CONSTRAINT =
            "uk_session_participants_session_player";

    private static final String SESSION_COURT_UNIQUE_CONSTRAINT =
            "uk_session_courts_session_court";

    private final SessionRepository sessionRepository;
    private final SessionParticipantRepository participantRepository;
    private final SessionCourtRepository sessionCourtRepository;
    private final SessionActiveMatchLookup activeMatchLookup;

    private final PlayerLookup playerLookup;
    private final VenueLookup venueLookup;
    private final CourtLookup courtLookup;

    private final Clock clock;

    public SessionService(
            SessionRepository sessionRepository,
            SessionParticipantRepository participantRepository,
            SessionCourtRepository sessionCourtRepository,
            SessionActiveMatchLookup activeMatchLookup,
            PlayerLookup playerLookup,
            VenueLookup venueLookup,
            CourtLookup courtLookup,
            Clock clock
    ) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.sessionCourtRepository = sessionCourtRepository;
        this.activeMatchLookup = activeMatchLookup;
        this.playerLookup = playerLookup;
        this.venueLookup = venueLookup;
        this.courtLookup = courtLookup;
        this.clock = clock;
    }

    @Transactional
    public Session createSession(CreateSessionCommand command) {
        VenueSnapshot venue = venueLookup.requireVenue(command.venueId());

        if (!venue.active()) {
            throw new SessionResourceConflictException(
                    "Cannot create a Session for inactive Venue: " + venue.id()
            );
        }

        Instant now = clock.instant();

        Session session = Session.create(
                venue.id(),
                command.title(),
                command.sport(),
                command.matchFormat(),
                command.plannedStartAt(),
                command.plannedEndAt(),
                now
        );

        SessionEntity entity = SessionEntity.from(session);

        return sessionRepository
                .saveAndFlush(entity)
                .toDomain();
    }

    @Transactional(readOnly = true)
    public Session getSession(UUID sessionId) {
        return findSessionEntity(sessionId).toDomain();
    }

    @Transactional
    public Session startSession(UUID sessionId) {
        SessionEntity entity = findSessionEntity(sessionId);

        Instant now = clock.instant();

        Session updated = entity.toDomain().start(now);

        entity.applyRuntimeState(updated);

        sessionRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public Session completeSession(UUID sessionId) {
        SessionEntity entity = findSessionEntityForUpdate(sessionId);
        Session session = entity.toDomain();

        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new InvalidSessionStateException(
                    "Session cannot complete from status " + session.status()
            );
        }

        if (activeMatchLookup.hasPlayingMatch(sessionId)) {
            throw new SessionResourceConflictException(
                    "Session cannot complete while a Match is PLAYING"
            );
        }

        Instant now = clock.instant();

        Session updated = session.complete(now);

        entity.applyRuntimeState(updated);

        sessionRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public Session cancelSession(UUID sessionId) {
        SessionEntity entity = findSessionEntity(sessionId);

        Instant now = clock.instant();

        Session updated = entity.toDomain().cancel(now);

        entity.applyRuntimeState(updated);

        sessionRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionParticipant addParticipant(AddParticipantCommand command) {
        Session session = findSessionEntity(command.sessionId()).toDomain();

        requireSessionOpenForAllocation(
                session,
                "add Participants"
        );

        if (!playerLookup.exists(command.playerId())) {
            throw new PlayerNotFoundException(command.playerId());
        }

        if (participantRepository.existsBySessionIdAndPlayerId(
                command.sessionId(),
                command.playerId()
        )) {
            throw new DuplicateSessionParticipantException(
                    command.sessionId(),
                    command.playerId()
            );
        }

        Instant now = clock.instant();

        SessionParticipant participant =
                SessionParticipant.register(
                        command.sessionId(),
                        command.playerId(),
                        now
                );

        try {
            SessionParticipantEntity entity =
                    SessionParticipantEntity.from(participant);

            return participantRepository
                    .saveAndFlush(entity)
                    .toDomain();

        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(
                    exception,
                    PARTICIPANT_UNIQUE_CONSTRAINT
            )) {
                throw new DuplicateSessionParticipantException(
                        command.sessionId(),
                        command.playerId(),
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<SessionParticipant> listParticipants(UUID sessionId) {
        findSessionEntity(sessionId);

        return participantRepository
                .findAllBySessionIdOrderByJoinedAtAscIdAsc(sessionId)
                .stream()
                .map(SessionParticipantEntity::toDomain)
                .toList();
    }

    @Transactional
    public SessionBuddyPair createBuddyPair(
            UUID sessionId,
            UUID firstSessionParticipantId,
            UUID secondSessionParticipantId
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(
                firstSessionParticipantId,
                "firstSessionParticipantId is required"
        );
        Objects.requireNonNull(
                secondSessionParticipantId,
                "secondSessionParticipantId is required"
        );
        if (firstSessionParticipantId.equals(secondSessionParticipantId)) {
            throw new InvalidBuddyPairRequestException(
                    "Buddy Pair requires two different SessionParticipants"
            );
        }
        requireBuddyMutableSession(sessionId);

        List<UUID> participantIds = List.of(
                firstSessionParticipantId,
                secondSessionParticipantId
        ).stream().sorted().toList();
        List<SessionParticipantEntity> lockedParticipants =
                participantRepository.findAllByIdForUpdateOrderById(
                        participantIds
                );
        Map<UUID, SessionParticipantEntity> participantById =
                lockedParticipants.stream().collect(Collectors.toMap(
                        SessionParticipantEntity::getId,
                        Function.identity()
                ));
        SessionParticipantEntity first = requireBuddyParticipant(
                sessionId,
                firstSessionParticipantId,
                participantById
        );
        SessionParticipantEntity second = requireBuddyParticipant(
                sessionId,
                secondSessionParticipantId,
                participantById
        );
        if (first.getBuddyPairId() != null || second.getBuddyPairId() != null) {
            throw new SessionResourceConflictException(
                    "Each SessionParticipant may belong to only one Buddy Pair"
            );
        }

        SessionBuddyPair buddyPair = SessionBuddyPair.create(
                sessionId,
                firstSessionParticipantId,
                secondSessionParticipantId
        );
        Instant now = clock.instant();
        first.applyRuntimeState(first.toDomain().assignBuddyPair(
                buddyPair.id(),
                now
        ));
        second.applyRuntimeState(second.toDomain().assignBuddyPair(
                buddyPair.id(),
                now
        ));
        participantRepository.flush();
        return buddyPair;
    }

    @Transactional
    public void removeBuddyPair(UUID sessionId, UUID buddyPairId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(buddyPairId, "buddyPairId is required");
        requireBuddyMutableSession(sessionId);
        List<SessionParticipantEntity> members =
                participantRepository.findBuddyPairMembersForUpdate(
                        sessionId,
                        buddyPairId
                );
        if (members.isEmpty()) {
            throw new SessionBuddyPairNotFoundException(
                    sessionId,
                    buddyPairId
            );
        }
        if (members.size() != 2) {
            throw new SessionResourceConflictException(
                    "Buddy Pair must contain exactly two SessionParticipants"
            );
        }
        Instant now = clock.instant();
        members.forEach(member -> member.applyRuntimeState(
                member.toDomain().removeBuddyPair(buddyPairId, now)
        ));
        participantRepository.flush();
    }

    @Transactional
    public SessionParticipant checkInParticipant(
            UUID sessionId,
            UUID participantId
    ) {
        Session session = findSessionEntity(sessionId).toDomain();

        if (session.status() != SessionStatus.IN_PROGRESS) {
            throw new InvalidSessionStateException(
                    "Participants can check in only while Session is IN_PROGRESS"
            );
        }

        SessionParticipantEntity entity =
                findParticipantEntity(
                        sessionId,
                        participantId
                );

        Instant now = clock.instant();

        SessionParticipant updated =
                entity.toDomain().checkIn(now);

        entity.applyRuntimeState(updated);

        participantRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionParticipant pauseParticipant(
            UUID sessionId,
            UUID participantId
    ) {
        requireMutableSession(sessionId);

        SessionParticipantEntity entity =
                findParticipantEntity(
                        sessionId,
                        participantId
                );

        Instant now = clock.instant();

        SessionParticipant updated =
                entity.toDomain().pause(now);

        entity.applyRuntimeState(updated);

        participantRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionParticipant resumeParticipant(
            UUID sessionId,
            UUID participantId
    ) {
        requireMutableSession(sessionId);

        SessionParticipantEntity entity =
                findParticipantEntity(
                        sessionId,
                        participantId
                );

        Instant now = clock.instant();

        SessionParticipant updated =
                entity.toDomain().resume(now);

        entity.applyRuntimeState(updated);

        participantRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionParticipant leaveParticipant(
            UUID sessionId,
            UUID participantId
    ) {
        requireMutableSession(sessionId);

        SessionParticipantEntity entity =
                findParticipantEntity(
                        sessionId,
                        participantId
                );

        Instant now = clock.instant();

        SessionParticipant updated =
                entity.toDomain().leave(now);

        entity.applyRuntimeState(updated);

        participantRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionCourt addCourt(
            AddSessionCourtCommand command
    ) {
        Session session =
                findSessionEntity(command.sessionId())
                        .toDomain();

        requireSessionOpenForAllocation(
                session,
                "allocate Courts"
        );

        CourtSnapshot court =
                courtLookup.requireCourt(command.courtId());

        VenueSnapshot venue =
                venueLookup.requireVenue(session.venueId());

        if (!venue.active()) {
            throw new SessionResourceConflictException(
                    "Cannot allocate a Court from inactive Venue: "
                            + venue.id()
            );
        }

        if (!court.active()) {
            throw new SessionResourceConflictException(
                    "Cannot allocate inactive Court: "
                            + court.id()
            );
        }

        if (!court.venueId().equals(session.venueId())) {
            throw new SessionResourceConflictException(
                    "Court belongs to a different Venue than Session"
            );
        }

        if (court.sportCode() != session.sportCode()) {
            throw new SessionResourceConflictException(
                    "Court sport does not match Session sport"
            );
        }

        if (sessionCourtRepository.existsBySessionIdAndCourtId(
                command.sessionId(),
                command.courtId()
        )) {
            throw new DuplicateSessionCourtException(
                    command.sessionId(),
                    command.courtId()
            );
        }

        Instant now = clock.instant();

        SessionCourt sessionCourt =
                SessionCourt.allocate(
                        command.sessionId(),
                        command.courtId(),
                        now
                );

        try {
            SessionCourtEntity entity =
                    SessionCourtEntity.from(sessionCourt);

            return sessionCourtRepository
                    .saveAndFlush(entity)
                    .toDomain();

        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(
                    exception,
                    SESSION_COURT_UNIQUE_CONSTRAINT
            )) {
                throw new DuplicateSessionCourtException(
                        command.sessionId(),
                        command.courtId(),
                        exception
                );
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<SessionCourt> listCourts(UUID sessionId) {
        findSessionEntity(sessionId);

        return sessionCourtRepository
                .findAllBySessionIdOrderByAddedAtAscIdAsc(sessionId)
                .stream()
                .map(SessionCourtEntity::toDomain)
                .toList();
    }

    @Transactional
    public SessionCourt disableCourt(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        requireMutableSession(sessionId);

        SessionCourtEntity entity =
                findSessionCourtEntity(
                        sessionId,
                        sessionCourtId
                );

        Instant now = clock.instant();

        SessionCourt updated =
                entity.toDomain().disable(now);

        entity.applyRuntimeState(updated);

        sessionCourtRepository.flush();

        return entity.toDomain();
    }

    @Transactional
    public SessionCourt enableCourt(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        requireMutableSession(sessionId);

        SessionCourtEntity entity =
                findSessionCourtEntity(
                        sessionId,
                        sessionCourtId
                );

        Instant now = clock.instant();

        SessionCourt updated =
                entity.toDomain().enable(now);

        entity.applyRuntimeState(updated);

        sessionCourtRepository.flush();

        return entity.toDomain();
    }

    private SessionEntity findSessionEntity(UUID sessionId) {
        return sessionRepository
                .findById(sessionId)
                .orElseThrow(
                        () -> new SessionNotFoundException(sessionId)
                );
    }

    private SessionEntity findSessionEntityForUpdate(UUID sessionId) {
        return sessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(
                        () -> new SessionNotFoundException(sessionId)
                );
    }

    private SessionParticipantEntity findParticipantEntity(
            UUID sessionId,
            UUID participantId
    ) {
        return participantRepository
                .findByIdAndSessionId(
                        participantId,
                        sessionId
                )
                .orElseThrow(
                        () -> new SessionParticipantNotFoundException(
                                sessionId,
                                participantId
                        )
                );
    }

    private SessionParticipantEntity requireBuddyParticipant(
            UUID sessionId,
            UUID participantId,
            Map<UUID, SessionParticipantEntity> participantById
    ) {
        SessionParticipantEntity participant = participantById.get(
                participantId
        );
        if (participant == null
                || !participant.getSessionId().equals(sessionId)) {
            throw new SessionParticipantNotFoundException(
                    sessionId,
                    participantId
            );
        }
        return participant;
    }

    private Session requireBuddyMutableSession(UUID sessionId) {
        Session session = findSessionEntityForUpdate(sessionId).toDomain();
        if (session.isTerminal()) {
            throw new InvalidSessionStateException(
                    "Cannot modify Buddy Pairs while Session is "
                            + session.status()
            );
        }
        return session;
    }

    private SessionCourtEntity findSessionCourtEntity(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        return sessionCourtRepository
                .findByIdAndSessionId(
                        sessionCourtId,
                        sessionId
                )
                .orElseThrow(
                        () -> new SessionCourtNotFoundException(
                                sessionId,
                                sessionCourtId
                        )
                );
    }

    private Session requireMutableSession(UUID sessionId) {
        Session session =
                findSessionEntity(sessionId).toDomain();

        if (session.isTerminal()) {
            throw new InvalidSessionStateException(
                    "Cannot modify runtime state while Session is "
                            + session.status()
            );
        }

        return session;
    }

    private void requireSessionOpenForAllocation(
            Session session,
            String action
    ) {
        if (session.isTerminal()) {
            throw new InvalidSessionStateException(
                    "Cannot "
                            + action
                            + " while Session is "
                            + session.status()
            );
        }
    }

    private boolean violatesConstraint(
            Throwable exception,
            String constraintName
    ) {
        Throwable current = exception;

        while (current != null) {
            if (current
                    instanceof ConstraintViolationException constraintViolation
                    && constraintName.equals(
                    constraintViolation.getConstraintName()
            )) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}

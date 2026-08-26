package com.sportssession.platform.session.infrastructure;

import com.sportssession.platform.session.application.SessionRuntimeLookup;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionCourtNotFoundException;
import com.sportssession.platform.session.domain.SessionNotFoundException;
import com.sportssession.platform.session.domain.SessionParticipant;
import com.sportssession.platform.session.domain.SessionParticipantNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class SessionRuntimeLookupAdapter implements SessionRuntimeLookup {

    private final SessionRepository sessionRepository;
    private final SessionCourtRepository sessionCourtRepository;
    private final SessionParticipantRepository sessionParticipantRepository;

    public SessionRuntimeLookupAdapter(
            SessionRepository sessionRepository,
            SessionCourtRepository sessionCourtRepository,
            SessionParticipantRepository sessionParticipantRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.sessionCourtRepository = sessionCourtRepository;
        this.sessionParticipantRepository = sessionParticipantRepository;
    }

    @Override
    public Session requireSession(UUID sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId))
                .toDomain();
    }

    @Override
    public Session requireSessionForUpdate(UUID sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId))
                .toDomain();
    }

    @Override
    public SessionCourt requireSessionCourt(
            UUID requestedSessionId,
            UUID sessionCourtId
    ) {
        return sessionCourtRepository.findById(sessionCourtId)
                .orElseThrow(() -> new SessionCourtNotFoundException(
                        requestedSessionId,
                        sessionCourtId
                ))
                .toDomain();
    }

    @Override
    public SessionParticipant requireSessionParticipant(
            UUID requestedSessionId,
            UUID sessionParticipantId
    ) {
        return sessionParticipantRepository.findById(sessionParticipantId)
                .orElseThrow(() -> new SessionParticipantNotFoundException(
                        requestedSessionId,
                        sessionParticipantId
                ))
                .toDomain();
    }

    @Override
    public SessionCourt requireSessionCourtForUpdate(
            UUID requestedSessionId,
            UUID sessionCourtId
    ) {
        return sessionCourtRepository.findByIdForUpdate(sessionCourtId)
                .orElseThrow(() -> new SessionCourtNotFoundException(
                        requestedSessionId,
                        sessionCourtId
                ))
                .toDomain();
    }

    @Override
    public List<SessionParticipant> requireSessionParticipantsForUpdate(
            UUID requestedSessionId,
            List<UUID> sessionParticipantIds
    ) {
        List<UUID> orderedIds = sessionParticipantIds.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        List<SessionParticipant> participants = sessionParticipantRepository
                .findAllByIdForUpdateOrderById(orderedIds)
                .stream()
                .map(SessionParticipantEntity::toDomain)
                .toList();

        Set<UUID> foundIds = new HashSet<>();
        participants.forEach(participant -> foundIds.add(participant.id()));
        orderedIds.stream()
                .filter(participantId -> !foundIds.contains(participantId))
                .findFirst()
                .ifPresent(participantId -> {
                    throw new SessionParticipantNotFoundException(
                            requestedSessionId,
                            participantId
                    );
                });

        return participants;
    }

    @Override
    public void applyMatchRuntimeState(
            SessionCourt sessionCourt,
            List<SessionParticipant> sessionParticipants
    ) {
        SessionCourtEntity courtEntity = sessionCourtRepository
                .findById(sessionCourt.id())
                .orElseThrow(() -> new SessionCourtNotFoundException(
                        sessionCourt.sessionId(),
                        sessionCourt.id()
                ));
        courtEntity.applyRuntimeState(sessionCourt);

        sessionParticipants.stream()
                .sorted(Comparator.comparing(SessionParticipant::id))
                .forEach(participant -> {
                    SessionParticipantEntity participantEntity =
                            sessionParticipantRepository
                                    .findById(participant.id())
                                    .orElseThrow(() ->
                                            new SessionParticipantNotFoundException(
                                                    participant.sessionId(),
                                                    participant.id()
                                            ));
                    participantEntity.applyRuntimeState(participant);
                });

        sessionCourtRepository.flush();
        sessionParticipantRepository.flush();
    }
}

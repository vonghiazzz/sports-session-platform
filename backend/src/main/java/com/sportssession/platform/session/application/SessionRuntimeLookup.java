package com.sportssession.platform.session.application;

import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionParticipant;

import java.util.List;
import java.util.UUID;

public interface SessionRuntimeLookup {

    Session requireSession(UUID sessionId);

    Session requireSessionForUpdate(UUID sessionId);

    SessionCourt requireSessionCourt(UUID requestedSessionId, UUID sessionCourtId);

    SessionParticipant requireSessionParticipant(
            UUID requestedSessionId,
            UUID sessionParticipantId
    );

    SessionCourt requireSessionCourtForUpdate(
            UUID requestedSessionId,
            UUID sessionCourtId
    );

    SessionCourt requireScopedSessionCourtForUpdate(
            UUID requestedSessionId,
            UUID sessionCourtId
    );

    List<SessionParticipant> requireSessionParticipantsForUpdate(
            UUID requestedSessionId,
            List<UUID> sessionParticipantIds
    );

    void applyMatchRuntimeState(
            SessionCourt sessionCourt,
            List<SessionParticipant> sessionParticipants
    );
}

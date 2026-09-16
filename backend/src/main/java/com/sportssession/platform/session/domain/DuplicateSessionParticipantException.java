package com.sportssession.platform.session.domain;

import java.util.UUID;

public class DuplicateSessionParticipantException extends RuntimeException {

    public DuplicateSessionParticipantException(UUID sessionId, UUID playerId) {
        super("Player " + playerId + " is already a Participant in Session " + sessionId);
    }

    public DuplicateSessionParticipantException(
            UUID sessionId,
            UUID playerId,
            Throwable cause
    ) {
        super("Player " + playerId
                + " is already a Participant in Session " + sessionId, cause);
    }
}

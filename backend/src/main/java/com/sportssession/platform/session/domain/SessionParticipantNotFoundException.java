package com.sportssession.platform.session.domain;

import java.util.UUID;

public class SessionParticipantNotFoundException extends RuntimeException {

    public SessionParticipantNotFoundException(UUID sessionId, UUID participantId) {
        super("Session Participant not found in Session "
                + sessionId + ": " + participantId);
    }
}

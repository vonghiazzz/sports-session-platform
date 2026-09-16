package com.sportssession.platform.session.domain;

import java.util.UUID;

public class SessionCourtNotFoundException extends RuntimeException {

    public SessionCourtNotFoundException(UUID sessionId, UUID sessionCourtId) {
        super("Session Court not found in Session "
                + sessionId + ": " + sessionCourtId);
    }
}

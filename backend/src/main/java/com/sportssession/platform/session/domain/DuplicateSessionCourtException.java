package com.sportssession.platform.session.domain;

import java.util.UUID;

public class DuplicateSessionCourtException extends RuntimeException {

    public DuplicateSessionCourtException(UUID sessionId, UUID courtId) {
        super("Court " + courtId + " is already allocated to Session " + sessionId);
    }

    public DuplicateSessionCourtException(UUID sessionId, UUID courtId, Throwable cause) {
        super("Court " + courtId
                + " is already allocated to Session " + sessionId, cause);
    }
}

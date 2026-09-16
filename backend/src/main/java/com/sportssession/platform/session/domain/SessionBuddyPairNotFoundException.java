package com.sportssession.platform.session.domain;

import java.util.UUID;

public class SessionBuddyPairNotFoundException extends RuntimeException {

    public SessionBuddyPairNotFoundException(UUID sessionId, UUID buddyPairId) {
        super("Buddy Pair not found in Session " + sessionId + ": "
                + buddyPairId);
    }
}

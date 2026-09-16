package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.SessionBuddyPair;

import java.util.UUID;

public record BuddyPairResponse(
        UUID buddyPairId,
        UUID sessionId,
        UUID firstSessionParticipantId,
        UUID secondSessionParticipantId
) {
    static BuddyPairResponse from(SessionBuddyPair buddyPair) {
        return new BuddyPairResponse(
                buddyPair.id(),
                buddyPair.sessionId(),
                buddyPair.firstSessionParticipantId(),
                buddyPair.secondSessionParticipantId()
        );
    }
}

package com.sportssession.platform.session.domain;

import java.util.Objects;
import java.util.UUID;

public record SessionBuddyPair(
        UUID id,
        UUID sessionId,
        UUID firstSessionParticipantId,
        UUID secondSessionParticipantId
) {
    public SessionBuddyPair {
        Objects.requireNonNull(id, "id is required");
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
    }

    public static SessionBuddyPair create(
            UUID sessionId,
            UUID firstSessionParticipantId,
            UUID secondSessionParticipantId
    ) {
        return new SessionBuddyPair(
                UUID.randomUUID(),
                sessionId,
                firstSessionParticipantId,
                secondSessionParticipantId
        );
    }
}

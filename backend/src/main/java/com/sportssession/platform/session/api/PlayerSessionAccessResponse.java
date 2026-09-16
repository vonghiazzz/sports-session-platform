package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.SessionParticipant;

import java.util.UUID;

public record PlayerSessionAccessResponse(
        UUID sessionId,
        UUID sessionParticipantId
) {
    static PlayerSessionAccessResponse from(SessionParticipant participant) {
        return new PlayerSessionAccessResponse(
                participant.sessionId(),
                participant.id()
        );
    }
}

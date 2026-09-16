package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.SessionParticipant;

import java.util.UUID;

public record SessionParticipantPersonalAccessResponse(
        UUID sessionId,
        UUID sessionParticipantId,
        UUID personalAccessToken
) {
    static SessionParticipantPersonalAccessResponse from(
            SessionParticipant participant
    ) {
        return new SessionParticipantPersonalAccessResponse(
                participant.sessionId(),
                participant.id(),
                participant.personalAccessToken()
        );
    }
}

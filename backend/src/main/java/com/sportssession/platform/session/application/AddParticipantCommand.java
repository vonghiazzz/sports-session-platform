package com.sportssession.platform.session.application;

import java.util.UUID;

public record AddParticipantCommand(
        UUID sessionId,
        UUID playerId
) {
}

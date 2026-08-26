package com.sportssession.platform.session.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddParticipantRequest(
        @NotNull(message = "playerId is required")
        UUID playerId
) {
}

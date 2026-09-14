package com.sportssession.platform.session.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateBuddyPairRequest(
        @NotNull UUID firstSessionParticipantId,
        @NotNull UUID secondSessionParticipantId
) {
}

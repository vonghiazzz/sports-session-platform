package com.sportssession.platform.match.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateManualMatchRequest(
        @NotNull(message = "sessionCourtId is required")
        UUID sessionCourtId,

        @NotNull(message = "participants are required")
        @Size(
                min = 4,
                max = 4,
                message = "A Manual Match requires exactly 4 participant assignments"
        )
        List<
                @NotNull(message = "participant assignment is required")
                @Valid MatchParticipantRequest
                > participants
) {
}

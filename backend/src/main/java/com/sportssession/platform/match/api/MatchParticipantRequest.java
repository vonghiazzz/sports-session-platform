package com.sportssession.platform.match.api;

import com.sportssession.platform.match.application.ManualMatchParticipantAssignment;
import com.sportssession.platform.match.domain.TeamSide;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatchParticipantRequest(
        @NotNull(message = "sessionParticipantId is required")
        UUID sessionParticipantId,

        @NotNull(message = "teamSide is required")
        TeamSide teamSide,

        @NotNull(message = "teamSlot is required")
        @Min(value = 1, message = "teamSlot must be 1 or 2")
        @Max(value = 2, message = "teamSlot must be 1 or 2")
        Integer teamSlot
) {
    ManualMatchParticipantAssignment toAssignment() {
        return new ManualMatchParticipantAssignment(
                sessionParticipantId,
                teamSide,
                teamSlot
        );
    }
}

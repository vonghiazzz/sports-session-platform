package com.sportssession.platform.match.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateManualMatchCommand(
        UUID sessionId,
        UUID sessionCourtId,
        List<ManualMatchParticipantAssignment> participants
) {
    public CreateManualMatchCommand {
        participants = List.copyOf(Objects.requireNonNull(
                participants,
                "participants are required"
        ));
    }
}

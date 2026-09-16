package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.MatchSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateAndStartPlannedMatchCommand(
        UUID sessionId,
        UUID sessionCourtId,
        MatchSource source,
        List<ManualMatchParticipantAssignment> participants
) {
    public CreateAndStartPlannedMatchCommand {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(source, "source is required");
        participants = List.copyOf(Objects.requireNonNull(
                participants, "participants are required"
        ));
    }
}

package com.sportssession.platform.match.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CreateAndStartRecommendedMatchCommand(
        UUID sessionId,
        UUID sessionCourtId,
        List<RecommendedMatchParticipantAssignment> participants
) {
    public CreateAndStartRecommendedMatchCommand {
        participants = List.copyOf(Objects.requireNonNull(
                participants,
                "participants are required"
        ));
    }
}

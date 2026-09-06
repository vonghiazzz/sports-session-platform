package com.sportssession.platform.matchplan.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UpdateMatchPlanCommand(
        UUID matchPlanId,
        List<MatchPlanAssignment> participants
) {
    public UpdateMatchPlanCommand {
        Objects.requireNonNull(matchPlanId, "matchPlanId is required");
        participants = List.copyOf(Objects.requireNonNull(
                participants, "participants are required"
        ));
    }
}

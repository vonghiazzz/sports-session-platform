package com.sportssession.platform.matchplan.api;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchplan.application.MatchPlanAssignment;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MatchPlanParticipantRequest(
        @NotNull UUID sessionParticipantId,
        @NotNull TeamSide teamSide,
        @Min(1) @Max(2) int teamSlot
) {
    MatchPlanAssignment toAssignment() {
        return new MatchPlanAssignment(
                sessionParticipantId, teamSide, teamSlot
        );
    }
}

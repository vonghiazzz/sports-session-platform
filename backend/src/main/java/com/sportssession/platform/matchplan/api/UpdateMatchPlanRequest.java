package com.sportssession.platform.matchplan.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateMatchPlanRequest(
        @NotNull @Size(min = 4, max = 4)
        List<@Valid MatchPlanParticipantRequest> participants
) {
}

package com.sportssession.platform.matchplan.api;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoveMatchPlanRequest(
        @NotNull UUID targetSessionCourtId
) {
}

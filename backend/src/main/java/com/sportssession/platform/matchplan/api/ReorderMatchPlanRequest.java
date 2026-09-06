package com.sportssession.platform.matchplan.api;

import jakarta.validation.constraints.Min;

public record ReorderMatchPlanRequest(
        @Min(1) int targetPosition
) {
}

package com.sportssession.platform.player.api;

import com.sportssession.platform.player.domain.SkillLevel;
import jakarta.validation.constraints.NotNull;

public record UpdatePlayerSkillLevelRequest(
        @NotNull(message = "skillLevel is required")
        SkillLevel skillLevel
) {
}

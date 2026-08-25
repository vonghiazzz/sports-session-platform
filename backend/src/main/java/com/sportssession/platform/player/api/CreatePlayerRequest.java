package com.sportssession.platform.player.api;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.domain.SportCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePlayerRequest(
        @NotBlank(message = "displayName must not be blank")
        @Size(max = 120, message = "displayName must not exceed 120 characters")
        String displayName,

        @NotNull(message = "sport is required")
        SportCode sport,

        @NotNull(message = "skillLevel is required")
        SkillLevel skillLevel
) {
}


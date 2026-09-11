package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.UUID;

public record UpdatePlayerSkillLevelCommand(
        UUID playerId,
        SportCode sportCode,
        SkillLevel skillLevel
) {
}

package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.domain.SportCode;

public record CreatePlayerCommand(
        String displayName,
        SportCode sport,
        SkillLevel skillLevel
) {
}


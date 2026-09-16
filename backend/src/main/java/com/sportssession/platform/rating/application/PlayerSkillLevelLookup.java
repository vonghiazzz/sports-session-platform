package com.sportssession.platform.rating.application;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PlayerSkillLevelLookup {

    Map<UUID, SkillLevel> requireSkillLevels(
            Collection<UUID> playerIds,
            SportCode sportCode
    );
}

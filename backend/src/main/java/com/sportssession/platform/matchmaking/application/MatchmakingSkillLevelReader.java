package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MatchmakingSkillLevelReader {

    Map<UUID, SkillLevel> readSkillLevels(
            Collection<UUID> playerIds,
            SportCode sportCode
    );
}
package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.matchmaking.application.MatchmakingSkillLevelReader;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class MatchmakingSkillLevelReaderAdapter
        implements MatchmakingSkillLevelReader {

    private final PlayerSportProfileRepository profileRepository;

    public MatchmakingSkillLevelReaderAdapter(
            PlayerSportProfileRepository profileRepository
    ) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillLevel> readSkillLevels(
            Collection<UUID> playerIds,
            SportCode sportCode
    ) {
        if (playerIds == null) {
            throw new IllegalArgumentException("playerIds are required");
        }
        if (sportCode == null) {
            throw new IllegalArgumentException("sportCode is required");
        }

        Set<UUID> requestedIds = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            if (playerId == null) {
                throw new IllegalArgumentException("playerId is required");
            }
            requestedIds.add(playerId);
        }

        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, SkillLevel> result = new LinkedHashMap<>();
        profileRepository.findAllByPlayerIdInAndSportCode(
                requestedIds,
                sportCode
        ).forEach(profile -> result.put(
                profile.getPlayerId(),
                profile.getSkillLevel()
        ));

        return Map.copyOf(result);
    }
}

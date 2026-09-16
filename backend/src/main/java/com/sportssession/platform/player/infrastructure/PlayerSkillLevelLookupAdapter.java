package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.rating.application.MissingPlayerRatingPriorException;
import com.sportssession.platform.rating.application.PlayerSkillLevelLookup;
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
public class PlayerSkillLevelLookupAdapter implements PlayerSkillLevelLookup {

    private final PlayerSportProfileRepository profileRepository;

    public PlayerSkillLevelLookupAdapter(
            PlayerSportProfileRepository profileRepository
    ) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, SkillLevel> requireSkillLevels(
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

        Map<UUID, SkillLevel> skillLevels = new LinkedHashMap<>();
        profileRepository.findAllByPlayerIdInAndSportCode(
                requestedIds,
                sportCode
        ).forEach(profile -> skillLevels.put(
                profile.getPlayerId(),
                profile.getSkillLevel()
        ));

        Set<UUID> missingPlayerIds = new LinkedHashSet<>(requestedIds);
        missingPlayerIds.removeAll(skillLevels.keySet());
        if (!missingPlayerIds.isEmpty()) {
            throw new MissingPlayerRatingPriorException(
                    missingPlayerIds,
                    sportCode
            );
        }
        return Map.copyOf(skillLevels);
    }
}

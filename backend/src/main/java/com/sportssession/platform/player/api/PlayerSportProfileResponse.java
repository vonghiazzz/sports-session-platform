package com.sportssession.platform.player.api;

import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.player.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record PlayerSportProfileResponse(
        UUID id,
        SportCode sport,
        SkillLevel skillLevel,
        Instant createdAt,
        Instant updatedAt
) {
    static PlayerSportProfileResponse from(PlayerSportProfile profile) {
        return new PlayerSportProfileResponse(
                profile.id(),
                profile.sportCode(),
                profile.skillLevel(),
                profile.createdAt(),
                profile.updatedAt());
    }
}


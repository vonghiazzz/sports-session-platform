package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.PlayerSportProfileResult;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record PlayerSportProfileResponse(
        UUID id,
        SportCode sport,
        SkillLevel skillLevel,
        PlayerRatingResponse rating,
        Instant createdAt,
        Instant updatedAt
) {
    static PlayerSportProfileResponse from(PlayerSportProfileResult result) {
        var profile = result.profile();
        return new PlayerSportProfileResponse(
                profile.id(),
                profile.sportCode(),
                profile.skillLevel(),
                PlayerRatingResponse.from(result.rating()),
                profile.createdAt(),
                profile.updatedAt());
    }
}

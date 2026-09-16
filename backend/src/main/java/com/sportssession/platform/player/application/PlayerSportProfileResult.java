package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.PlayerSportProfile;

import java.util.Objects;

public record PlayerSportProfileResult(
        PlayerSportProfile profile,
        PlayerManagementRatingSnapshot rating
) {
    public PlayerSportProfileResult {
        Objects.requireNonNull(profile, "profile is required");
        Objects.requireNonNull(rating, "rating is required");
        if (!profile.playerId().equals(rating.playerId())
                || profile.sportCode() != rating.sportCode()) {
            throw new IllegalArgumentException(
                    "profile and Rating identities must match"
            );
        }
    }
}

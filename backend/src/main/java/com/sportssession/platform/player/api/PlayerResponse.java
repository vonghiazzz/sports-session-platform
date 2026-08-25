package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.PlayerResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlayerResponse(
        UUID id,
        String displayName,
        List<PlayerSportProfileResponse> sportProfiles,
        Instant createdAt,
        Instant updatedAt
) {
    static PlayerResponse from(PlayerResult result) {
        return new PlayerResponse(
                result.player().id(),
                result.player().displayName(),
                result.sportProfiles().stream()
                        .map(PlayerSportProfileResponse::from)
                        .toList(),
                result.player().createdAt(),
                result.player().updatedAt());
    }
}


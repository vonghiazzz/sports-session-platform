package com.sportssession.platform.player.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PlayerManagementRatingHistoryResult(
        UUID playerId,
        SportCode sportCode,
        MatchFormat matchFormat,
        List<PlayerManagementRatingHistoryItem> events
) {
    public PlayerManagementRatingHistoryResult {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        events = List.copyOf(events);
    }
}

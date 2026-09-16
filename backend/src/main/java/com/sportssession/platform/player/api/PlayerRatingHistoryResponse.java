package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.PlayerManagementRatingHistoryResult;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.List;
import java.util.UUID;

public record PlayerRatingHistoryResponse(
        UUID playerId,
        SportCode sport,
        MatchFormat matchFormat,
        List<PlayerRatingHistoryEventResponse> events
) {
    static PlayerRatingHistoryResponse from(
            PlayerManagementRatingHistoryResult result
    ) {
        return new PlayerRatingHistoryResponse(
                result.playerId(),
                result.sportCode(),
                result.matchFormat(),
                result.events().stream()
                        .map(PlayerRatingHistoryEventResponse::from)
                        .toList()
        );
    }
}

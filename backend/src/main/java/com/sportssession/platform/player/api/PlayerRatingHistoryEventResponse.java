package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.PlayerManagementRatingHistoryItem;
import com.sportssession.platform.player.application.PlayerManagementRatingOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlayerRatingHistoryEventResponse(
        UUID matchId,
        Instant matchCompletedAt,
        PlayerManagementRatingOutcome outcome,
        BigDecimal beforeRatingValue,
        BigDecimal beforeUncertainty,
        BigDecimal afterRatingValue,
        BigDecimal afterUncertainty,
        int resultVersion,
        String algorithmVersion,
        Instant createdAt
) {
    static PlayerRatingHistoryEventResponse from(
            PlayerManagementRatingHistoryItem item
    ) {
        return new PlayerRatingHistoryEventResponse(
                item.matchId(),
                item.matchCompletedAt(),
                item.outcome(),
                item.beforeRatingValue(),
                item.beforeUncertainty(),
                item.afterRatingValue(),
                item.afterUncertainty(),
                item.resultVersion(),
                item.algorithmVersion(),
                item.createdAt()
        );
    }
}

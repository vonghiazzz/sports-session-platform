package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.PlayerManagementRatingBasis;
import com.sportssession.platform.player.application.PlayerManagementRatingSnapshot;

import java.math.BigDecimal;

public record PlayerRatingResponse(
        BigDecimal ratingValue,
        BigDecimal uncertainty,
        int ratedMatches,
        PlayerManagementRatingBasis ratingBasis,
        String ratingAlgorithmVersion
) {
    static PlayerRatingResponse from(PlayerManagementRatingSnapshot rating) {
        return new PlayerRatingResponse(
                rating.ratingValue(),
                rating.uncertainty(),
                rating.ratedMatches(),
                rating.ratingBasis(),
                rating.ratingAlgorithmVersion()
        );
    }
}

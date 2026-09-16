package com.sportssession.platform.rating.domain;

import com.sportssession.platform.player.domain.SkillLevel;

import java.util.Objects;

public final class RatingInitializer {

    private RatingInitializer() {
    }

    public static RatingState initialize(SkillLevel skillLevel) {
        Objects.requireNonNull(skillLevel, "skillLevel is required");

        double mu = switch (skillLevel) {
            case WEAK -> 15.0;
            case WEAK_PLUS -> 19.0;
            case INTERMEDIATE_MINUS -> 23.0;
            case INTERMEDIATE -> 27.0;
            case INTERMEDIATE_PLUS -> 31.0;
            case GOOD -> 35.0;
        };
        return new RatingState(
                mu,
                WengLinPlackettLuceRatingEngine.INITIAL_SIGMA
        );
    }
}

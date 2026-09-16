package com.sportssession.platform.rating.domain;

import java.util.Objects;

public record RatingUpdate(RatingState before, RatingState after) {

    public RatingUpdate {
        Objects.requireNonNull(before, "before is required");
        Objects.requireNonNull(after, "after is required");
    }
}

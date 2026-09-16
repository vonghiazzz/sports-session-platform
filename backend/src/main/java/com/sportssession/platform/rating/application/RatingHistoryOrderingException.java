package com.sportssession.platform.rating.application;

import java.util.UUID;

public class RatingHistoryOrderingException extends RuntimeException {

    public RatingHistoryOrderingException(UUID matchId) {
        super("A canonically later Match is already applied for Rating context: "
                + matchId);
    }
}

package com.sportssession.platform.rating.application;

import java.util.UUID;

public class RatingEvidenceUnavailableException extends RuntimeException {

    public RatingEvidenceUnavailableException(UUID matchId) {
        super("Completed Match Rating evidence is unavailable: " + matchId);
    }
}

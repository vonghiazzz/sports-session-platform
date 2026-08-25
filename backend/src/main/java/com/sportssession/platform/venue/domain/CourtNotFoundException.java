package com.sportssession.platform.venue.domain;

import java.util.UUID;

public class CourtNotFoundException extends RuntimeException {

    public CourtNotFoundException(UUID courtId) {
        super("Court not found: " + courtId);
    }
}

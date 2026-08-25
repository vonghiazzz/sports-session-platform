package com.sportssession.platform.venue.domain;

import java.util.UUID;

public class DuplicateCourtNameException extends RuntimeException {

    public DuplicateCourtNameException(UUID venueId, String courtName) {
        super("Court name already exists in Venue " + venueId + ": " + courtName);
    }

    public DuplicateCourtNameException(UUID venueId, String courtName, Throwable cause) {
        super("Court name already exists in Venue " + venueId + ": " + courtName, cause);
    }
}

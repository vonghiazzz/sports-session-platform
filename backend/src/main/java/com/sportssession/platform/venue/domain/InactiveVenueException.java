package com.sportssession.platform.venue.domain;

import java.util.UUID;

public class InactiveVenueException extends RuntimeException {

    public InactiveVenueException(UUID venueId) {
        super("Cannot create a court for inactive Venue: " + venueId);
    }
}

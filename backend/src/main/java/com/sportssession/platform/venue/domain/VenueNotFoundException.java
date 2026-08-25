package com.sportssession.platform.venue.domain;

import java.util.UUID;

public class VenueNotFoundException extends RuntimeException {

    public VenueNotFoundException(UUID venueId) {
        super("Venue not found: " + venueId);
    }
}

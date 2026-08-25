package com.sportssession.platform.venue.application;

public record CreateVenueCommand(
        String name,
        String locationText,
        boolean active
) {
}

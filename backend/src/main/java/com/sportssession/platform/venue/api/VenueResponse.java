package com.sportssession.platform.venue.api;

import com.sportssession.platform.venue.domain.Venue;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String locationText,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static VenueResponse from(Venue venue) {
        return new VenueResponse(
                venue.id(),
                venue.name(),
                venue.locationText(),
                venue.active(),
                venue.createdAt(),
                venue.updatedAt());
    }
}

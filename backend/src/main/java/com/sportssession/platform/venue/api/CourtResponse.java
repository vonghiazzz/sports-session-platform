package com.sportssession.platform.venue.api;

import com.sportssession.platform.venue.domain.Court;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record CourtResponse(
        UUID id,
        UUID venueId,
        String name,
        SportCode sport,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    static CourtResponse from(Court court) {
        return new CourtResponse(
                court.id(),
                court.venueId(),
                court.name(),
                court.sportCode(),
                court.active(),
                court.createdAt(),
                court.updatedAt());
    }
}

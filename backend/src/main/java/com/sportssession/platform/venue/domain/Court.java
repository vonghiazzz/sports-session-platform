package com.sportssession.platform.venue.domain;

import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Court(
        UUID id,
        UUID venueId,
        String name,
        SportCode sportCode,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public Court {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(venueId, "venueId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("name must not exceed 120 characters");
        }
    }

    public static Court create(
            UUID venueId,
            String name,
            SportCode sportCode,
            boolean active,
            Instant now
    ) {
        return new Court(UUID.randomUUID(), venueId, name, sportCode, active, now, now);
    }
}

package com.sportssession.platform.venue.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Venue(
        UUID id,
        String name,
        String locationText,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public Venue {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        name = name.strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 120) {
            throw new IllegalArgumentException("name must not exceed 120 characters");
        }

        if (locationText != null) {
            locationText = locationText.strip();
            if (locationText.isEmpty()) {
                locationText = null;
            } else if (locationText.length() > 500) {
                throw new IllegalArgumentException(
                        "locationText must not exceed 500 characters");
            }
        }
    }

    public static Venue create(
            String name,
            String locationText,
            boolean active,
            Instant now
    ) {
        return new Venue(UUID.randomUUID(), name, locationText, active, now, now);
    }
}

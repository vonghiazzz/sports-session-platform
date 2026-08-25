package com.sportssession.platform.player.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Player(
        UUID id,
        String displayName,
        Instant createdAt,
        Instant updatedAt
) {
    public Player {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(displayName, "displayName is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        displayName = displayName.strip();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayName.length() > 120) {
            throw new IllegalArgumentException("displayName must not exceed 120 characters");
        }
    }

    public static Player create(String displayName, Instant now) {
        return new Player(UUID.randomUUID(), displayName, now, now);
    }
}


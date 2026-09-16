package com.sportssession.platform.player.domain;

import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlayerSportProfile(
        UUID id,
        UUID playerId,
        SportCode sportCode,
        SkillLevel skillLevel,
        Instant createdAt,
        Instant updatedAt
) {
    public PlayerSportProfile {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(skillLevel, "skillLevel is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static PlayerSportProfile create(
            UUID playerId,
            SportCode sportCode,
            SkillLevel skillLevel,
            Instant now
    ) {
        return new PlayerSportProfile(
                UUID.randomUUID(), playerId, sportCode, skillLevel, now, now);
    }

    public PlayerSportProfile changeSkillLevel(
            SkillLevel newSkillLevel,
            Instant now
    ) {
        Objects.requireNonNull(newSkillLevel, "skillLevel is required");
        Objects.requireNonNull(now, "now is required");
        if (now.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "updatedAt must not be before createdAt"
            );
        }
        return new PlayerSportProfile(
                id,
                playerId,
                sportCode,
                newSkillLevel,
                createdAt,
                now
        );
    }
}

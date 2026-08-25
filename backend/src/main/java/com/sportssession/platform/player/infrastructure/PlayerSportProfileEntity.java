package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_sport_profiles")
public class PlayerSportProfileEntity {

    @Id
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_code", nullable = false, length = 32)
    private SportCode sportCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill_level", nullable = false, length = 32)
    private SkillLevel skillLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerSportProfileEntity() {
    }

    private PlayerSportProfileEntity(
            UUID id,
            UUID playerId,
            SportCode sportCode,
            SkillLevel skillLevel,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.playerId = playerId;
        this.sportCode = sportCode;
        this.skillLevel = skillLevel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlayerSportProfileEntity from(PlayerSportProfile profile) {
        return new PlayerSportProfileEntity(
                profile.id(),
                profile.playerId(),
                profile.sportCode(),
                profile.skillLevel(),
                profile.createdAt(),
                profile.updatedAt());
    }

    public PlayerSportProfile toDomain() {
        return new PlayerSportProfile(
                id, playerId, sportCode, skillLevel, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SportCode getSportCode() {
        return sportCode;
    }

    public SkillLevel getSkillLevel() {
        return skillLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

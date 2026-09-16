package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.player.domain.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "players")
public class PlayerEntity {

    @Id
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "player_code", nullable = false, insertable = false, updatable = false)
    private long playerCode;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerEntity() {
    }

    private PlayerEntity(UUID id, String displayName, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PlayerEntity from(Player player) {
        return new PlayerEntity(
                player.id(), player.displayName(), player.createdAt(), player.updatedAt());
    }

    public Player toDomain() {
        return new Player(id, playerCode, displayName, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getPlayerCode() {
        return playerCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

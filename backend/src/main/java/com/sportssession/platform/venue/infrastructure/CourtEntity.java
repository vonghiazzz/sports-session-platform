package com.sportssession.platform.venue.infrastructure;

import com.sportssession.platform.venue.domain.Court;
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
@Table(name = "courts")
public class CourtEntity {

    @Id
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_code", nullable = false, length = 32)
    private SportCode sportCode;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CourtEntity() {
    }

    private CourtEntity(
            UUID id,
            UUID venueId,
            String name,
            SportCode sportCode,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.venueId = venueId;
        this.name = name;
        this.sportCode = sportCode;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CourtEntity from(Court court) {
        return new CourtEntity(
                court.id(),
                court.venueId(),
                court.name(),
                court.sportCode(),
                court.active(),
                court.createdAt(),
                court.updatedAt());
    }

    public Court toDomain() {
        return new Court(id, venueId, name, sportCode, active, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getName() {
        return name;
    }

    public SportCode getSportCode() {
        return sportCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

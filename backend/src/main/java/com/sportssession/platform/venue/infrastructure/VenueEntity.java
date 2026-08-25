package com.sportssession.platform.venue.infrastructure;

import com.sportssession.platform.venue.domain.Venue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "venues")
public class VenueEntity {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "location_text", length = 500)
    private String locationText;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VenueEntity() {
    }

    private VenueEntity(
            UUID id,
            String name,
            String locationText,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.locationText = locationText;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static VenueEntity from(Venue venue) {
        return new VenueEntity(
                venue.id(),
                venue.name(),
                venue.locationText(),
                venue.active(),
                venue.createdAt(),
                venue.updatedAt());
    }

    public Venue toDomain() {
        return new Venue(id, name, locationText, active, createdAt, updatedAt);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocationText() {
        return locationText;
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

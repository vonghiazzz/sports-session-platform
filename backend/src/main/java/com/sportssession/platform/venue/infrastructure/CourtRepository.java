package com.sportssession.platform.venue.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<CourtEntity, UUID> {

    boolean existsByVenueIdAndName(UUID venueId, String name);

    List<CourtEntity> findAllByVenueIdOrderByCreatedAtAscIdAsc(UUID venueId);
}

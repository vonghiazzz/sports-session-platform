package com.sportssession.platform.venue.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<VenueEntity, UUID> {

    List<VenueEntity> findAllByOrderByCreatedAtAscIdAsc();
}

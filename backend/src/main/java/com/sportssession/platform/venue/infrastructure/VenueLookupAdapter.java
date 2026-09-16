package com.sportssession.platform.venue.infrastructure;

import com.sportssession.platform.venue.application.VenueLookup;
import com.sportssession.platform.venue.application.VenueSnapshot;
import com.sportssession.platform.venue.domain.VenueNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class VenueLookupAdapter implements VenueLookup {

    private final VenueRepository venueRepository;

    public VenueLookupAdapter(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Override
    public VenueSnapshot requireVenue(UUID venueId) {
        VenueEntity entity = venueRepository.findById(venueId)
                .orElseThrow(() -> new VenueNotFoundException(venueId));

        return new VenueSnapshot(
                entity.getId(),
                entity.isActive()
        );
    }
}
package com.sportssession.platform.venue.infrastructure;

import com.sportssession.platform.venue.application.CourtLookup;
import com.sportssession.platform.venue.application.CourtSnapshot;
import com.sportssession.platform.venue.domain.CourtNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CourtLookupAdapter implements CourtLookup {

    private final CourtRepository courtRepository;

    public CourtLookupAdapter(CourtRepository courtRepository) {
        this.courtRepository = courtRepository;
    }

    @Override
    public CourtSnapshot requireCourt(UUID courtId) {
        CourtEntity entity = courtRepository.findById(courtId)
                .orElseThrow(() -> new CourtNotFoundException(courtId));

        return new CourtSnapshot(
                entity.getId(),
                entity.getVenueId(),
                entity.getSportCode(),
                entity.isActive()
        );
    }
}
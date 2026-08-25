package com.sportssession.platform.venue.application;

import com.sportssession.platform.venue.domain.Court;
import com.sportssession.platform.venue.domain.CourtNotFoundException;
import com.sportssession.platform.venue.domain.DuplicateCourtNameException;
import com.sportssession.platform.venue.domain.InactiveVenueException;
import com.sportssession.platform.venue.domain.Venue;
import com.sportssession.platform.venue.domain.VenueNotFoundException;
import com.sportssession.platform.venue.infrastructure.CourtEntity;
import com.sportssession.platform.venue.infrastructure.CourtRepository;
import com.sportssession.platform.venue.infrastructure.VenueEntity;
import com.sportssession.platform.venue.infrastructure.VenueRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class VenueService {

    private static final String COURT_NAME_UNIQUE_CONSTRAINT = "uk_courts_venue_name";

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;

    public VenueService(VenueRepository venueRepository, CourtRepository courtRepository) {
        this.venueRepository = venueRepository;
        this.courtRepository = courtRepository;
    }

    @Transactional
    public Venue createVenue(CreateVenueCommand command) {
        Venue venue = Venue.create(
                command.name(), command.locationText(), command.active(), Instant.now());
        venueRepository.saveAndFlush(VenueEntity.from(venue));
        return venue;
    }

    @Transactional(readOnly = true)
    public Venue getVenue(UUID venueId) {
        return findVenue(venueId);
    }

    @Transactional(readOnly = true)
    public List<Venue> listVenues() {
        return venueRepository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(VenueEntity::toDomain)
                .toList();
    }

    @Transactional
    public Court createCourt(CreateCourtCommand command) {
        Venue venue = findVenue(command.venueId());
        if (!venue.active()) {
            throw new InactiveVenueException(venue.id());
        }

        Court court = Court.create(
                venue.id(), command.name(), command.sport(), command.active(), Instant.now());
        if (courtRepository.existsByVenueIdAndName(venue.id(), court.name())) {
            throw new DuplicateCourtNameException(venue.id(), court.name());
        }

        try {
            courtRepository.saveAndFlush(CourtEntity.from(court));
        } catch (DataIntegrityViolationException exception) {
            if (violatesCourtNameUniqueConstraint(exception)) {
                throw new DuplicateCourtNameException(venue.id(), court.name(), exception);
            }
            throw exception;
        }
        return court;
    }

    @Transactional(readOnly = true)
    public Court getCourt(UUID courtId) {
        return courtRepository.findById(courtId)
                .map(CourtEntity::toDomain)
                .orElseThrow(() -> new CourtNotFoundException(courtId));
    }

    @Transactional(readOnly = true)
    public List<Court> listCourts(UUID venueId) {
        findVenue(venueId);
        return courtRepository.findAllByVenueIdOrderByCreatedAtAscIdAsc(venueId).stream()
                .map(CourtEntity::toDomain)
                .toList();
    }

    private Venue findVenue(UUID venueId) {
        return venueRepository.findById(venueId)
                .map(VenueEntity::toDomain)
                .orElseThrow(() -> new VenueNotFoundException(venueId));
    }

    private boolean violatesCourtNameUniqueConstraint(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && COURT_NAME_UNIQUE_CONSTRAINT.equals(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

package com.sportssession.platform.venue.api;

import com.sportssession.platform.venue.application.CreateCourtCommand;
import com.sportssession.platform.venue.application.CreateVenueCommand;
import com.sportssession.platform.venue.application.VenueService;
import com.sportssession.platform.venue.domain.Court;
import com.sportssession.platform.venue.domain.Venue;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> createVenue(
            @Valid @RequestBody CreateVenueRequest request
    ) {
        Venue created = venueService.createVenue(new CreateVenueCommand(
                request.name(), request.locationText(), request.activeOrDefault()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{venueId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(VenueResponse.from(created));
    }

    @GetMapping("/{venueId}")
    public VenueResponse getVenue(@PathVariable UUID venueId) {
        return VenueResponse.from(venueService.getVenue(venueId));
    }

    @GetMapping
    public List<VenueResponse> listVenues() {
        return venueService.listVenues().stream()
                .map(VenueResponse::from)
                .toList();
    }

    @PostMapping("/{venueId}/courts")
    public ResponseEntity<CourtResponse> createCourt(
            @PathVariable UUID venueId,
            @Valid @RequestBody CreateCourtRequest request
    ) {
        Court created = venueService.createCourt(new CreateCourtCommand(
                venueId, request.name(), request.sport(), request.activeOrDefault()));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/courts/{courtId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(CourtResponse.from(created));
    }

    @GetMapping("/{venueId}/courts")
    public List<CourtResponse> listCourts(@PathVariable UUID venueId) {
        return venueService.listCourts(venueId).stream()
                .map(CourtResponse::from)
                .toList();
    }
}

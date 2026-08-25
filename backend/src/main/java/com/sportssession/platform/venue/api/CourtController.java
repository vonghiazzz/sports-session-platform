package com.sportssession.platform.venue.api;

import com.sportssession.platform.venue.application.VenueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/courts")
public class CourtController {

    private final VenueService venueService;

    public CourtController(VenueService venueService) {
        this.venueService = venueService;
    }

    @GetMapping("/{courtId}")
    public CourtResponse getCourt(@PathVariable UUID courtId) {
        return CourtResponse.from(venueService.getCourt(courtId));
    }
}

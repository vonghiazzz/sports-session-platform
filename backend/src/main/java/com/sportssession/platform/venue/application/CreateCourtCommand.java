package com.sportssession.platform.venue.application;

import com.sportssession.platform.shared.domain.SportCode;

import java.util.UUID;

public record CreateCourtCommand(
        UUID venueId,
        String name,
        SportCode sport,
        boolean active
) {
}

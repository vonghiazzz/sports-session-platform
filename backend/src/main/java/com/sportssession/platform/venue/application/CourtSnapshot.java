package com.sportssession.platform.venue.application;

import com.sportssession.platform.shared.domain.SportCode;

import java.util.UUID;

public record CourtSnapshot(
        UUID id,
        UUID venueId,
        SportCode sportCode,
        boolean active
) {
}
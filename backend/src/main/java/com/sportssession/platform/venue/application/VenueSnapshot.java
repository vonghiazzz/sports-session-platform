package com.sportssession.platform.venue.application;

import java.util.UUID;

public record VenueSnapshot(
        UUID id,
        boolean active
) {
}
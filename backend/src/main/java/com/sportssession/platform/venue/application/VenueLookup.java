package com.sportssession.platform.venue.application;

import java.util.UUID;

public interface VenueLookup {

    VenueSnapshot requireVenue(UUID venueId);
}
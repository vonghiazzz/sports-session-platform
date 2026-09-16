package com.sportssession.platform.venue.application;

import java.util.UUID;

public interface CourtLookup {

    CourtSnapshot requireCourt(UUID courtId);
}
package com.sportssession.platform.session.application;

import com.sportssession.platform.session.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record CreateSessionCommand(
        UUID venueId,
        String title,
        SportCode sport,
        MatchFormat matchFormat,
        Instant plannedStartAt,
        Instant plannedEndAt
) {
}

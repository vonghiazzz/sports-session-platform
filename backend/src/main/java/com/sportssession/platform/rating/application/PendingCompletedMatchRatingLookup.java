package com.sportssession.platform.rating.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.Optional;
import java.util.UUID;

public interface PendingCompletedMatchRatingLookup {

    Optional<UUID> findEarliestUnresolved(
            SportCode sportCode,
            MatchFormat matchFormat
    );
}

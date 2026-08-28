package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MatchmakingRatingReader {

    Map<UUID, MatchmakingRatingSnapshot> readEffectiveRatings(
            Collection<UUID> playerIds,
            SportCode sportCode,
            MatchFormat matchFormat
    );
}

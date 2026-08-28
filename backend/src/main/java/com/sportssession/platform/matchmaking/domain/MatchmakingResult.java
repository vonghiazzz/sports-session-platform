package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public sealed interface MatchmakingResult
        permits MatchRecommendation, MatchmakingUnavailable {

    String algorithmVersion();

    Instant evaluationTime();

    UUID sessionId();

    UUID sessionCourtId();

    SportCode sportCode();

    MatchFormat matchFormat();

    int eligiblePlayerCount();
}

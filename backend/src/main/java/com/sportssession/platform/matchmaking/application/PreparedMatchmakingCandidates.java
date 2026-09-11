package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PreparedMatchmakingCandidates(
        UUID sessionId,
        SportCode sportCode,
        MatchFormat matchFormat,
        Instant evaluationTime,
        List<MatchmakingCandidate> candidates
) {
    public PreparedMatchmakingCandidates {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        Objects.requireNonNull(candidates, "candidates are required");
        candidates = List.copyOf(candidates);
    }
}

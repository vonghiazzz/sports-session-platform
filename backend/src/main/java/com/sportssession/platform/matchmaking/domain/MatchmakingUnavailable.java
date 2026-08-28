package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MatchmakingUnavailable(
        String algorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        UUID sessionCourtId,
        SportCode sportCode,
        MatchFormat matchFormat,
        int eligiblePlayerCount,
        MatchmakingUnavailableReason reason
) implements MatchmakingResult {
    public MatchmakingUnavailable {
        Objects.requireNonNull(algorithmVersion, "algorithmVersion is required");
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(reason, "reason is required");
        if (eligiblePlayerCount < 0) {
            throw new IllegalArgumentException(
                    "eligiblePlayerCount must not be negative");
        }
    }
}

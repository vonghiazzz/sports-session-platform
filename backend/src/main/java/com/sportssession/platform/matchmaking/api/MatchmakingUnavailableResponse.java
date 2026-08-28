package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailable;
import com.sportssession.platform.matchmaking.domain.MatchmakingUnavailableReason;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.UUID;

public record MatchmakingUnavailableResponse(
        MatchmakingGenerationOutcome outcome,
        String algorithmVersion,
        Instant evaluationTime,
        UUID sessionId,
        UUID sessionCourtId,
        SportCode sportCode,
        MatchFormat matchFormat,
        int eligiblePlayerCount,
        MatchmakingUnavailableReason reason
) implements MatchmakingGenerationResponse {
    static MatchmakingUnavailableResponse from(
            MatchmakingUnavailable unavailable
    ) {
        return new MatchmakingUnavailableResponse(
                MatchmakingGenerationOutcome.UNAVAILABLE,
                unavailable.algorithmVersion(),
                unavailable.evaluationTime(),
                unavailable.sessionId(),
                unavailable.sessionCourtId(),
                unavailable.sportCode(),
                unavailable.matchFormat(),
                unavailable.eligiblePlayerCount(),
                unavailable.reason()
        );
    }
}

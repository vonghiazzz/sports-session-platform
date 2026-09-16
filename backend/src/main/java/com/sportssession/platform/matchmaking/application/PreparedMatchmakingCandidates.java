package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;
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
        List<MatchmakingCandidate> candidates,
        MatchmakingSessionPairingHistory pairingHistory
) {
    public PreparedMatchmakingCandidates {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        Objects.requireNonNull(candidates, "candidates are required");
        Objects.requireNonNull(pairingHistory, "pairingHistory is required");
        if (!pairingHistory.sessionId().equals(sessionId)) {
            throw new IllegalArgumentException(
                    "pairingHistory must belong to sessionId"
            );
        }
        candidates = List.copyOf(candidates);
    }
}

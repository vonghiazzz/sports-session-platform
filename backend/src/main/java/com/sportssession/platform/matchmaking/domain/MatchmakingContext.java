package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MatchmakingContext(
        UUID sessionId,
        UUID sessionCourtId,
        SportCode sportCode,
        MatchFormat matchFormat,
        Instant evaluationTime,
        List<MatchmakingCandidate> candidates
) {
    public MatchmakingContext {
        require(sessionId != null, "sessionId is required");
        require(sessionCourtId != null, "sessionCourtId is required");
        require(sportCode != null, "sportCode is required");
        require(matchFormat != null, "matchFormat is required");
        require(evaluationTime != null, "evaluationTime is required");
        require(candidates != null, "candidates are required");
        require(sportCode == SportCode.BADMINTON,
                "Matchmaking V1 supports only BADMINTON");
        require(matchFormat == MatchFormat.DOUBLES,
                "Matchmaking V1 supports only DOUBLES");

        List<MatchmakingCandidate> defensiveCandidates =
                new ArrayList<>(candidates.size());
        Set<UUID> participantIds = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        for (MatchmakingCandidate candidate : candidates) {
            require(candidate != null, "candidate is required");
            defensiveCandidates.add(candidate);
            require(!candidate.waitingSince().isAfter(evaluationTime),
                    "waitingSince must not be after evaluationTime");
            require(participantIds.add(candidate.sessionParticipantId()),
                    "sessionParticipantId must be unique");
            require(playerIds.add(candidate.playerId()),
                    "playerId must be unique");
        }
        candidates = List.copyOf(defensiveCandidates);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidMatchmakingInputException(message);
        }
    }
}

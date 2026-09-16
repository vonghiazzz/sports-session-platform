package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchmakingSessionEvidence(
        UUID sessionId,
        SportCode sportCode,
        MatchFormat matchFormat,
        SessionStatus sessionStatus,
        List<MatchmakingSessionParticipantSnapshot> participants
) {
    public MatchmakingSessionEvidence {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(sessionStatus, "sessionStatus is required");
        Objects.requireNonNull(participants, "participants are required");
        participants = List.copyOf(participants);
    }
}

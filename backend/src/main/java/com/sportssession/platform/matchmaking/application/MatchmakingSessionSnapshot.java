package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MatchmakingSessionSnapshot(
        UUID sessionId,
        SportCode sportCode,
        MatchFormat matchFormat,
        SessionStatus sessionStatus,
        UUID sessionCourtId,
        SessionCourtStatus sessionCourtStatus,
        List<MatchmakingSessionParticipantSnapshot> participants
) {
    public MatchmakingSessionSnapshot {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        Objects.requireNonNull(sessionStatus, "sessionStatus is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(
                sessionCourtStatus,
                "sessionCourtStatus is required"
        );
        Objects.requireNonNull(participants, "participants are required");
        participants = List.copyOf(participants);
    }
}

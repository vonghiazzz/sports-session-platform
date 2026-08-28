package com.sportssession.platform.matchmaking.application;

import java.util.Objects;
import java.util.UUID;

public class MatchmakingSessionSnapshotException extends RuntimeException {

    private final MatchmakingSessionSnapshotFailureReason reason;
    private final UUID sessionId;
    private final UUID sessionCourtId;

    public MatchmakingSessionSnapshotException(
            MatchmakingSessionSnapshotFailureReason reason,
            UUID sessionId,
            UUID sessionCourtId
    ) {
        super(message(reason, sessionId, sessionCourtId));
        this.reason = Objects.requireNonNull(reason, "reason is required");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId is required");
        this.sessionCourtId = sessionCourtId;
    }

    public MatchmakingSessionSnapshotFailureReason reason() {
        return reason;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID sessionCourtId() {
        return sessionCourtId;
    }

    private static String message(
            MatchmakingSessionSnapshotFailureReason reason,
            UUID sessionId,
            UUID sessionCourtId
    ) {
        return switch (Objects.requireNonNull(reason, "reason is required")) {
            case SESSION_NOT_FOUND -> "Matchmaking Session not found: " + sessionId;
            case SESSION_COURT_NOT_FOUND_FOR_SESSION ->
                    "Matchmaking SessionCourt " + sessionCourtId
                            + " not found for Session " + sessionId;
        };
    }
}

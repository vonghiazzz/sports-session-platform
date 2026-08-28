package com.sportssession.platform.matchmaking.application;

import java.util.UUID;

public interface MatchmakingSessionSnapshotReader {

    MatchmakingSessionSnapshot load(UUID sessionId, UUID sessionCourtId);
}

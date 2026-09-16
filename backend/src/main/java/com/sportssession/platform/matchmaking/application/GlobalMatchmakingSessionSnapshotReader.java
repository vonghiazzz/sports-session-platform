package com.sportssession.platform.matchmaking.application;

import java.util.UUID;

public interface GlobalMatchmakingSessionSnapshotReader {

    GlobalMatchmakingSessionSnapshot load(UUID sessionId);
}

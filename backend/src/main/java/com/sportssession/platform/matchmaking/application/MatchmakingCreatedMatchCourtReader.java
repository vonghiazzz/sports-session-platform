package com.sportssession.platform.matchmaking.application;

import java.util.Set;
import java.util.UUID;

public interface MatchmakingCreatedMatchCourtReader {

    Set<UUID> createdMatchCourtIds(UUID sessionId);
}

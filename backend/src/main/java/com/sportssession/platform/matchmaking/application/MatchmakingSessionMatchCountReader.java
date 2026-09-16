package com.sportssession.platform.matchmaking.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface MatchmakingSessionMatchCountReader {

    Map<UUID, Integer> readCompletedMatchCounts(
            UUID sessionId,
            Collection<UUID> sessionParticipantIds
    );
}

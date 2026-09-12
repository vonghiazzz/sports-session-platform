package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;

import java.util.UUID;

public interface MatchmakingSessionPairingHistoryReader {

    MatchmakingSessionPairingHistory readCompletedPairingHistory(
            UUID sessionId
    );
}

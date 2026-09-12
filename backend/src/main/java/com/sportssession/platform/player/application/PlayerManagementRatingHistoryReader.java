package com.sportssession.platform.player.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.List;
import java.util.UUID;

public interface PlayerManagementRatingHistoryReader {

    List<PlayerManagementRatingHistoryItem> readHistory(
            UUID playerId,
            SportCode sportCode,
            MatchFormat matchFormat
    );
}

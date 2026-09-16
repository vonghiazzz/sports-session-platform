package com.sportssession.platform.rating.domain;

import java.util.List;

public interface RatingEngine {

    /**
     * Returns updates in the stable order A1, A2, B1, B2.
     */
    List<RatingUpdate> rate(
            List<RatingState> teamA,
            List<RatingState> teamB,
            WinningTeam winner
    );

    String algorithmVersion();
}

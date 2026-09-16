package com.sportssession.platform.matchmaking.api;

import com.sportssession.platform.matchmaking.domain.RecommendedTeam;

public record MatchmakingTeamResponse(
        MatchmakingPlayerResponse slot1,
        MatchmakingPlayerResponse slot2
) {
    static MatchmakingTeamResponse from(RecommendedTeam team) {
        return new MatchmakingTeamResponse(
                MatchmakingPlayerResponse.from(team.slot1()),
                MatchmakingPlayerResponse.from(team.slot2())
        );
    }
}

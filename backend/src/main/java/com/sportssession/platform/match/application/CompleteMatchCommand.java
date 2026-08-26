package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record CompleteMatchCommand(
        UUID matchId,
        TeamSide winnerTeam,
        Integer teamAScore,
        Integer teamBScore
) {
}

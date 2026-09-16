package com.sportssession.platform.match.api;

import com.sportssession.platform.match.domain.TeamSide;
import jakarta.validation.constraints.NotNull;

public record CompleteMatchRequest(
        @NotNull(message = "winnerTeam is required")
        TeamSide winnerTeam,
        Integer teamAScore,
        Integer teamBScore
) {
}

package com.sportssession.platform.match.domain;

public record MatchResult(
        TeamSide winnerTeam,
        Integer teamAScore,
        Integer teamBScore
) {
    public MatchResult {
        if (winnerTeam == null) {
            throw new InvalidMatchResultException("winnerTeam is required");
        }

        if ((teamAScore == null) != (teamBScore == null)) {
            throw new InvalidMatchResultException(
                    "Both team scores must be supplied together"
            );
        }

        if (teamAScore != null) {
            if (teamAScore < 0 || teamBScore < 0) {
                throw new InvalidMatchResultException(
                        "Scores must not be negative"
                );
            }
            if (teamAScore.equals(teamBScore)) {
                throw new InvalidMatchResultException(
                        "Match scores must not be tied"
                );
            }
            if (winnerTeam == TeamSide.A && teamAScore <= teamBScore) {
                throw new InvalidMatchResultException(
                        "Team A score must exceed Team B score when Team A wins"
                );
            }
            if (winnerTeam == TeamSide.B && teamBScore <= teamAScore) {
                throw new InvalidMatchResultException(
                        "Team B score must exceed Team A score when Team B wins"
                );
            }
        }
    }

    public static MatchResult winnerOnly(TeamSide winnerTeam) {
        return new MatchResult(winnerTeam, null, null);
    }

    public static MatchResult withScore(
            TeamSide winnerTeam,
            int teamAScore,
            int teamBScore
    ) {
        return new MatchResult(winnerTeam, teamAScore, teamBScore);
    }
}

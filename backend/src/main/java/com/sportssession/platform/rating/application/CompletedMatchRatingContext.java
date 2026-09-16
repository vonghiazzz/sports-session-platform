package com.sportssession.platform.rating.application;

import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record CompletedMatchRatingContext(
        UUID matchId,
        UUID sessionId,
        int resultVersion,
        Instant completedAt,
        MatchSource source,
        SportCode sportCode,
        MatchFormat matchFormat,
        TeamSide winnerTeam,
        Integer teamAScore,
        Integer teamBScore,
        List<RatingParticipantEvidence> teamA,
        List<RatingParticipantEvidence> teamB
) {
    public CompletedMatchRatingContext {
        require(matchId != null, "matchId is required");
        require(sessionId != null, "sessionId is required");
        require(resultVersion >= 1, "resultVersion must be at least 1");
        require(completedAt != null, "completedAt is required");
        require(source != null, "source is required");
        require(sportCode != null, "sportCode is required");
        require(matchFormat != null, "matchFormat is required");
        require(winnerTeam != null, "winnerTeam is required");
        require(teamA != null, "teamA is required");
        require(teamB != null, "teamB is required");

        teamA = List.copyOf(teamA);
        teamB = List.copyOf(teamB);

        validateTeam(teamA, TeamSide.A);
        validateTeam(teamB, TeamSide.B);

        Set<UUID> playerIds = new HashSet<>();
        teamA.forEach(participant -> playerIds.add(participant.playerId()));
        teamB.forEach(participant -> playerIds.add(participant.playerId()));
        require(playerIds.size() == 4,
                "Completed Match evidence requires four distinct playerIds");
    }

    private static void validateTeam(
            List<RatingParticipantEvidence> participants,
            TeamSide expectedSide
    ) {
        require(participants.size() == 2,
                "Completed Match evidence requires exactly two participants on Team "
                        + expectedSide);
        require(participants.stream()
                        .allMatch(participant -> participant.teamSide() == expectedSide),
                "Participant team metadata must match Team " + expectedSide);
        Set<Integer> slots = new HashSet<>();
        participants.forEach(participant -> slots.add(participant.teamSlot()));
        require(slots.equals(Set.of(1, 2)),
                "Team " + expectedSide + " must contain slots 1 and 2");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidCompletedMatchRatingEvidenceException(message);
        }
    }
}

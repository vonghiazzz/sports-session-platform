package com.sportssession.platform.matchmaking.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CompletedMatchPairing(
        UUID matchId,
        Instant completedAt,
        List<UUID> teamA,
        List<UUID> teamB
) {
    public CompletedMatchPairing {
        Objects.requireNonNull(matchId, "matchId is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
        teamA = copyTeam(teamA, "teamA");
        teamB = copyTeam(teamB, "teamB");

        Set<UUID> participants = new HashSet<>(teamA);
        participants.addAll(teamB);
        if (participants.size() != 4) {
            throw new InvalidMatchmakingInputException(
                    "Completed Match pairing requires four unique "
                            + "SessionParticipants"
            );
        }
    }

    public Set<UUID> participantIds() {
        Set<UUID> participants = new HashSet<>(teamA);
        participants.addAll(teamB);
        return Set.copyOf(participants);
    }

    public boolean contains(UUID sessionParticipantId) {
        Objects.requireNonNull(
                sessionParticipantId,
                "sessionParticipantId is required"
        );
        return teamA.contains(sessionParticipantId)
                || teamB.contains(sessionParticipantId);
    }

    private static List<UUID> copyTeam(List<UUID> team, String name) {
        Objects.requireNonNull(team, name + " is required");
        if (team.size() != 2 || team.stream().anyMatch(Objects::isNull)) {
            throw new InvalidMatchmakingInputException(
                    name + " must contain exactly two SessionParticipants"
            );
        }
        if (new HashSet<>(team).size() != 2) {
            throw new InvalidMatchmakingInputException(
                    name + " must contain unique SessionParticipants"
            );
        }
        return List.copyOf(team);
    }
}

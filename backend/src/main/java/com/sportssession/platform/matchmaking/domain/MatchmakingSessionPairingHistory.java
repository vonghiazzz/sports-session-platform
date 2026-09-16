package com.sportssession.platform.matchmaking.domain;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class MatchmakingSessionPairingHistory {

    private static final Comparator<CompletedMatchPairing> MATCH_CHRONOLOGY =
            Comparator.comparing(CompletedMatchPairing::completedAt)
                    .thenComparing(match -> match.matchId().toString());

    private final UUID sessionId;
    private final List<CompletedMatchPairing> completedMatches;
    private final Map<ParticipantPair, Integer> teammateMatchCounts;
    private final Map<ParticipantPair, Integer> opponentMatchCounts;
    private final Map<UUID, CompletedMatchPairing> latestMatchByParticipant;

    public MatchmakingSessionPairingHistory(
            UUID sessionId,
            List<CompletedMatchPairing> completedMatches
    ) {
        this.sessionId = Objects.requireNonNull(
                sessionId,
                "sessionId is required"
        );
        Objects.requireNonNull(
                completedMatches,
                "completedMatches are required"
        );
        this.completedMatches = List.copyOf(completedMatches);

        Map<ParticipantPair, Integer> teammateCounts = new HashMap<>();
        Map<ParticipantPair, Integer> opponentCounts = new HashMap<>();
        Map<UUID, CompletedMatchPairing> latestMatches = new HashMap<>();
        Set<UUID> matchIds = new HashSet<>();
        for (CompletedMatchPairing match : this.completedMatches) {
            Objects.requireNonNull(match, "completedMatch is required");
            if (!matchIds.add(match.matchId())) {
                throw new InvalidMatchmakingInputException(
                        "completed Match IDs must be unique"
                );
            }
            countPair(teammateCounts, match.teamA().get(0), match.teamA().get(1));
            countPair(teammateCounts, match.teamB().get(0), match.teamB().get(1));
            for (UUID teamAPlayer : match.teamA()) {
                for (UUID teamBPlayer : match.teamB()) {
                    countPair(opponentCounts, teamAPlayer, teamBPlayer);
                }
            }
            for (UUID participantId : match.participantIds()) {
                latestMatches.compute(participantId, (ignored, current) ->
                        current == null
                                || MATCH_CHRONOLOGY.compare(match, current) > 0
                                ? match
                                : current
                );
            }
        }
        teammateMatchCounts = Map.copyOf(teammateCounts);
        opponentMatchCounts = Map.copyOf(opponentCounts);
        latestMatchByParticipant = Map.copyOf(latestMatches);
    }

    public static MatchmakingSessionPairingHistory empty(UUID sessionId) {
        return new MatchmakingSessionPairingHistory(sessionId, List.of());
    }

    public UUID sessionId() {
        return sessionId;
    }

    public List<CompletedMatchPairing> completedMatches() {
        return completedMatches;
    }

    public boolean isImmediateQuartetRepeat(
            UUID anchorSessionParticipantId,
            Collection<UUID> candidateParticipantIds
    ) {
        Objects.requireNonNull(
                anchorSessionParticipantId,
                "anchorSessionParticipantId is required"
        );
        Objects.requireNonNull(
                candidateParticipantIds,
                "candidateParticipantIds are required"
        );
        Set<UUID> candidateQuartet = new HashSet<>();
        for (UUID participantId : candidateParticipantIds) {
            Objects.requireNonNull(
                    participantId,
                    "candidate sessionParticipantId is required"
            );
            candidateQuartet.add(participantId);
        }
        if (candidateQuartet.size() != 4) {
            throw new InvalidMatchmakingInputException(
                    "Candidate quartet requires four unique "
                            + "SessionParticipants"
            );
        }
        CompletedMatchPairing latest = latestMatchByParticipant.get(
                anchorSessionParticipantId
        );
        return latest != null
                && latest.participantIds().equals(candidateQuartet);
    }

    public int teammateMatchCount(UUID first, UUID second) {
        return teammateMatchCounts.getOrDefault(
                ParticipantPair.of(first, second),
                0
        );
    }

    public int opponentMatchCount(UUID first, UUID second) {
        return opponentMatchCounts.getOrDefault(
                ParticipantPair.of(first, second),
                0
        );
    }

    private void countPair(
            Map<ParticipantPair, Integer> counts,
            UUID first,
            UUID second
    ) {
        counts.merge(ParticipantPair.of(first, second), 1, Math::addExact);
    }

    private record ParticipantPair(UUID first, UUID second) {
        private static ParticipantPair of(UUID first, UUID second) {
            Objects.requireNonNull(first, "first participant is required");
            Objects.requireNonNull(second, "second participant is required");
            if (first.equals(second)) {
                throw new InvalidMatchmakingInputException(
                        "Pair requires two different SessionParticipants"
                );
            }
            return first.toString().compareTo(second.toString()) < 0
                    ? new ParticipantPair(first, second)
                    : new ParticipantPair(second, first);
        }
    }
}

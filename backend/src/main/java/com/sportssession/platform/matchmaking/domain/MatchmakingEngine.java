package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.match.domain.TeamSide;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MatchmakingEngine {

    public static final String ALGORITHM_VERSION =
            "fairness-anchor-rating-sum-v1";

    private static final Comparator<MatchmakingCandidate> PLAYER_KEY_ORDER =
            Comparator.comparing(candidate -> candidate.playerId().toString());

    private static final int[][] PARTITIONS = {
            {0, 1, 2, 3},
            {0, 2, 1, 3},
            {0, 3, 1, 2}
    };

    public MatchmakingResult recommend(MatchmakingContext context) {
        if (context == null) {
            throw new InvalidMatchmakingInputException("context is required");
        }

        List<MatchmakingCandidate> candidates = context.candidates().stream()
                .sorted(PLAYER_KEY_ORDER)
                .toList();
        if (candidates.size() < 4) {
            return new MatchmakingUnavailable(
                    ALGORITHM_VERSION,
                    context.evaluationTime(),
                    context.sessionId(),
                    context.sessionCourtId(),
                    context.sportCode(),
                    context.matchFormat(),
                    candidates.size(),
                    MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
            );
        }

        Instant oldestWaitingSince = candidates.stream()
                .map(MatchmakingCandidate::waitingSince)
                .min(Comparator.naturalOrder())
                .orElseThrow();
        PartitionEvaluation best = null;

        for (int first = 0; first < candidates.size() - 3; first++) {
            for (int second = first + 1;
                 second < candidates.size() - 2;
                 second++) {
                for (int third = second + 1;
                     third < candidates.size() - 1;
                     third++) {
                    for (int fourth = third + 1;
                         fourth < candidates.size();
                         fourth++) {
                        List<MatchmakingCandidate> group = List.of(
                                candidates.get(first),
                                candidates.get(second),
                                candidates.get(third),
                                candidates.get(fourth)
                        );
                        if (group.stream().noneMatch(candidate ->
                                candidate.waitingSince().equals(
                                        oldestWaitingSince))) {
                            continue;
                        }
                        for (int[] partition : PARTITIONS) {
                            PartitionEvaluation evaluated = evaluate(
                                    group,
                                    partition
                            );
                            if (best == null || compare(evaluated, best) < 0) {
                                best = evaluated;
                            }
                        }
                    }
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException(
                    "At least one anchored partition must exist");
        }
        return recommendation(context, candidates.size(), oldestWaitingSince, best);
    }

    private PartitionEvaluation evaluate(
            List<MatchmakingCandidate> group,
            int[] partition
    ) {
        List<MatchmakingCandidate> firstPair = canonicalPair(
                group.get(partition[0]),
                group.get(partition[1])
        );
        List<MatchmakingCandidate> secondPair = canonicalPair(
                group.get(partition[2]),
                group.get(partition[3])
        );
        List<MatchmakingCandidate> teamA;
        List<MatchmakingCandidate> teamB;
        if (comparePlayerKeys(firstPair, secondPair) <= 0) {
            teamA = firstPair;
            teamB = secondPair;
        } else {
            teamA = secondPair;
            teamB = firstPair;
        }

        BigDecimal teamATotal = ratingTotal(teamA);
        BigDecimal teamBTotal = ratingTotal(teamB);
        BigDecimal difference = teamATotal.subtract(teamBTotal).abs();
        List<Instant> waitingVector = group.stream()
                .map(MatchmakingCandidate::waitingSince)
                .sorted()
                .toList();
        List<String> selectedPlayerKey = group.stream()
                .map(candidate -> candidate.playerId().toString())
                .sorted()
                .toList();
        List<String> partitionKey = List.of(
                teamA.get(0).playerId().toString(),
                teamA.get(1).playerId().toString(),
                teamB.get(0).playerId().toString(),
                teamB.get(1).playerId().toString()
        );
        return new PartitionEvaluation(
                teamA,
                teamB,
                teamATotal,
                teamBTotal,
                difference,
                waitingVector,
                selectedPlayerKey,
                partitionKey
        );
    }

    private MatchRecommendation recommendation(
            MatchmakingContext context,
            int eligiblePlayerCount,
            Instant oldestWaitingSince,
            PartitionEvaluation evaluation
    ) {
        RecommendedTeam teamA = recommendedTeam(
                TeamSide.A,
                evaluation.teamA(),
                evaluation.teamATotal(),
                context.evaluationTime()
        );
        RecommendedTeam teamB = recommendedTeam(
                TeamSide.B,
                evaluation.teamB(),
                evaluation.teamBTotal(),
                context.evaluationTime()
        );
        return new MatchRecommendation(
                ALGORITHM_VERSION,
                context.evaluationTime(),
                context.sessionId(),
                context.sessionCourtId(),
                context.sportCode(),
                context.matchFormat(),
                eligiblePlayerCount,
                teamA,
                teamB,
                evaluation.teamATotal(),
                evaluation.teamBTotal(),
                evaluation.ratingDifference(),
                oldestWaitingSince
        );
    }

    private RecommendedTeam recommendedTeam(
            TeamSide teamSide,
            List<MatchmakingCandidate> players,
            BigDecimal ratingTotal,
            Instant evaluationTime
    ) {
        return new RecommendedTeam(
                teamSide,
                recommendedPlayer(
                        players.get(0),
                        teamSide,
                        1,
                        evaluationTime
                ),
                recommendedPlayer(
                        players.get(1),
                        teamSide,
                        2,
                        evaluationTime
                ),
                ratingTotal
        );
    }

    private RecommendedPlayer recommendedPlayer(
            MatchmakingCandidate candidate,
            TeamSide teamSide,
            int teamSlot,
            Instant evaluationTime
    ) {
        long waitingSeconds = Duration.between(
                candidate.waitingSince(),
                evaluationTime
        ).getSeconds();
        return new RecommendedPlayer(
                candidate.sessionParticipantId(),
                candidate.playerId(),
                teamSide,
                teamSlot,
                candidate.waitingSince(),
                waitingSeconds,
                candidate.ratingValue(),
                candidate.uncertainty(),
                candidate.ratedMatches(),
                candidate.ratingBasis()
        );
    }

    private List<MatchmakingCandidate> canonicalPair(
            MatchmakingCandidate first,
            MatchmakingCandidate second
    ) {
        List<MatchmakingCandidate> pair = new ArrayList<>(List.of(first, second));
        pair.sort(PLAYER_KEY_ORDER);
        return List.copyOf(pair);
    }

    private BigDecimal ratingTotal(List<MatchmakingCandidate> team) {
        return team.get(0).ratingValue().add(team.get(1).ratingValue());
    }

    private int compare(PartitionEvaluation left, PartitionEvaluation right) {
        int result = left.ratingDifference().compareTo(right.ratingDifference());
        if (result != 0) {
            return result;
        }
        result = compareLists(left.waitingVector(), right.waitingVector());
        if (result != 0) {
            return result;
        }
        result = compareLists(
                left.selectedPlayerKey(),
                right.selectedPlayerKey()
        );
        if (result != 0) {
            return result;
        }
        return compareLists(left.partitionKey(), right.partitionKey());
    }

    private int comparePlayerKeys(
            List<MatchmakingCandidate> left,
            List<MatchmakingCandidate> right
    ) {
        for (int index = 0; index < 2; index++) {
            int compared = left.get(index).playerId().toString().compareTo(
                    right.get(index).playerId().toString()
            );
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private <T extends Comparable<? super T>> int compareLists(
            List<T> left,
            List<T> right
    ) {
        for (int index = 0; index < left.size(); index++) {
            int compared = left.get(index).compareTo(right.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    private record PartitionEvaluation(
            List<MatchmakingCandidate> teamA,
            List<MatchmakingCandidate> teamB,
            BigDecimal teamATotal,
            BigDecimal teamBTotal,
            BigDecimal ratingDifference,
            List<Instant> waitingVector,
            List<String> selectedPlayerKey,
            List<String> partitionKey
    ) {
    }
}

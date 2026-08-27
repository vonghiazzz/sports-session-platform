package com.sportssession.platform.rating.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WengLinPlackettLuceRatingEngine implements RatingEngine {

    public static final String ALGORITHM_VERSION = "weng-lin-pl-v1";

    static final double BASE_MU = 25.0;
    static final double INITIAL_SIGMA = BASE_MU / 3.0;
    static final double BETA = BASE_MU / 6.0;
    static final double KAPPA = 0.0001;
    private static final int TEAM_SIZE = 2;

    @Override
    public List<RatingUpdate> rate(
            List<RatingState> teamA,
            List<RatingState> teamB,
            WinningTeam winner
    ) {
        validateTeam(teamA, "teamA");
        validateTeam(teamB, "teamB");
        Objects.requireNonNull(winner, "winner is required");

        TeamStatistics statisticsA = aggregate(teamA);
        TeamStatistics statisticsB = aggregate(teamB);
        CollectiveScale scale = collectiveScale(statisticsA, statisticsB);
        double probabilityA = plProbability(
                statisticsA.mu(),
                statisticsB.mu(),
                scale.c()
        );
        double probabilityB = 1.0 - probabilityA;

        TeamAdjustment adjustmentA = adjustment(
                statisticsA,
                scale,
                probabilityA,
                winner == WinningTeam.A
        );
        TeamAdjustment adjustmentB = adjustment(
                statisticsB,
                scale,
                probabilityB,
                winner == WinningTeam.B
        );

        List<RatingUpdate> updates = new ArrayList<>(TEAM_SIZE * 2);
        addPlayerUpdates(updates, teamA, statisticsA, adjustmentA);
        addPlayerUpdates(updates, teamB, statisticsB, adjustmentB);
        return List.copyOf(updates);
    }

    @Override
    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    static TeamStatistics aggregate(List<RatingState> team) {
        double mu = 0.0;
        double variance = 0.0;
        for (RatingState player : team) {
            mu += player.mu();
            variance += player.sigma() * player.sigma();
        }
        RatingNumericNormalizer.requireFinite(mu, "team mu");
        RatingNumericNormalizer.requireFinite(variance, "team variance");
        if (variance <= 0.0) {
            throw new IllegalArgumentException(
                    "team variance must be greater than zero");
        }
        return new TeamStatistics(mu, variance, StrictMath.sqrt(variance));
    }

    static CollectiveScale collectiveScale(
            TeamStatistics teamA,
            TeamStatistics teamB
    ) {
        double betaSquared = BETA * BETA;
        double cSquared = teamA.variance()
                + betaSquared
                + teamB.variance()
                + betaSquared;
        RatingNumericNormalizer.requireFinite(cSquared, "collective variance");
        return new CollectiveScale(StrictMath.sqrt(cSquared), cSquared);
    }

    static double plProbability(double teamMu, double opponentMu, double c) {
        double difference = teamMu - opponentMu;
        RatingNumericNormalizer.requireFinite(difference, "team mu difference");
        double scaledDifference = difference / c;
        if (scaledDifference >= 0.0) {
            return 1.0 / (1.0 + StrictMath.exp(-scaledDifference));
        }
        double exponential = StrictMath.exp(scaledDifference);
        return exponential / (1.0 + exponential);
    }

    static TeamAdjustment adjustment(
            TeamStatistics team,
            CollectiveScale scale,
            double winProbability,
            boolean won
    ) {
        double observedResult = won ? 1.0 : 0.0;
        double omega = (team.variance() / scale.c())
                * (observedResult - winProbability);
        double gamma = team.sigma() / scale.c();
        double delta = gamma
                * (team.variance() / scale.cSquared())
                * winProbability
                * (1.0 - winProbability);
        RatingNumericNormalizer.requireFinite(omega, "omega");
        RatingNumericNormalizer.requireFinite(delta, "delta");
        return new TeamAdjustment(omega, delta, gamma);
    }

    private static void addPlayerUpdates(
            List<RatingUpdate> updates,
            List<RatingState> team,
            TeamStatistics statistics,
            TeamAdjustment adjustment
    ) {
        for (RatingState before : team) {
            double playerVariance = before.sigma() * before.sigma();
            double varianceShare = playerVariance / statistics.variance();
            double muAfter = before.mu()
                    + varianceShare * adjustment.omega();
            double rawVarianceMultiplier = 1.0
                    - varianceShare * adjustment.delta();

            // In locked two-team V1, delta < 0.25 and share < 1, so the
            // raw multiplier is > 0.75. Kappa remains the published safeguard.
            double varianceMultiplier = StrictMath.max(
                    rawVarianceMultiplier,
                    KAPPA
            );
            double varianceAfter = playerVariance * varianceMultiplier;
            double sigmaAfter = StrictMath.sqrt(varianceAfter);
            updates.add(new RatingUpdate(
                    before,
                    new RatingState(muAfter, sigmaAfter)
            ));
        }
    }

    private static void validateTeam(List<RatingState> team, String name) {
        Objects.requireNonNull(team, name + " is required");
        if (team.size() != TEAM_SIZE) {
            throw new IllegalArgumentException(
                    name + " must contain exactly two RatingStates");
        }
        for (int index = 0; index < team.size(); index++) {
            Objects.requireNonNull(
                    team.get(index),
                    name + " RatingState at index " + index + " is required"
            );
        }
    }

    record TeamStatistics(double mu, double variance, double sigma) {
    }

    record CollectiveScale(double c, double cSquared) {
    }

    record TeamAdjustment(double omega, double delta, double gamma) {
    }
}

package com.sportssession.platform.rating.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WengLinPlackettLuceRatingEngineTest {

    private static final double FRESH_SIGMA = 8.333333333;
    private static final WengLinPlackettLuceRatingEngine ENGINE =
            new WengLinPlackettLuceRatingEngine();

    @Test
    void exposesLockedAlgorithmVersionAndConstants() {
        assertThat(ENGINE.algorithmVersion()).isEqualTo("weng-lin-pl-v1");
        assertThat(WengLinPlackettLuceRatingEngine.BASE_MU).isEqualTo(25.0);
        assertThat(WengLinPlackettLuceRatingEngine.INITIAL_SIGMA)
                .isEqualTo(25.0 / 3.0);
        assertThat(WengLinPlackettLuceRatingEngine.BETA)
                .isEqualTo(25.0 / 6.0);
        assertThat(WengLinPlackettLuceRatingEngine.KAPPA)
                .isEqualTo(0.0001);
    }

    @Test
    void goldenOneEqualFreshPlayersTeamAWins() {
        List<RatingUpdate> updates = rate(
                team(state(25, FRESH_SIGMA), state(25, FRESH_SIGMA)),
                team(state(25, FRESH_SIGMA), state(25, FRESH_SIGMA)),
                WinningTeam.A
        );

        assertUpdates(updates,
                state(26.964185503, 8.177556357),
                state(26.964185503, 8.177556357),
                state(23.035814497, 8.177556357),
                state(23.035814497, 8.177556357));
    }

    @Test
    void goldenTwoExpectedStrongTeamWin() {
        List<RatingUpdate> updates = expectedStrongTeamWin();

        assertUpdates(updates,
                state(35.552386939, 8.258402475),
                state(31.552386939, 8.258402475),
                state(18.447613061, 8.258402475),
                state(14.447613061, 8.258402475));
    }

    @Test
    void goldenThreeWeakTeamUpsetProducesMateriallyLargerMovement() {
        List<RatingUpdate> expected = expectedStrongTeamWin();
        List<RatingUpdate> upset = rate(
                freshTeam(35, 31),
                freshTeam(19, 15),
                WinningTeam.B
        );

        assertUpdates(upset,
                state(31.624015932, 8.258402475),
                state(27.624015932, 8.258402475),
                state(22.375984068, 8.258402475),
                state(18.375984068, 8.258402475));
        assertThat(meanMovement(upset.get(0)))
                .isGreaterThan(meanMovement(expected.get(0)) * 6.0);
    }

    @Test
    void goldenFourMixedUncertaintyUsesIndividualVarianceShare() {
        List<RatingUpdate> updates = rate(
                team(state(25, 10), state(25, 3)),
                freshTeam(25, 25),
                WinningTeam.A
        );

        assertUpdates(updates,
                state(27.974235740, 9.721432172),
                state(25.267681217, 2.992574238),
                state(22.934558514, 8.151919958),
                state(22.934558514, 8.151919958));
        assertThat(meanMovement(updates.get(0)))
                .isGreaterThan(meanMovement(updates.get(1)) * 10.0);
    }

    @Test
    void goldenFiveEstablishedPlayersMoveLessThanFreshPlayers() {
        List<RatingUpdate> established = rate(
                team(state(25, 3), state(25, 3)),
                team(state(25, 3), state(25, 3)),
                WinningTeam.A
        );
        List<RatingUpdate> fresh = rate(
                freshTeam(25, 25),
                freshTeam(25, 25),
                WinningTeam.A
        );

        assertUpdates(established,
                state(25.535099524, 2.975827063),
                state(25.535099524, 2.975827063),
                state(24.464900476, 2.975827063),
                state(24.464900476, 2.975827063));
        assertThat(meanMovement(established.get(0)))
                .isLessThan(meanMovement(fresh.get(0)));
    }

    @Test
    void swappingTeamsAndWinnerProducesSymmetricUpdates() {
        List<RatingState> teamA = team(state(35, 7), state(31, 5));
        List<RatingState> teamB = team(state(23, 4), state(19, 8));

        List<RatingUpdate> original = rate(teamA, teamB, WinningTeam.A);
        List<RatingUpdate> swapped = rate(teamB, teamA, WinningTeam.B);

        assertThat(swapped.get(0)).isEqualTo(original.get(2));
        assertThat(swapped.get(1)).isEqualTo(original.get(3));
        assertThat(swapped.get(2)).isEqualTo(original.get(0));
        assertThat(swapped.get(3)).isEqualTo(original.get(1));
    }

    @Test
    void memberOrderingDoesNotChangeCorrespondingPlayerUpdates() {
        RatingState a1 = state(35, 9);
        RatingState a2 = state(31, 4);
        RatingState b1 = state(23, 7);
        RatingState b2 = state(19, 5);

        List<RatingUpdate> original = rate(
                team(a1, a2), team(b1, b2), WinningTeam.A);
        List<RatingUpdate> reordered = rate(
                team(a2, a1), team(b2, b1), WinningTeam.A);

        assertThat(reordered.get(0)).isEqualTo(original.get(1));
        assertThat(reordered.get(1)).isEqualTo(original.get(0));
        assertThat(reordered.get(2)).isEqualTo(original.get(3));
        assertThat(reordered.get(3)).isEqualTo(original.get(2));
    }

    @Test
    void aggregatesTeamMeansAndVariancesBySum() {
        var statistics = WengLinPlackettLuceRatingEngine.aggregate(
                team(state(35, 3), state(31, 4))
        );

        assertThat(statistics.mu()).isEqualTo(66.0);
        assertThat(statistics.variance()).isEqualTo(25.0);
        assertThat(statistics.sigma()).isEqualTo(5.0);
    }

    @Test
    void collectiveScaleIncludesBetaSquaredForEachTeam() {
        var teamA = WengLinPlackettLuceRatingEngine.aggregate(
                team(state(35, 3), state(31, 4)));
        var teamB = WengLinPlackettLuceRatingEngine.aggregate(
                team(state(19, 4), state(15, 5)));

        var scale = WengLinPlackettLuceRatingEngine.collectiveScale(
                teamA,
                teamB
        );
        double expectedSquared = 25.0 + 41.0
                + 2.0 * StrictMath.pow(25.0 / 6.0, 2.0);

        assertThat(scale.cSquared()).isEqualTo(expectedSquared);
        assertThat(scale.c()).isEqualTo(StrictMath.sqrt(expectedSquared));
    }

    @Test
    void calculatesPublishedProbabilityOmegaDeltaAndGamma() {
        var teamA = WengLinPlackettLuceRatingEngine.aggregate(
                freshTeam(35, 31));
        var teamB = WengLinPlackettLuceRatingEngine.aggregate(
                freshTeam(19, 15));
        var scale = WengLinPlackettLuceRatingEngine.collectiveScale(
                teamA,
                teamB
        );
        double probability = WengLinPlackettLuceRatingEngine.plProbability(
                teamA.mu(),
                teamB.mu(),
                scale.c()
        );
        var adjustment = WengLinPlackettLuceRatingEngine.adjustment(
                teamA,
                scale,
                probability,
                true
        );

        assertThat(probability).isEqualTo(
                1.0 / (1.0 + StrictMath.exp(-32.0 / scale.c()))
        );
        assertThat(adjustment.gamma())
                .isEqualTo(teamA.sigma() / scale.c());
        assertThat(adjustment.omega()).isEqualTo(
                teamA.variance() / scale.c() * (1.0 - probability)
        );
        assertThat(adjustment.delta()).isEqualTo(
                adjustment.gamma()
                        * teamA.variance() / scale.cSquared()
                        * probability * (1.0 - probability)
        );
    }

    @Test
    void representativeCasesRespectAnalyticalKappaSafetyBound() {
        assertRawMultipliersAboveThreeQuarters(
                freshTeam(25, 25), freshTeam(25, 25));
        assertRawMultipliersAboveThreeQuarters(
                freshTeam(35, 31), freshTeam(19, 15));
        assertRawMultipliersAboveThreeQuarters(
                team(state(25, 10), state(25, 3)), freshTeam(25, 25));
        assertRawMultipliersAboveThreeQuarters(
                team(state(25, 3), state(25, 3)),
                team(state(25, 3), state(25, 3)));
    }

    @Test
    void everyGoldenOutputHasFinitePositiveSigma() {
        List<List<RatingUpdate>> cases = List.of(
                rate(freshTeam(25, 25), freshTeam(25, 25), WinningTeam.A),
                expectedStrongTeamWin(),
                rate(freshTeam(35, 31), freshTeam(19, 15), WinningTeam.B),
                rate(team(state(25, 10), state(25, 3)),
                        freshTeam(25, 25), WinningTeam.A),
                rate(team(state(25, 3), state(25, 3)),
                        team(state(25, 3), state(25, 3)), WinningTeam.A)
        );

        assertThat(cases)
                .flatExtracting(updates -> updates)
                .allSatisfy(update -> {
                    assertThat(update.after().sigma()).isFinite();
                    assertThat(update.after().sigma()).isPositive();
                });
    }

    @Test
    void normalizedOutputIsTheCanonicalInputToTheNextMatch() {
        List<RatingUpdate> first = rate(
                freshTeam(25, 25), freshTeam(25, 25), WinningTeam.A);
        List<RatingState> nextA = team(
                first.get(0).after(), first.get(1).after());
        List<RatingState> nextB = team(
                first.get(2).after(), first.get(3).after());

        List<RatingUpdate> second = rate(nextA, nextB, WinningTeam.B);

        assertThat(second.get(0).before()).isSameAs(first.get(0).after());
        assertThat(second.get(1).before()).isSameAs(first.get(1).after());
        assertThat(second.get(2).before()).isSameAs(first.get(2).after());
        assertThat(second.get(3).before()).isSameAs(first.get(3).after());
    }

    @Test
    void returnsExactlyFourImmutableBeforeAfterUpdates() {
        List<RatingUpdate> updates = rate(
                freshTeam(25, 25), freshTeam(25, 25), WinningTeam.A);

        assertThat(updates).hasSize(4);
        assertThat(updates).allSatisfy(update -> {
            assertThat(update.before()).isNotNull();
            assertThat(update.after()).isNotNull();
        });
        assertThatThrownBy(() -> updates.add(updates.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullWinnerTeamsStatesAndWrongCardinality() {
        List<RatingState> valid = freshTeam(25, 25);

        assertThatNullPointerException()
                .isThrownBy(() -> ENGINE.rate(valid, valid, null))
                .withMessage("winner is required");
        assertThatNullPointerException()
                .isThrownBy(() -> ENGINE.rate(null, valid, WinningTeam.A))
                .withMessage("teamA is required");
        assertThatNullPointerException()
                .isThrownBy(() -> ENGINE.rate(valid, null, WinningTeam.A))
                .withMessage("teamB is required");
        assertThatThrownBy(() -> ENGINE.rate(
                List.of(state(25, FRESH_SIGMA)),
                valid,
                WinningTeam.A
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamA must contain exactly two RatingStates");
        assertThatThrownBy(() -> ENGINE.rate(
                valid,
                List.of(state(25, FRESH_SIGMA), state(25, FRESH_SIGMA),
                        state(25, FRESH_SIGMA)),
                WinningTeam.A
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("teamB must contain exactly two RatingStates");

        List<RatingState> nullStateTeam = new ArrayList<>(Arrays.asList(
                state(25, FRESH_SIGMA), null));
        assertThatNullPointerException()
                .isThrownBy(() -> ENGINE.rate(
                        nullStateTeam,
                        valid,
                        WinningTeam.A
                )).withMessage("teamA RatingState at index 1 is required");
    }

    private static List<RatingUpdate> expectedStrongTeamWin() {
        return rate(
                freshTeam(35, 31),
                freshTeam(19, 15),
                WinningTeam.A
        );
    }

    private static void assertRawMultipliersAboveThreeQuarters(
            List<RatingState> teamA,
            List<RatingState> teamB
    ) {
        var statisticsA = WengLinPlackettLuceRatingEngine.aggregate(teamA);
        var statisticsB = WengLinPlackettLuceRatingEngine.aggregate(teamB);
        var scale = WengLinPlackettLuceRatingEngine.collectiveScale(
                statisticsA,
                statisticsB
        );
        double probabilityA = WengLinPlackettLuceRatingEngine.plProbability(
                statisticsA.mu(), statisticsB.mu(), scale.c());
        var adjustmentA = WengLinPlackettLuceRatingEngine.adjustment(
                statisticsA, scale, probabilityA, true);
        var adjustmentB = WengLinPlackettLuceRatingEngine.adjustment(
                statisticsB, scale, 1.0 - probabilityA, false);

        assertTeamRawMultipliers(teamA, statisticsA, adjustmentA);
        assertTeamRawMultipliers(teamB, statisticsB, adjustmentB);
    }

    private static void assertTeamRawMultipliers(
            List<RatingState> team,
            WengLinPlackettLuceRatingEngine.TeamStatistics statistics,
            WengLinPlackettLuceRatingEngine.TeamAdjustment adjustment
    ) {
        assertThat(adjustment.delta()).isLessThan(0.25);
        for (RatingState state : team) {
            double share = state.sigma() * state.sigma()
                    / statistics.variance();
            double rawMultiplier = 1.0 - share * adjustment.delta();
            assertThat(share).isGreaterThan(0.0).isLessThan(1.0);
            assertThat(rawMultiplier).isGreaterThan(0.75);
            assertThat(rawMultiplier)
                    .isGreaterThan(WengLinPlackettLuceRatingEngine.KAPPA);
        }
    }

    private static List<RatingUpdate> rate(
            List<RatingState> teamA,
            List<RatingState> teamB,
            WinningTeam winner
    ) {
        return ENGINE.rate(teamA, teamB, winner);
    }

    private static List<RatingState> freshTeam(double firstMu, double secondMu) {
        return team(
                state(firstMu, FRESH_SIGMA),
                state(secondMu, FRESH_SIGMA)
        );
    }

    private static List<RatingState> team(
            RatingState first,
            RatingState second
    ) {
        return List.of(first, second);
    }

    private static RatingState state(double mu, double sigma) {
        return new RatingState(mu, sigma);
    }

    private static void assertUpdates(
            List<RatingUpdate> actual,
            RatingState... expectedAfter
    ) {
        assertThat(actual).hasSize(expectedAfter.length);
        for (int index = 0; index < expectedAfter.length; index++) {
            assertThat(actual.get(index).after())
                    .as("after state at index %s", index)
                    .isEqualTo(expectedAfter[index]);
        }
    }

    private static double meanMovement(RatingUpdate update) {
        return StrictMath.abs(update.after().mu() - update.before().mu());
    }
}

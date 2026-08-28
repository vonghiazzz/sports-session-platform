package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchmakingEngineTest {

    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-08-28T12:00:00Z");
    private static final UUID SESSION_ID = uuid(9001);
    private static final UUID SESSION_COURT_ID = uuid(9002);
    private static final MatchmakingEngine ENGINE = new MatchmakingEngine();

    @Test
    void exposesOneLockedAlgorithmVersion() {
        assertThat(MatchmakingEngine.ALGORITHM_VERSION)
                .isEqualTo("fairness-anchor-rating-sum-v1");
    }

    @Test
    void fewerThanFourCandidatesReturnsExplicitUnavailableOutcome() {
        MatchmakingResult result = ENGINE.recommend(context(List.of(
                candidate(1, 300, "20"),
                candidate(2, 200, "25"),
                candidate(3, 100, "30")
        )));

        assertThat(result).isInstanceOf(MatchmakingUnavailable.class);
        MatchmakingUnavailable unavailable = (MatchmakingUnavailable) result;
        assertThat(unavailable.algorithmVersion())
                .isEqualTo(MatchmakingEngine.ALGORITHM_VERSION);
        assertThat(unavailable.evaluationTime()).isEqualTo(EVALUATION_TIME);
        assertThat(unavailable.sessionId()).isEqualTo(SESSION_ID);
        assertThat(unavailable.sessionCourtId()).isEqualTo(SESSION_COURT_ID);
        assertThat(unavailable.sportCode()).isEqualTo(SportCode.BADMINTON);
        assertThat(unavailable.matchFormat()).isEqualTo(MatchFormat.DOUBLES);
        assertThat(unavailable.eligiblePlayerCount()).isEqualTo(3);
        assertThat(unavailable.reason()).isEqualTo(
                MatchmakingUnavailableReason.INSUFFICIENT_ELIGIBLE_PLAYERS
        );
    }

    @Test
    void exactlyFourCandidatesSelectsAllAndBestOfThreePartitions() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 400, "10"),
                candidate(2, 300, "20"),
                candidate(3, 200, "30"),
                candidate(4, 100, "40")
        ));

        assertThat(selectedPlayerIds(recommendation))
                .containsExactlyInAnyOrder(uuid(1), uuid(2), uuid(3), uuid(4));
        assertTeam(recommendation.teamA(), uuid(1), uuid(4), "50");
        assertTeam(recommendation.teamB(), uuid(2), uuid(3), "50");
        assertThat(recommendation.ratingDifference())
                .isEqualByComparingTo("0");
    }

    @Test
    void oldestPlayerIsSelectedEvenWhenUnrestrictedBestGroupExcludesIt() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 600, "100"),
                candidate(2, 500, "10"),
                candidate(3, 400, "20"),
                candidate(4, 300, "30"),
                candidate(5, 200, "40"),
                candidate(6, 100, "50")
        ));

        assertThat(selectedPlayerIds(recommendation)).contains(uuid(1));
        assertThat(recommendation.oldestWaitingSince())
                .isEqualTo(EVALUATION_TIME.minusSeconds(600));
        assertThat(recommendation.ratingDifference())
                .isEqualByComparingTo("20");
    }

    @Test
    void sameOldestAnchorChoosesLowerTeamRatingDifference() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 600, "100"),
                candidate(2, 500, "10"),
                candidate(3, 400, "20"),
                candidate(4, 300, "40"),
                candidate(5, 200, "50"),
                candidate(6, 100, "70")
        ));

        assertThat(selectedPlayerIds(recommendation)).contains(uuid(1));
        assertThat(recommendation.ratingDifference())
                .isEqualByComparingTo("0");
    }

    @Test
    void equalRatingDifferencePrefersOlderSelectedWaitingVector() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 600, "25"),
                candidate(2, 500, "25"),
                candidate(3, 400, "25"),
                candidate(4, 300, "25"),
                candidate(5, 200, "25"),
                candidate(6, 100, "25")
        ));

        assertThat(selectedPlayerIds(recommendation))
                .containsExactlyInAnyOrder(uuid(1), uuid(2), uuid(3), uuid(4));
    }

    @Test
    void equalRatingsAndWaitingUseSelectedPlayerUuidTieBreak() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(6, 300, "25"),
                candidate(3, 300, "25"),
                candidate(5, 300, "25"),
                candidate(1, 300, "25"),
                candidate(4, 300, "25"),
                candidate(2, 300, "25")
        ));

        assertThat(selectedPlayerIds(recommendation))
                .containsExactlyInAnyOrder(uuid(1), uuid(2), uuid(3), uuid(4));
    }

    @Test
    void equalPartitionsUseCanonicalPartitionTieBreak() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(4, 100, "25"),
                candidate(3, 200, "25"),
                candidate(2, 300, "25"),
                candidate(1, 400, "25")
        ));

        assertTeam(recommendation.teamA(), uuid(1), uuid(2), "50");
        assertTeam(recommendation.teamB(), uuid(3), uuid(4), "50");
    }

    @Test
    void teamSwapSymmetryReturnsOnlyCanonicalOrientation() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(4, 100, "40"),
                candidate(2, 300, "20"),
                candidate(1, 400, "10"),
                candidate(3, 200, "30")
        ));

        assertThat(recommendation.teamA().teamSide()).isEqualTo(TeamSide.A);
        assertThat(recommendation.teamB().teamSide()).isEqualTo(TeamSide.B);
        assertTeam(recommendation.teamA(), uuid(1), uuid(4), "50");
        assertTeam(recommendation.teamB(), uuid(2), uuid(3), "50");
    }

    @Test
    void lowerCanonicalPlayerUuidInsidePairBecomesSlotOne() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(4, 100, "40"),
                candidate(1, 400, "10"),
                candidate(3, 200, "30"),
                candidate(2, 300, "20")
        ));

        assertThat(recommendation.teamA().slot1().playerId())
                .isEqualTo(uuid(1));
        assertThat(recommendation.teamA().slot1().teamSlot()).isEqualTo(1);
        assertThat(recommendation.teamA().slot2().playerId())
                .isEqualTo(uuid(4));
        assertThat(recommendation.teamA().slot2().teamSlot()).isEqualTo(2);
    }

    @Test
    void ratingBasisDoesNotAffectScoringAndRemainsExplanationMetadata() {
        List<MatchmakingCandidate> mixed = List.of(
                candidate(1, 400, "10", "8", 0, RatingBasis.INITIAL_PRIOR),
                candidate(2, 300, "20", "8", 9, RatingBasis.PERSISTED),
                candidate(3, 200, "30", "8", 2, RatingBasis.PERSISTED),
                candidate(4, 100, "40", "8", 0, RatingBasis.INITIAL_PRIOR)
        );
        List<MatchmakingCandidate> persisted = mixed.stream()
                .map(value -> new MatchmakingCandidate(
                        value.sessionParticipantId(),
                        value.playerId(),
                        value.waitingSince(),
                        value.ratingValue(),
                        value.uncertainty(),
                        value.ratedMatches(),
                        RatingBasis.PERSISTED
                ))
                .toList();

        MatchRecommendation mixedResult = recommend(mixed);
        MatchRecommendation persistedResult = recommend(persisted);

        assertSameScoredComposition(mixedResult, persistedResult);
        assertThat(players(mixedResult))
                .extracting(RecommendedPlayer::ratingBasis)
                .contains(RatingBasis.INITIAL_PRIOR, RatingBasis.PERSISTED);
    }

    @Test
    void uncertaintyDoesNotAffectScoring() {
        List<MatchmakingCandidate> baseline = List.of(
                candidate(1, 400, "10", "1", 1, RatingBasis.PERSISTED),
                candidate(2, 300, "20", "2", 1, RatingBasis.PERSISTED),
                candidate(3, 200, "30", "100", 1, RatingBasis.PERSISTED),
                candidate(4, 100, "40", "0.000001", 1, RatingBasis.PERSISTED)
        );
        List<MatchmakingCandidate> swapped = List.of(
                candidate(1, 400, "10", "1000", 1, RatingBasis.PERSISTED),
                candidate(2, 300, "20", "0.01", 1, RatingBasis.PERSISTED),
                candidate(3, 200, "30", "2", 1, RatingBasis.PERSISTED),
                candidate(4, 100, "40", "50", 1, RatingBasis.PERSISTED)
        );

        assertSameScoredComposition(recommend(baseline), recommend(swapped));
    }

    @Test
    void ratedMatchesDoesNotAffectScoring() {
        List<MatchmakingCandidate> baseline = List.of(
                candidate(1, 400, "10", "8", 0, RatingBasis.PERSISTED),
                candidate(2, 300, "20", "8", 1, RatingBasis.PERSISTED),
                candidate(3, 200, "30", "8", 100, RatingBasis.PERSISTED),
                candidate(4, 100, "40", "8", 999, RatingBasis.PERSISTED)
        );
        List<MatchmakingCandidate> changed = baseline.stream()
                .map(value -> new MatchmakingCandidate(
                        value.sessionParticipantId(),
                        value.playerId(),
                        value.waitingSince(),
                        value.ratingValue(),
                        value.uncertainty(),
                        7,
                        value.ratingBasis()
                ))
                .toList();

        assertSameScoredComposition(recommend(baseline), recommend(changed));
    }

    @Test
    void recentlyReleasedPlayerRemainsValidButHasLowerWaitingPriority() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 500, "25"),
                candidate(2, 400, "25"),
                candidate(3, 300, "25"),
                candidate(4, 200, "25"),
                candidate(5, 5, "25")
        ));

        assertThat(selectedPlayerIds(recommendation))
                .containsExactlyInAnyOrder(uuid(1), uuid(2), uuid(3), uuid(4))
                .doesNotContain(uuid(5));
    }

    @Test
    void duplicatePlayerIdIsInvalidInput() {
        MatchmakingCandidate first = candidate(1, 400, "10");
        MatchmakingCandidate duplicate = new MatchmakingCandidate(
                uuid(999),
                first.playerId(),
                EVALUATION_TIME.minusSeconds(300),
                new BigDecimal("20"),
                new BigDecimal("8"),
                0,
                RatingBasis.PERSISTED
        );

        assertThatThrownBy(() -> context(List.of(first, duplicate)))
                .isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("playerId must be unique");
    }

    @Test
    void duplicateSessionParticipantIdIsInvalidInput() {
        MatchmakingCandidate first = candidate(1, 400, "10");
        MatchmakingCandidate duplicate = new MatchmakingCandidate(
                first.sessionParticipantId(),
                uuid(999),
                EVALUATION_TIME.minusSeconds(300),
                new BigDecimal("20"),
                new BigDecimal("8"),
                0,
                RatingBasis.PERSISTED
        );

        assertThatThrownBy(() -> context(List.of(first, duplicate)))
                .isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("sessionParticipantId must be unique");
    }

    @Test
    void waitingSinceAfterEvaluationTimeIsInvalidInput() {
        MatchmakingCandidate future = new MatchmakingCandidate(
                uuid(101),
                uuid(1),
                EVALUATION_TIME.plusSeconds(1),
                new BigDecimal("25"),
                new BigDecimal("8"),
                0,
                RatingBasis.PERSISTED
        );

        assertThatThrownBy(() -> context(List.of(future)))
                .isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("waitingSince must not be after evaluationTime");
    }

    @Test
    void nonPositiveUncertaintyIsInvalidInput() {
        assertThatThrownBy(() -> candidate(
                1, 100, "25", "0", 0, RatingBasis.PERSISTED
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("uncertainty must be greater than zero");
        assertThatThrownBy(() -> candidate(
                1, 100, "25", "-0.1", 0, RatingBasis.PERSISTED
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("uncertainty must be greater than zero");
    }

    @Test
    void negativeRatedMatchesIsInvalidInput() {
        assertThatThrownBy(() -> candidate(
                1, 100, "25", "8", -1, RatingBasis.PERSISTED
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("ratedMatches must not be negative");
    }

    @Test
    void rejectsMissingCandidateValuesWithoutDefaulting() {
        MatchmakingCandidate valid = candidate(1, 100, "25");

        assertMissingCandidateValue(new MatchmakingCandidateValues(
                null, valid.playerId(), valid.waitingSince(), valid.ratingValue(),
                valid.uncertainty(), valid.ratingBasis()),
                "sessionParticipantId is required");
        assertMissingCandidateValue(new MatchmakingCandidateValues(
                valid.sessionParticipantId(), null, valid.waitingSince(),
                valid.ratingValue(), valid.uncertainty(), valid.ratingBasis()),
                "playerId is required");
        assertMissingCandidateValue(new MatchmakingCandidateValues(
                valid.sessionParticipantId(), valid.playerId(), null,
                valid.ratingValue(), valid.uncertainty(), valid.ratingBasis()),
                "waitingSince is required");
        assertMissingCandidateValue(new MatchmakingCandidateValues(
                valid.sessionParticipantId(), valid.playerId(),
                valid.waitingSince(), null, valid.uncertainty(),
                valid.ratingBasis()), "ratingValue is required");
        assertMissingCandidateValue(new MatchmakingCandidateValues(
                valid.sessionParticipantId(), valid.playerId(),
                valid.waitingSince(), valid.ratingValue(), null,
                valid.ratingBasis()), "uncertainty is required");
        assertMissingCandidateValue(new MatchmakingCandidateValues(
                valid.sessionParticipantId(), valid.playerId(),
                valid.waitingSince(), valid.ratingValue(), valid.uncertainty(),
                null), "ratingBasis is required");
    }

    @Test
    void nullCandidateElementIsInvalidInput() {
        List<MatchmakingCandidate> candidates = new ArrayList<>();
        candidates.add(null);

        assertThatThrownBy(() -> context(candidates))
                .isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("candidate is required");
    }

    @Test
    void nullEngineContextIsInvalidInput() {
        assertThatThrownBy(() -> ENGINE.recommend(null))
                .isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage("context is required");
    }

    @Test
    void decimalRatingsAreComparedExactlyWithoutFloatingPointConversion() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 400, "10000000000000000.1"),
                candidate(2, 300, "10000000000000000.2"),
                candidate(3, 200, "10000000000000000.3"),
                candidate(4, 100, "10000000000000000.4")
        ));

        assertTeam(
                recommendation.teamA(),
                uuid(1),
                uuid(4),
                "20000000000000000.5"
        );
        assertTeam(
                recommendation.teamB(),
                uuid(2),
                uuid(3),
                "20000000000000000.5"
        );
        assertThat(recommendation.ratingDifference())
                .isEqualByComparingTo("0.0");
    }

    @Test
    void identicalContentInDifferentIterationOrdersIsFullyDeterministic() {
        List<MatchmakingCandidate> candidates = new ArrayList<>(List.of(
                candidate(1, 600, "31.000000001"),
                candidate(2, 500, "29.000000002"),
                candidate(3, 400, "23.000000003"),
                candidate(4, 300, "21.000000004"),
                candidate(5, 200, "19.000000005"),
                candidate(6, 100, "17.000000006")
        ));
        MatchRecommendation original = recommend(candidates);
        Collections.reverse(candidates);
        MatchRecommendation reversed = recommend(candidates);

        assertThat(reversed).isEqualTo(original);
    }

    @Test
    void thirtyCandidateRunIsDeterministicAndReturnsRecommendation() {
        List<MatchmakingCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            candidates.add(candidate(
                    index,
                    1_000L - index,
                    "%d.%09d".formatted(15 + index % 11, index)
            ));
        }

        MatchRecommendation first = recommend(candidates);
        Collections.rotate(candidates, 11);
        MatchRecommendation rotated = recommend(candidates);

        assertThat(first).isEqualTo(rotated);
        assertThat(first.eligiblePlayerCount()).isEqualTo(30);
        assertThat(selectedPlayerIds(first)).hasSize(4);
    }

    @Test
    void waitingSecondsUsesOneExplicitEvaluationTime() {
        MatchRecommendation recommendation = recommend(List.of(
                candidate(1, 444, "10"),
                candidate(2, 333, "20"),
                candidate(3, 222, "30"),
                candidate(4, 111, "40")
        ));

        assertThat(players(recommendation))
                .extracting(RecommendedPlayer::waitingSeconds)
                .containsExactlyInAnyOrder(444L, 333L, 222L, 111L);
        assertThat(recommendation.evaluationTime()).isEqualTo(EVALUATION_TIME);
    }

    @Test
    void contextDefensivelyCopiesExternallyMutableCandidateCollection() {
        List<MatchmakingCandidate> mutable = new ArrayList<>(List.of(
                candidate(1, 400, "10"),
                candidate(2, 300, "20"),
                candidate(3, 200, "30"),
                candidate(4, 100, "40")
        ));
        MatchmakingContext context = context(mutable);
        mutable.clear();

        assertThat(context.candidates()).hasSize(4);
        assertThatThrownBy(() -> context.candidates().add(
                candidate(5, 50, "25")
        )).isInstanceOf(UnsupportedOperationException.class);
        assertThat(ENGINE.recommend(context))
                .isInstanceOf(MatchRecommendation.class);
    }

    private static MatchRecommendation recommend(
            List<MatchmakingCandidate> candidates
    ) {
        MatchmakingResult result = ENGINE.recommend(context(candidates));
        assertThat(result).isInstanceOf(MatchRecommendation.class);
        return (MatchRecommendation) result;
    }

    private static MatchmakingContext context(
            List<MatchmakingCandidate> candidates
    ) {
        return new MatchmakingContext(
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                EVALUATION_TIME,
                candidates
        );
    }

    private static MatchmakingCandidate candidate(
            int id,
            long waitingSeconds,
            String ratingValue
    ) {
        return candidate(
                id,
                waitingSeconds,
                ratingValue,
                "8.333333333",
                0,
                RatingBasis.PERSISTED
        );
    }

    private static MatchmakingCandidate candidate(
            int id,
            long waitingSeconds,
            String ratingValue,
            String uncertainty,
            int ratedMatches,
            RatingBasis ratingBasis
    ) {
        return new MatchmakingCandidate(
                uuid(1000 + id),
                uuid(id),
                EVALUATION_TIME.minusSeconds(waitingSeconds),
                new BigDecimal(ratingValue),
                new BigDecimal(uncertainty),
                ratedMatches,
                ratingBasis
        );
    }

    private static void assertTeam(
            RecommendedTeam team,
            UUID slot1,
            UUID slot2,
            String ratingTotal
    ) {
        assertThat(team.slot1().playerId()).isEqualTo(slot1);
        assertThat(team.slot1().teamSlot()).isEqualTo(1);
        assertThat(team.slot2().playerId()).isEqualTo(slot2);
        assertThat(team.slot2().teamSlot()).isEqualTo(2);
        assertThat(team.ratingTotal()).isEqualByComparingTo(ratingTotal);
    }

    private static List<RecommendedPlayer> players(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        );
    }

    private static List<UUID> selectedPlayerIds(
            MatchRecommendation recommendation
    ) {
        return players(recommendation).stream()
                .map(RecommendedPlayer::playerId)
                .toList();
    }

    private static void assertSameScoredComposition(
            MatchRecommendation first,
            MatchRecommendation second
    ) {
        assertThat(selectedPlayerIds(second))
                .containsExactlyElementsOf(selectedPlayerIds(first));
        assertThat(second.teamARatingTotal())
                .isEqualByComparingTo(first.teamARatingTotal());
        assertThat(second.teamBRatingTotal())
                .isEqualByComparingTo(first.teamBRatingTotal());
        assertThat(second.ratingDifference())
                .isEqualByComparingTo(first.ratingDifference());
    }

    private static void assertMissingCandidateValue(
            MatchmakingCandidateValues values,
            String message
    ) {
        assertThatThrownBy(() -> new MatchmakingCandidate(
                values.sessionParticipantId(),
                values.playerId(),
                values.waitingSince(),
                values.ratingValue(),
                values.uncertainty(),
                0,
                values.ratingBasis()
        )).isInstanceOf(InvalidMatchmakingInputException.class)
                .hasMessage(message);
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }

    private record MatchmakingCandidateValues(
            UUID sessionParticipantId,
            UUID playerId,
            Instant waitingSince,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            RatingBasis ratingBasis
    ) {
    }
}

package com.sportssession.platform.matchmaking.domain;

import com.sportssession.platform.player.domain.SkillLevel;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MatchmakingBuddyPairTest {

    private static final UUID SESSION_ID = uuid(9_000);
    private static final UUID SESSION_COURT_ID = uuid(9_001);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-12T09:00:00Z");
    private static final MatchmakingEngine ENGINE = new MatchmakingEngine();

    @Test
    void oldestAnchorPullsBuddyIntoRecommendationAndSameTeam() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 600, "100", 1),
                        buddyCandidate(2, 100, "10", 1),
                        candidate(3, 500, "20"),
                        candidate(4, 400, "30"),
                        candidate(5, 300, "40"),
                        candidate(6, 200, "50")
                ),
                emptyHistory()
        );

        assertThat(selected(recommendation))
                .contains(participantId(1), participantId(2));
        assertThat(sameTeam(
                recommendation,
                participantId(1),
                participantId(2)
        )).isTrue();
    }

    @Test
    void recommendationNeverSelectsOnlyOneMemberOfEligibleBuddyPair() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        candidate(1, 600, "25"),
                        candidate(2, 500, "25"),
                        candidate(3, 400, "25"),
                        candidate(4, 300, "25"),
                        buddyCandidate(5, 200, "25", 2),
                        buddyCandidate(6, 100, "25", 2)
                ),
                emptyHistory()
        );

        boolean selectedFirst = selected(recommendation)
                .contains(participantId(5));
        boolean selectedSecond = selected(recommendation)
                .contains(participantId(6));
        assertThat(selectedFirst).isEqualTo(selectedSecond);
    }

    @Test
    void twoBuddyPairsFormTheTwoCompleteTeams() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 400, "10", 1),
                        buddyCandidate(2, 300, "20", 1),
                        buddyCandidate(3, 200, "30", 2),
                        buddyCandidate(4, 100, "40", 2)
                ),
                emptyHistory()
        );

        assertThat(sameTeam(
                recommendation,
                participantId(1),
                participantId(2)
        )).isTrue();
        assertThat(sameTeam(
                recommendation,
                participantId(3),
                participantId(4)
        )).isTrue();
    }

    @Test
    void buddyConstraintOverridesBetterRatingPartition() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 400, "10", 1),
                        buddyCandidate(2, 300, "10", 1),
                        candidate(3, 200, "20"),
                        candidate(4, 100, "40")
                ),
                emptyHistory()
        );

        assertThat(sameTeam(
                recommendation,
                participantId(1),
                participantId(2)
        )).isTrue();
        assertThat(recommendation.ratingDifference())
                .isEqualByComparingTo("40");
    }

    @Test
    void buddyRecommendationIsDeterministicAcrossCandidateInputOrder() {
        List<MatchmakingCandidate> candidates = new ArrayList<>(List.of(
                buddyCandidate(1, 600, "25", 1),
                buddyCandidate(2, 100, "25", 1),
                candidate(3, 500, "25"),
                candidate(4, 400, "25"),
                candidate(5, 300, "25"),
                candidate(6, 200, "25")
        ));
        MatchRecommendation first = recommend(candidates, emptyHistory());
        Collections.reverse(candidates);

        assertThat(recommend(candidates, emptyHistory())).isEqualTo(first);
    }

    @Test
    void noBuddyPreservesPreviousSelectionSemantics() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        candidate(1, 400, "10"),
                        candidate(2, 300, "20"),
                        candidate(3, 200, "30"),
                        candidate(4, 100, "40")
                ),
                emptyHistory()
        );

        assertThat(sameTeam(
                recommendation,
                participantId(1),
                participantId(4)
        )).isTrue();
        assertThat(sameTeam(
                recommendation,
                participantId(2),
                participantId(3)
        )).isTrue();
    }

    @Test
    void buddyTeammateRepetitionIsExempt() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 400, "25", 1),
                        buddyCandidate(2, 300, "25", 1),
                        candidate(3, 200, "25"),
                        candidate(4, 100, "25")
                ),
                history(match(1, 100, 1, 2, 5, 6))
        );

        assertThat(recommendation.teammateRepeatCount()).isZero();
    }

    @Test
    void nonBuddyTeammateRepetitionStillCounts() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 400, "25", 1),
                        buddyCandidate(2, 300, "25", 1),
                        candidate(3, 200, "25"),
                        candidate(4, 100, "25")
                ),
                history(
                        match(1, 100, 1, 2, 5, 6),
                        match(2, 200, 3, 4, 5, 6)
                )
        );

        assertThat(recommendation.teammateRepeatCount()).isEqualTo(1);
    }

    @Test
    void buddyDoesNotExemptOpponentOrImmediateQuartetDiversity() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 400, "25", 1),
                        buddyCandidate(2, 300, "25", 1),
                        candidate(3, 200, "25"),
                        candidate(4, 100, "25")
                ),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(recommendation.immediateQuartetRepeat()).isTrue();
        assertThat(recommendation.teammateRepeatCount()).isEqualTo(1);
        assertThat(recommendation.opponentRepeatCount()).isEqualTo(4);
    }

    @Test
    void buddyPairRotatesToFreshOpponentsWhenFairnessIsTied() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        buddyCandidate(1, 600, "25", 1),
                        buddyCandidate(2, 500, "25", 1),
                        candidate(3, 400, "25"),
                        candidate(4, 300, "25"),
                        candidate(5, 200, "25"),
                        candidate(6, 100, "25")
                ),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selected(recommendation))
                .containsExactlyInAnyOrder(
                        participantId(1),
                        participantId(2),
                        participantId(5),
                        participantId(6)
                );
        assertThat(recommendation.immediateQuartetRepeat()).isFalse();
        assertThat(recommendation.opponentRepeatCount()).isZero();
    }

    private static MatchRecommendation recommend(
            List<MatchmakingCandidate> candidates,
            MatchmakingSessionPairingHistory history
    ) {
        MatchmakingResult result = ENGINE.recommend(new MatchmakingContext(
                SESSION_ID,
                SESSION_COURT_ID,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                EVALUATION_TIME,
                candidates,
                history
        ));
        assertThat(result).isInstanceOf(MatchRecommendation.class);
        return (MatchRecommendation) result;
    }

    private static MatchmakingCandidate candidate(
            int number,
            long waitingSeconds,
            String rating
    ) {
        return new MatchmakingCandidate(
                participantId(number),
                uuid(number),
                EVALUATION_TIME.minusSeconds(waitingSeconds),
                SkillLevel.INTERMEDIATE,
                0,
                new BigDecimal(rating),
                new BigDecimal("8.333333333"),
                0,
                RatingBasis.INITIAL_PRIOR,
                null
        );
    }

    private static MatchmakingCandidate buddyCandidate(
            int number,
            long waitingSeconds,
            String rating,
            int buddyPairNumber
    ) {
        MatchmakingCandidate candidate = candidate(
                number,
                waitingSeconds,
                rating
        );
        return new MatchmakingCandidate(
                candidate.sessionParticipantId(),
                candidate.playerId(),
                candidate.waitingSince(),
                candidate.skillLevel(),
                candidate.sessionMatchesPlayed(),
                candidate.ratingValue(),
                candidate.uncertainty(),
                candidate.ratedMatches(),
                candidate.ratingBasis(),
                uuid(8_000 + buddyPairNumber)
        );
    }

    private static MatchmakingSessionPairingHistory emptyHistory() {
        return MatchmakingSessionPairingHistory.empty(SESSION_ID);
    }

    private static MatchmakingSessionPairingHistory history(
            CompletedMatchPairing... matches
    ) {
        return new MatchmakingSessionPairingHistory(
                SESSION_ID,
                List.of(matches)
        );
    }

    private static CompletedMatchPairing match(
            int matchNumber,
            long completedSeconds,
            int teamAFirst,
            int teamASecond,
            int teamBFirst,
            int teamBSecond
    ) {
        return new CompletedMatchPairing(
                uuid(7_000 + matchNumber),
                EVALUATION_TIME.minusSeconds(completedSeconds),
                List.of(
                        participantId(teamAFirst),
                        participantId(teamASecond)
                ),
                List.of(
                        participantId(teamBFirst),
                        participantId(teamBSecond)
                )
        );
    }

    private static Set<UUID> selected(MatchRecommendation recommendation) {
        return List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        ).stream()
                .map(RecommendedPlayer::sessionParticipantId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean sameTeam(
            MatchRecommendation recommendation,
            UUID first,
            UUID second
    ) {
        Set<UUID> teamA = Set.of(
                recommendation.teamA().slot1().sessionParticipantId(),
                recommendation.teamA().slot2().sessionParticipantId()
        );
        Set<UUID> teamB = Set.of(
                recommendation.teamB().slot1().sessionParticipantId(),
                recommendation.teamB().slot2().sessionParticipantId()
        );
        return teamA.containsAll(Set.of(first, second))
                || teamB.containsAll(Set.of(first, second));
    }

    private static UUID participantId(int number) {
        return uuid(1_000 + number);
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

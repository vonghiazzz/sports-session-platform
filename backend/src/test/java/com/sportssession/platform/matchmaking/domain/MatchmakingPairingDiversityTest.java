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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchmakingPairingDiversityTest {

    private static final UUID SESSION_ID = uuid(9000);
    private static final UUID SESSION_COURT_ID = uuid(9001);
    private static final Instant EVALUATION_TIME =
            Instant.parse("2026-09-12T08:00:00Z");
    private static final MatchmakingEngine ENGINE = new MatchmakingEngine();

    @Test
    void avoidsImmediateSameQuartetWhenEquivalentAlternativeExists() {
        MatchRecommendation recommendation = recommend(
                candidates(5),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selectedParticipantIds(recommendation))
                .contains(participantId(1), participantId(5));
        assertThat(recommendation.immediateQuartetRepeat()).isFalse();
    }

    @Test
    void reshuffledTeamsStillReportImmediateQuartetRepeat() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        candidate(1, 600, SkillLevel.INTERMEDIATE, 0, "10"),
                        candidate(2, 500, SkillLevel.INTERMEDIATE, 0, "20"),
                        candidate(3, 400, SkillLevel.INTERMEDIATE, 0, "30"),
                        candidate(4, 300, SkillLevel.INTERMEDIATE, 0, "40")
                ),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(recommendation.immediateQuartetRepeat()).isTrue();
        assertThat(sameTeam(recommendation, participantId(1), participantId(2)))
                .isFalse();
    }

    @Test
    void minimizesTeammateRepetitionBeforeOpponentRepetition() {
        MatchRecommendation recommendation = recommend(
                candidates(4),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(recommendation.teammateRepeatCount()).isZero();
        assertThat(sameTeam(recommendation, participantId(1), participantId(2)))
                .isFalse();
        assertThat(sameTeam(recommendation, participantId(3), participantId(4)))
                .isFalse();
    }

    @Test
    void minimizesOpponentRepetitionWhenTeammateCountsTie() {
        MatchmakingSessionPairingHistory history = history(
                match(1, 100, 1, 5, 3, 6),
                match(2, 200, 2, 5, 4, 6)
        );

        MatchRecommendation recommendation = recommend(
                candidates(4),
                history
        );

        assertThat(recommendation.teammateRepeatCount()).isZero();
        assertThat(recommendation.opponentRepeatCount()).isZero();
        assertThat(sameTeam(recommendation, participantId(1), participantId(3)))
                .isTrue();
        assertThat(sameTeam(recommendation, participantId(2), participantId(4)))
                .isTrue();
    }

    @Test
    void teammateRepeatCountIsComparedBeforeOpponentRepeatCount() {
        MatchRecommendation recommendation = recommend(
                candidates(4),
                history(
                        match(1, 100, 1, 5, 3, 6),
                        match(2, 200, 2, 5, 4, 6),
                        match(3, 300, 1, 3, 5, 6)
                )
        );

        assertThat(recommendation.teammateRepeatCount()).isZero();
        assertThat(recommendation.opponentRepeatCount()).isEqualTo(2);
        assertThat(sameTeam(recommendation, participantId(1), participantId(3)))
                .isFalse();
    }

    @Test
    void diversityDoesNotOutrankSkillLevelSpread() {
        List<MatchmakingCandidate> candidates = new ArrayList<>(candidates(4));
        candidates.add(candidate(5, 200, SkillLevel.WEAK, 0, "25"));

        MatchRecommendation recommendation = recommend(
                candidates,
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selectedParticipantIds(recommendation))
                .containsExactlyInAnyOrder(
                        participantId(1),
                        participantId(2),
                        participantId(3),
                        participantId(4)
                );
        assertThat(recommendation.immediateQuartetRepeat()).isTrue();
    }

    @Test
    void diversityDoesNotOutrankSessionMatchCountFairness() {
        List<MatchmakingCandidate> candidates = new ArrayList<>(candidates(4));
        candidates.add(candidate(
                5,
                200,
                SkillLevel.INTERMEDIATE,
                1,
                "25"
        ));

        MatchRecommendation recommendation = recommend(
                candidates,
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selectedParticipantIds(recommendation))
                .containsExactlyInAnyOrder(
                        participantId(1),
                        participantId(2),
                        participantId(3),
                        participantId(4)
                );
        assertThat(recommendation.immediateQuartetRepeat()).isTrue();
    }

    @Test
    void diversityOutranksTeamRatingDifference() {
        List<MatchmakingCandidate> candidates = List.of(
                candidate(1, 600, SkillLevel.INTERMEDIATE, 0, "10"),
                candidate(2, 500, SkillLevel.INTERMEDIATE, 0, "20"),
                candidate(3, 400, SkillLevel.INTERMEDIATE, 0, "30"),
                candidate(4, 300, SkillLevel.INTERMEDIATE, 0, "40"),
                candidate(5, 200, SkillLevel.INTERMEDIATE, 0, "100")
        );

        MatchRecommendation recommendation = recommend(
                candidates,
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selectedParticipantIds(recommendation))
                .contains(participantId(5));
        assertThat(recommendation.immediateQuartetRepeat()).isFalse();
        assertThat(recommendation.ratingDifference()).isGreaterThan(
                BigDecimal.ZERO
        );
    }

    @Test
    void onlyFourPreviouslyPairedPlayersRemainPlayable() {
        MatchRecommendation recommendation = recommend(
                candidates(4),
                history(match(1, 100, 1, 2, 3, 4))
        );

        assertThat(selectedParticipantIds(recommendation)).hasSize(4);
        assertThat(recommendation.immediateQuartetRepeat()).isTrue();
        assertThat(recommendation.opponentRepeatCount()).isPositive();
    }

    @Test
    void emptyHistoryPreservesPreviousSelectionSemantics() {
        MatchRecommendation recommendation = recommend(
                List.of(
                        candidate(1, 400, SkillLevel.INTERMEDIATE, 0, "10"),
                        candidate(2, 300, SkillLevel.INTERMEDIATE, 0, "20"),
                        candidate(3, 200, SkillLevel.INTERMEDIATE, 0, "30"),
                        candidate(4, 100, SkillLevel.INTERMEDIATE, 0, "40")
                ),
                MatchmakingSessionPairingHistory.empty(SESSION_ID)
        );

        assertThat(sameTeam(recommendation, participantId(1), participantId(4)))
                .isTrue();
        assertThat(sameTeam(recommendation, participantId(2), participantId(3)))
                .isTrue();
        assertThat(recommendation.immediateQuartetRepeat()).isFalse();
        assertThat(recommendation.teammateRepeatCount()).isZero();
        assertThat(recommendation.opponentRepeatCount()).isZero();
    }

    @Test
    void sameCandidatesAndHistoryRemainDeterministicAcrossInputOrder() {
        List<MatchmakingCandidate> candidates = new ArrayList<>(candidates(6));
        MatchmakingSessionPairingHistory history = history(
                match(1, 100, 1, 2, 3, 4),
                match(2, 200, 1, 3, 5, 6)
        );
        MatchRecommendation first = recommend(candidates, history);
        Collections.reverse(candidates);

        assertThat(recommend(candidates, history)).isEqualTo(first);
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

    private static List<MatchmakingCandidate> candidates(int count) {
        List<MatchmakingCandidate> candidates = new ArrayList<>();
        for (int number = 1; number <= count; number++) {
            candidates.add(candidate(
                    number,
                    700L - number * 100L,
                    SkillLevel.INTERMEDIATE,
                    0,
                    "25"
            ));
        }
        return List.copyOf(candidates);
    }

    private static MatchmakingCandidate candidate(
            int number,
            long waitingSeconds,
            SkillLevel skillLevel,
            int sessionMatchesPlayed,
            String rating
    ) {
        return new MatchmakingCandidate(
                participantId(number),
                uuid(number),
                EVALUATION_TIME.minusSeconds(waitingSeconds),
                skillLevel,
                sessionMatchesPlayed,
                new BigDecimal(rating),
                new BigDecimal("8.333333333"),
                0,
                RatingBasis.INITIAL_PRIOR
        );
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
                uuid(8000 + matchNumber),
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

    private static boolean sameTeam(
            MatchRecommendation recommendation,
            UUID first,
            UUID second
    ) {
        List<UUID> teamA = List.of(
                recommendation.teamA().slot1().sessionParticipantId(),
                recommendation.teamA().slot2().sessionParticipantId()
        );
        List<UUID> teamB = List.of(
                recommendation.teamB().slot1().sessionParticipantId(),
                recommendation.teamB().slot2().sessionParticipantId()
        );
        return teamA.containsAll(List.of(first, second))
                || teamB.containsAll(List.of(first, second));
    }

    private static List<UUID> selectedParticipantIds(
            MatchRecommendation recommendation
    ) {
        return List.of(
                recommendation.teamA().slot1().sessionParticipantId(),
                recommendation.teamA().slot2().sessionParticipantId(),
                recommendation.teamB().slot1().sessionParticipantId(),
                recommendation.teamB().slot2().sessionParticipantId()
        );
    }

    private static UUID participantId(int value) {
        return uuid(1000 + value);
    }

    private static UUID uuid(int value) {
        return UUID.fromString(
                "00000000-0000-0000-0000-%012x".formatted(value)
        );
    }
}

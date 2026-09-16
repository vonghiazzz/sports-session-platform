package com.sportssession.platform.rating.application;

import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletedMatchRatingContextTest {

    private final UUID playerA1 = UUID.randomUUID();
    private final UUID playerA2 = UUID.randomUUID();
    private final UUID playerB1 = UUID.randomUUID();
    private final UUID playerB2 = UUID.randomUUID();

    @Test
    void acceptsValidDoublesEvidenceAndDefensivelyCopiesTeams() {
        List<RatingParticipantEvidence> teamA = validTeamA();
        List<RatingParticipantEvidence> teamB = validTeamB();

        CompletedMatchRatingContext context = context(
                1,
                Instant.parse("2026-08-27T10:00:00Z"),
                TeamSide.A,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                teamA,
                teamB
        );

        assertThat(context.teamA()).containsExactlyElementsOf(teamA);
        assertThat(context.teamB()).containsExactlyElementsOf(teamB);
        assertThatThrownBy(() -> context.teamA().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicatePlayerIdAcrossTeams() {
        List<RatingParticipantEvidence> teamB = List.of(
                participant(playerB1, TeamSide.B, 1),
                participant(playerA1, TeamSide.B, 2)
        );

        assertInvalid(validTeamA(), teamB)
                .hasMessageContaining("distinct playerIds");
    }

    @Test
    void rejectsDuplicateTeamSlot() {
        List<RatingParticipantEvidence> teamA = List.of(
                participant(playerA1, TeamSide.A, 1),
                participant(playerA2, TeamSide.A, 1)
        );

        assertInvalid(teamA, validTeamB())
                .hasMessageContaining("slots 1 and 2");
    }

    @Test
    void rejectsWrongTeamSize() {
        assertInvalid(validTeamA().subList(0, 1), validTeamB())
                .hasMessageContaining("exactly two participants");
    }

    @Test
    void rejectsResultVersionZero() {
        assertThatThrownBy(() -> context(
                0,
                Instant.now(),
                TeamSide.A,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                validTeamA(),
                validTeamB()
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class)
                .hasMessageContaining("resultVersion");
    }

    @Test
    void rejectsNullCompletedAt() {
        assertThatThrownBy(() -> context(
                1,
                null,
                TeamSide.A,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                validTeamA(),
                validTeamB()
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class)
                .hasMessageContaining("completedAt");
    }

    @Test
    void rejectsNullWinner() {
        assertThatThrownBy(() -> context(
                1,
                Instant.now(),
                null,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                validTeamA(),
                validTeamB()
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class)
                .hasMessageContaining("winnerTeam");
    }

    @Test
    void rejectsNullSport() {
        assertThatThrownBy(() -> context(
                1,
                Instant.now(),
                TeamSide.A,
                null,
                MatchFormat.DOUBLES,
                validTeamA(),
                validTeamB()
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class)
                .hasMessageContaining("sportCode");
    }

    @Test
    void rejectsNullFormat() {
        assertThatThrownBy(() -> context(
                1,
                Instant.now(),
                TeamSide.A,
                SportCode.BADMINTON,
                null,
                validTeamA(),
                validTeamB()
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class)
                .hasMessageContaining("matchFormat");
    }

    private org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable>
    assertInvalid(
            List<RatingParticipantEvidence> teamA,
            List<RatingParticipantEvidence> teamB
    ) {
        return assertThatThrownBy(() -> context(
                1,
                Instant.now(),
                TeamSide.A,
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                teamA,
                teamB
        )).isInstanceOf(InvalidCompletedMatchRatingEvidenceException.class);
    }

    private CompletedMatchRatingContext context(
            int resultVersion,
            Instant completedAt,
            TeamSide winnerTeam,
            SportCode sportCode,
            MatchFormat matchFormat,
            List<RatingParticipantEvidence> teamA,
            List<RatingParticipantEvidence> teamB
    ) {
        return new CompletedMatchRatingContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                resultVersion,
                completedAt,
                MatchSource.MANUAL,
                sportCode,
                matchFormat,
                winnerTeam,
                21,
                18,
                teamA,
                teamB
        );
    }

    private List<RatingParticipantEvidence> validTeamA() {
        return List.of(
                participant(playerA1, TeamSide.A, 1),
                participant(playerA2, TeamSide.A, 2)
        );
    }

    private List<RatingParticipantEvidence> validTeamB() {
        return List.of(
                participant(playerB1, TeamSide.B, 1),
                participant(playerB2, TeamSide.B, 2)
        );
    }

    private RatingParticipantEvidence participant(
            UUID playerId,
            TeamSide teamSide,
            int slot
    ) {
        return new RatingParticipantEvidence(playerId, teamSide, slot);
    }
}

package com.sportssession.platform.match.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private static final Instant CREATED_AT =
            Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void createdStateIsValid() {
        Match match = newMatch();

        assertThat(match.status()).isEqualTo(MatchStatus.CREATED);
        assertThat(match.source()).isEqualTo(MatchSource.MANUAL);
        assertThat(match.result()).isNull();
        assertThat(match.resultVersion()).isZero();
        assertThat(match.startedAt()).isNull();
        assertThat(match.completedAt()).isNull();
        assertThat(match.cancelledAt()).isNull();
    }

    @Test
    void createdMatchCanStartWithoutCreatingAResult() {
        Instant startedAt = CREATED_AT.plusSeconds(60);

        Match started = newMatch().start(startedAt);

        assertThat(started.status()).isEqualTo(MatchStatus.PLAYING);
        assertThat(started.startedAt()).isEqualTo(startedAt);
        assertThat(started.completedAt()).isNull();
        assertThat(started.cancelledAt()).isNull();
        assertThat(started.result()).isNull();
        assertThat(started.resultVersion()).isZero();
    }

    @Test
    void matchCannotStartFromAnyNonCreatedStatus() {
        Match playing = newMatch().start(CREATED_AT.plusSeconds(60));
        Match completed = playing.complete(
                MatchResult.winnerOnly(TeamSide.A),
                CREATED_AT.plusSeconds(120)
        );
        Match cancelled = newMatch().cancel(CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> playing.start(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot start from status PLAYING");
        assertThatThrownBy(() -> completed.start(CREATED_AT.plusSeconds(180)))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot start from status COMPLETED");
        assertThatThrownBy(() -> cancelled.start(CREATED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot start from status CANCELLED");
    }

    @Test
    void completionRequiresAStartedMatch() {
        Match match = newMatch();

        assertThatThrownBy(() -> match.complete(
                MatchResult.winnerOnly(TeamSide.A),
                CREATED_AT.plusSeconds(300)
        ))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot complete from status CREATED");
    }

    @Test
    void completedMatchRequiresWinner() {
        Match playing = newMatch().start(CREATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> playing.complete(
                null,
                CREATED_AT.plusSeconds(300)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Completed Match requires a result");
    }

    @Test
    void suppliedScoreMustAgreeWithWinner() {
        assertThatThrownBy(() -> MatchResult.withScore(TeamSide.A, 10, 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Team A score must exceed Team B score when Team A wins"
                );
    }

    @Test
    void cancellationTimestampCannotPrecedeStartedAt() {
        Instant startedAt = CREATED_AT.plusSeconds(60);
        Match playing = newMatch().start(startedAt);

        assertThatThrownBy(() -> playing.cancel(startedAt.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be before startedAt");
    }

    @Test
    void completionTimestampCannotPrecedeStartedAt() {
        Instant startedAt = CREATED_AT.plusSeconds(60);
        Match playing = newMatch().start(startedAt);

        assertThatThrownBy(() -> playing.complete(
                MatchResult.winnerOnly(TeamSide.B),
                startedAt.minusSeconds(1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedAt must not be before startedAt");
    }

    @Test
    void cancelledMatchContainsNoResult() {
        Match cancelled = newMatch().cancel(CREATED_AT.plusSeconds(60));

        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(cancelled.result()).isNull();
        assertThat(cancelled.resultVersion()).isZero();
        assertThat(cancelled.cancelledAt())
                .isEqualTo(CREATED_AT.plusSeconds(60));
    }

    @Test
    void startedMatchCanCompleteWithWinnerAndOptionalScore() {
        MatchResult result = MatchResult.withScore(TeamSide.B, 18, 21);

        Match completed = newMatch()
                .start(CREATED_AT.plusSeconds(60))
                .complete(result, CREATED_AT.plusSeconds(600));

        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.result()).isEqualTo(result);
        assertThat(completed.resultVersion()).isEqualTo(1);
    }

    @Test
    void winnerOnlyCompletionSetsFirstResultVersion() {
        MatchResult result = MatchResult.winnerOnly(TeamSide.A);

        Match completed = newMatch()
                .start(CREATED_AT.plusSeconds(60))
                .complete(result, CREATED_AT.plusSeconds(600));

        assertThat(completed.status()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(completed.result()).isEqualTo(result);
        assertThat(completed.resultVersion()).isEqualTo(1);
        assertThat(completed.cancelledAt()).isNull();
    }

    @Test
    void completedOrCancelledMatchCannotComplete() {
        Match playing = newMatch().start(CREATED_AT.plusSeconds(60));
        Match completed = playing.complete(
                MatchResult.winnerOnly(TeamSide.A),
                CREATED_AT.plusSeconds(120)
        );
        Match cancelled = playing.cancel(CREATED_AT.plusSeconds(120));

        assertThatThrownBy(() -> completed.complete(
                MatchResult.winnerOnly(TeamSide.B),
                CREATED_AT.plusSeconds(180)
        ))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot complete from status COMPLETED");
        assertThatThrownBy(() -> cancelled.complete(
                MatchResult.winnerOnly(TeamSide.B),
                CREATED_AT.plusSeconds(180)
        ))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot complete from status CANCELLED");
    }

    @Test
    void playingMatchCancellationPreservesStartedAtAndHasNoResult() {
        Instant startedAt = CREATED_AT.plusSeconds(60);

        Match cancelled = newMatch()
                .start(startedAt)
                .cancel(CREATED_AT.plusSeconds(120));

        assertThat(cancelled.status()).isEqualTo(MatchStatus.CANCELLED);
        assertThat(cancelled.startedAt()).isEqualTo(startedAt);
        assertThat(cancelled.completedAt()).isNull();
        assertThat(cancelled.result()).isNull();
        assertThat(cancelled.resultVersion()).isZero();
    }

    @Test
    void completedOrCancelledMatchCannotCancel() {
        Match playing = newMatch().start(CREATED_AT.plusSeconds(60));
        Match completed = playing.complete(
                MatchResult.winnerOnly(TeamSide.A),
                CREATED_AT.plusSeconds(120)
        );
        Match cancelled = playing.cancel(CREATED_AT.plusSeconds(120));

        assertThatThrownBy(() -> completed.cancel(CREATED_AT.plusSeconds(180)))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot cancel from status COMPLETED");
        assertThatThrownBy(() -> cancelled.cancel(CREATED_AT.plusSeconds(180)))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessage("Match cannot cancel from status CANCELLED");
    }

    private Match newMatch() {
        return Match.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                MatchSource.MANUAL,
                CREATED_AT
        );
    }
}

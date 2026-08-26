package com.sportssession.platform.session.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionParticipantTest {

    private static final Instant JOINED_AT = Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void leavingWhilePausedAddsCurrentPauseDuration() {
        Instant checkedInAt = JOINED_AT.plusSeconds(60);
        Instant pausedAt = checkedInAt.plusSeconds(120);
        Instant leftAt = pausedAt.plusSeconds(900);

        SessionParticipant participant = SessionParticipant
                .register(UUID.randomUUID(), UUID.randomUUID(), JOINED_AT)
                .checkIn(checkedInAt)
                .pause(pausedAt)
                .leave(leftAt);

        assertThat(participant.status()).isEqualTo(ParticipantStatus.LEFT);
        assertThat(participant.totalPausedSeconds()).isEqualTo(900);
        assertThat(participant.pausedAt()).isNull();
        assertThat(participant.leftAt()).isEqualTo(leftAt);
    }

    @Test
    void resumeAddsPauseDurationAndResetsWaitingSince() {
        Instant checkedInAt = JOINED_AT.plusSeconds(60);
        Instant pausedAt = checkedInAt.plusSeconds(120);
        Instant resumedAt = pausedAt.plusSeconds(300);

        SessionParticipant participant = SessionParticipant
                .register(UUID.randomUUID(), UUID.randomUUID(), JOINED_AT)
                .checkIn(checkedInAt)
                .pause(pausedAt)
                .resume(resumedAt);

        assertThat(participant.status()).isEqualTo(ParticipantStatus.WAITING);
        assertThat(participant.totalPausedSeconds()).isEqualTo(300);
        assertThat(participant.waitingSince()).isEqualTo(resumedAt);
        assertThat(participant.pausedAt()).isNull();
    }

    @Test
    void playingParticipantCannotLeave() {
        Instant checkedInAt = JOINED_AT.plusSeconds(60);
        SessionParticipant participant = new SessionParticipant(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ParticipantStatus.PLAYING,
                JOINED_AT,
                checkedInAt,
                null,
                null,
                0,
                null,
                0,
                JOINED_AT,
                checkedInAt
        );

        assertThatThrownBy(() -> participant.leave(checkedInAt.plusSeconds(60)))
                .isInstanceOf(InvalidParticipantStateException.class)
                .hasMessage("Participant cannot leave from status PLAYING");
    }

    @Test
    void waitingParticipantCanStartMatch() {
        Instant checkedInAt = JOINED_AT.plusSeconds(60);
        Instant startedAt = checkedInAt.plusSeconds(120);
        SessionParticipant waiting = SessionParticipant
                .register(UUID.randomUUID(), UUID.randomUUID(), JOINED_AT)
                .checkIn(checkedInAt);

        SessionParticipant playing = waiting.startMatch(startedAt);

        assertThat(playing.status()).isEqualTo(ParticipantStatus.PLAYING);
        assertThat(playing.checkedInAt()).isEqualTo(checkedInAt);
        assertThat(playing.waitingSince()).isNull();
        assertThat(playing.pausedAt()).isNull();
        assertThat(playing.leftAt()).isNull();
        assertThat(playing.updatedAt()).isEqualTo(startedAt);
    }

    @Test
    void nonWaitingParticipantCannotStartMatch() {
        SessionParticipant registered = SessionParticipant.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                JOINED_AT
        );
        SessionParticipant waiting = registered.checkIn(JOINED_AT.plusSeconds(60));
        SessionParticipant paused = waiting.pause(JOINED_AT.plusSeconds(120));
        SessionParticipant playing = waiting.startMatch(JOINED_AT.plusSeconds(120));
        SessionParticipant left = waiting.leave(JOINED_AT.plusSeconds(120));

        assertCannotStartMatch(registered, "REGISTERED");
        assertCannotStartMatch(paused, "PAUSED");
        assertCannotStartMatch(playing, "PLAYING");
        assertCannotStartMatch(left, "LEFT");
    }

    @Test
    void playingParticipantCanReleaseFromMatch() {
        Instant checkedInAt = JOINED_AT.plusSeconds(60);
        Instant releasedAt = checkedInAt.plusSeconds(300);
        SessionParticipant playing = SessionParticipant
                .register(UUID.randomUUID(), UUID.randomUUID(), JOINED_AT)
                .checkIn(checkedInAt)
                .startMatch(checkedInAt.plusSeconds(60));

        SessionParticipant waiting = playing.releaseFromMatch(releasedAt);

        assertThat(waiting.status()).isEqualTo(ParticipantStatus.WAITING);
        assertThat(waiting.checkedInAt()).isEqualTo(checkedInAt);
        assertThat(waiting.waitingSince()).isEqualTo(releasedAt);
        assertThat(waiting.pausedAt()).isNull();
        assertThat(waiting.leftAt()).isNull();
        assertThat(waiting.updatedAt()).isEqualTo(releasedAt);
    }

    @Test
    void onlyPlayingParticipantCanReleaseFromMatch() {
        SessionParticipant registered = SessionParticipant.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                JOINED_AT
        );
        SessionParticipant waiting = registered.checkIn(JOINED_AT.plusSeconds(60));
        SessionParticipant paused = waiting.pause(JOINED_AT.plusSeconds(120));
        SessionParticipant left = waiting.leave(JOINED_AT.plusSeconds(120));

        assertCannotReleaseFromMatch(registered, "REGISTERED");
        assertCannotReleaseFromMatch(waiting, "WAITING");
        assertCannotReleaseFromMatch(paused, "PAUSED");
        assertCannotReleaseFromMatch(left, "LEFT");
    }

    private void assertCannotStartMatch(
            SessionParticipant participant,
            String status
    ) {
        assertThatThrownBy(() -> participant.startMatch(
                participant.updatedAt().plusSeconds(1)
        ))
                .isInstanceOf(InvalidParticipantStateException.class)
                .hasMessage("Participant cannot start a Match from status " + status);
    }

    private void assertCannotReleaseFromMatch(
            SessionParticipant participant,
            String status
    ) {
        assertThatThrownBy(() -> participant.releaseFromMatch(
                participant.updatedAt().plusSeconds(1)
        ))
                .isInstanceOf(InvalidParticipantStateException.class)
                .hasMessage(
                        "Participant cannot release from a Match from status "
                                + status
                );
    }
}

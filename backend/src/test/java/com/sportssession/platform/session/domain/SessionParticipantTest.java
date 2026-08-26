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
}

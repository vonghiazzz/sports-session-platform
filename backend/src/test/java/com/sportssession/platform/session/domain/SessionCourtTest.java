package com.sportssession.platform.session.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCourtTest {

    private static final Instant ALLOCATED_AT =
            Instant.parse("2026-08-26T10:00:00Z");

    @Test
    void availableCourtCanStartMatch() {
        SessionCourt available = newCourt();
        Instant startedAt = ALLOCATED_AT.plusSeconds(60);

        SessionCourt playing = available.startMatch(startedAt);

        assertThat(playing.status()).isEqualTo(SessionCourtStatus.PLAYING);
        assertThat(playing.updatedAt()).isEqualTo(startedAt);
    }

    @Test
    void playingOrUnavailableCourtCannotStartMatch() {
        SessionCourt available = newCourt();
        SessionCourt playing = available.startMatch(ALLOCATED_AT.plusSeconds(60));
        SessionCourt unavailable = available.disable(ALLOCATED_AT.plusSeconds(60));

        assertThatThrownBy(() -> playing.startMatch(ALLOCATED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidSessionCourtStateException.class)
                .hasMessage("Session Court cannot start a Match from status PLAYING");
        assertThatThrownBy(() -> unavailable.startMatch(ALLOCATED_AT.plusSeconds(120)))
                .isInstanceOf(InvalidSessionCourtStateException.class)
                .hasMessage("Session Court cannot start a Match from status UNAVAILABLE");
    }

    @Test
    void playingCourtCanReleaseFromMatch() {
        Instant releasedAt = ALLOCATED_AT.plusSeconds(120);
        SessionCourt playing = newCourt().startMatch(
                ALLOCATED_AT.plusSeconds(60)
        );

        SessionCourt available = playing.releaseFromMatch(releasedAt);

        assertThat(available.status()).isEqualTo(SessionCourtStatus.AVAILABLE);
        assertThat(available.updatedAt()).isEqualTo(releasedAt);
    }

    @Test
    void availableOrUnavailableCourtCannotReleaseFromMatch() {
        SessionCourt available = newCourt();
        SessionCourt unavailable = available.disable(
                ALLOCATED_AT.plusSeconds(60)
        );

        assertThatThrownBy(() -> available.releaseFromMatch(
                ALLOCATED_AT.plusSeconds(120)
        ))
                .isInstanceOf(InvalidSessionCourtStateException.class)
                .hasMessage(
                        "Session Court cannot release from a Match from status AVAILABLE"
                );
        assertThatThrownBy(() -> unavailable.releaseFromMatch(
                ALLOCATED_AT.plusSeconds(120)
        ))
                .isInstanceOf(InvalidSessionCourtStateException.class)
                .hasMessage(
                        "Session Court cannot release from a Match from status UNAVAILABLE"
                );
    }

    private SessionCourt newCourt() {
        return SessionCourt.allocate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ALLOCATED_AT
        );
    }
}

package com.sportssession.platform.session.domain;

import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    @Test
    void cancelledAtCannotBeBeforeStartedAt() {
        Instant createdAt = Instant.parse("2026-08-26T09:00:00Z");
        Session inProgress = Session.create(
                UUID.randomUUID(),
                "Morning Badminton",
                SportCode.BADMINTON,
                MatchFormat.DOUBLES,
                createdAt.plusSeconds(3600),
                createdAt.plusSeconds(7200),
                createdAt
        ).start(createdAt.plusSeconds(3600));

        assertThatThrownBy(() -> new Session(
                inProgress.id(),
                inProgress.venueId(),
                inProgress.title(),
                inProgress.sportCode(),
                inProgress.matchFormat(),
                inProgress.plannedStartAt(),
                inProgress.plannedEndAt(),
                SessionStatus.CANCELLED,
                inProgress.startedAt(),
                null,
                inProgress.startedAt().minusSeconds(1),
                inProgress.version(),
                inProgress.createdAt(),
                inProgress.updatedAt()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cancelledAt must not be before startedAt");
    }
}

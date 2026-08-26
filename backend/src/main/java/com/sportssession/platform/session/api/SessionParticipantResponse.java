package com.sportssession.platform.session.api;

import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionParticipant;

import java.time.Instant;
import java.util.UUID;

public record SessionParticipantResponse(
        UUID id,
        UUID sessionId,
        UUID playerId,
        ParticipantStatus status,
        Instant joinedAt,
        Instant checkedInAt,
        Instant waitingSince,
        Instant pausedAt,
        long totalPausedSeconds,
        Instant leftAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    static SessionParticipantResponse from(SessionParticipant participant) {
        return new SessionParticipantResponse(
                participant.id(),
                participant.sessionId(),
                participant.playerId(),
                participant.status(),
                participant.joinedAt(),
                participant.checkedInAt(),
                participant.waitingSince(),
                participant.pausedAt(),
                participant.totalPausedSeconds(),
                participant.leftAt(),
                participant.version(),
                participant.createdAt(),
                participant.updatedAt());
    }
}

package com.sportssession.platform.matchplan.domain;

import com.sportssession.platform.match.domain.MatchSource;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MatchPlan(
        UUID id,
        UUID sessionId,
        UUID sessionCourtId,
        MatchSource source,
        MatchPlanStatus status,
        Integer queuePosition,
        UUID startedMatchId,
        Instant startedAt,
        Instant cancelledAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public MatchPlan {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        validateState(
                status, queuePosition, startedMatchId, startedAt, cancelledAt
        );
    }

    public static MatchPlan queueManual(
            UUID sessionId,
            UUID sessionCourtId,
            int queuePosition,
            Instant now
    ) {
        return new MatchPlan(
                UUID.randomUUID(), sessionId, sessionCourtId,
                MatchSource.MANUAL, MatchPlanStatus.QUEUED,
                queuePosition, null, null, null, 0, now, now
        );
    }

    public MatchPlan move(UUID targetSessionCourtId, int targetPosition, Instant now) {
        requireQueued("move");
        return new MatchPlan(
                id, sessionId, targetSessionCourtId, source, status,
                targetPosition, null, null, null, version, createdAt, now
        );
    }

    public MatchPlan edit(Instant now) {
        requireQueued("edit");
        return new MatchPlan(
                id, sessionId, sessionCourtId, source, status,
                queuePosition, null, null, null, version, createdAt, now
        );
    }

    public MatchPlan reorder(int targetPosition, Instant now) {
        requireQueued("reorder");
        return new MatchPlan(
                id, sessionId, sessionCourtId, source, status,
                targetPosition, null, null, null, version, createdAt, now
        );
    }

    public MatchPlan start(UUID matchId, Instant now) {
        requireQueued("start");
        return new MatchPlan(
                id, sessionId, sessionCourtId, source, MatchPlanStatus.STARTED,
                null, matchId, now, null, version, createdAt, now
        );
    }

    public MatchPlan cancel(Instant now) {
        requireQueued("cancel");
        return new MatchPlan(
                id, sessionId, sessionCourtId, source, MatchPlanStatus.CANCELLED,
                null, null, null, now, version, createdAt, now
        );
    }

    private void requireQueued(String action) {
        if (status != MatchPlanStatus.QUEUED) {
            throw new MatchPlanConflictException(
                    "MatchPlan cannot " + action + " from status " + status
            );
        }
    }

    private static void validateState(
            MatchPlanStatus status,
            Integer queuePosition,
            UUID startedMatchId,
            Instant startedAt,
            Instant cancelledAt
    ) {
        boolean valid = switch (status) {
            case QUEUED -> queuePosition != null && queuePosition > 0
                    && startedMatchId == null && startedAt == null
                    && cancelledAt == null;
            case STARTED -> queuePosition == null && startedMatchId != null
                    && startedAt != null && cancelledAt == null;
            case CANCELLED -> queuePosition == null && startedMatchId == null
                    && startedAt == null && cancelledAt != null;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                    "MatchPlan state is inconsistent with status " + status
            );
        }
    }
}

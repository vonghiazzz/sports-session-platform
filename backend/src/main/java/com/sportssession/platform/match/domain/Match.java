package com.sportssession.platform.match.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Match(
        UUID id,
        UUID sessionId,
        UUID sessionCourtId,
        MatchStatus status,
        MatchSource source,
        MatchResult result,
        int resultVersion,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant updatedAt,
        long version
) {
    public Match {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (resultVersion < 0) {
            throw new IllegalArgumentException("resultVersion must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }

        validateState(status, result, resultVersion,
                startedAt, completedAt, cancelledAt);
    }

    public static Match create(
            UUID sessionId,
            UUID sessionCourtId,
            MatchSource source,
            Instant now
    ) {
        return new Match(
                UUID.randomUUID(),
                sessionId,
                sessionCourtId,
                MatchStatus.CREATED,
                source,
                null,
                0,
                now,
                null,
                null,
                null,
                now,
                0
        );
    }

    public Match start(Instant now) {
        requireStatus(MatchStatus.CREATED, "start");
        return copy(MatchStatus.PLAYING, null, 0,
                now, null, null, now);
    }

    public Match complete(MatchResult matchResult, Instant now) {
        requireStatus(MatchStatus.PLAYING, "complete");
        if (matchResult == null) {
            throw new InvalidMatchResultException(
                    "Completed Match requires a result"
            );
        }
        return copy(MatchStatus.COMPLETED, matchResult, resultVersion + 1,
                startedAt, now, null, now);
    }

    public Match cancel(Instant now) {
        if (status != MatchStatus.CREATED && status != MatchStatus.PLAYING) {
            throw new InvalidMatchStateException(
                    "Match cannot cancel from status " + status
            );
        }
        return copy(MatchStatus.CANCELLED, null, 0,
                startedAt, null, now, now);
    }

    private void requireStatus(MatchStatus expected, String action) {
        if (status != expected) {
            throw new InvalidMatchStateException(
                    "Match cannot " + action + " from status " + status
            );
        }
    }

    private Match copy(
            MatchStatus newStatus,
            MatchResult newResult,
            int newResultVersion,
            Instant newStartedAt,
            Instant newCompletedAt,
            Instant newCancelledAt,
            Instant newUpdatedAt
    ) {
        return new Match(
                id,
                sessionId,
                sessionCourtId,
                newStatus,
                source,
                newResult,
                newResultVersion,
                createdAt,
                newStartedAt,
                newCompletedAt,
                newCancelledAt,
                newUpdatedAt,
                version
        );
    }

    private static void validateState(
            MatchStatus status,
            MatchResult result,
            int resultVersion,
            Instant startedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
        boolean valid = switch (status) {
            case CREATED -> startedAt == null
                    && completedAt == null
                    && cancelledAt == null
                    && result == null
                    && resultVersion == 0;
            case PLAYING -> startedAt != null
                    && completedAt == null
                    && cancelledAt == null
                    && result == null
                    && resultVersion == 0;
            case COMPLETED -> startedAt != null
                    && completedAt != null
                    && cancelledAt == null
                    && result != null
                    && resultVersion >= 1;
            case CANCELLED -> completedAt == null
                    && cancelledAt != null
                    && result == null
                    && resultVersion == 0;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    "Match runtime state is inconsistent with status " + status
            );
        }
        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "completedAt must not be before startedAt"
            );
        }
        if (cancelledAt != null
                && startedAt != null
                && cancelledAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "cancelledAt must not be before startedAt"
            );
        }
    }
}

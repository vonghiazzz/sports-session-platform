package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class MatchmakingRatingResolutionException extends RuntimeException {

    private final MatchmakingRatingResolutionFailureReason reason;
    private final Set<UUID> affectedPlayerIds;
    private final SportCode sportCode;
    private final MatchFormat matchFormat;

    public MatchmakingRatingResolutionException(
            MatchmakingRatingResolutionFailureReason reason,
            Collection<UUID> affectedPlayerIds,
            SportCode sportCode,
            MatchFormat matchFormat,
            String message
    ) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(
                affectedPlayerIds,
                "affectedPlayerIds are required"
        );
        this.affectedPlayerIds = Set.copyOf(affectedPlayerIds);
        this.sportCode = Objects.requireNonNull(
                sportCode,
                "sportCode is required"
        );
        this.matchFormat = Objects.requireNonNull(
                matchFormat,
                "matchFormat is required"
        );
    }

    public MatchmakingRatingResolutionFailureReason reason() {
        return reason;
    }

    public Set<UUID> affectedPlayerIds() {
        return affectedPlayerIds;
    }

    public SportCode sportCode() {
        return sportCode;
    }

    public MatchFormat matchFormat() {
        return matchFormat;
    }
}

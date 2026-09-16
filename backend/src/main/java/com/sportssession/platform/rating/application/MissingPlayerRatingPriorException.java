package com.sportssession.platform.rating.application;

import com.sportssession.platform.shared.domain.SportCode;

import java.util.Set;
import java.util.UUID;

public class MissingPlayerRatingPriorException extends RuntimeException {

    private final Set<UUID> missingPlayerIds;
    private final SportCode sportCode;

    public MissingPlayerRatingPriorException(
            Set<UUID> missingPlayerIds,
            SportCode sportCode
    ) {
        super("Missing PlayerSportProfile for sport " + sportCode
                + " and playerIds " + missingPlayerIds);
        this.missingPlayerIds = Set.copyOf(missingPlayerIds);
        this.sportCode = sportCode;
    }

    public Set<UUID> missingPlayerIds() {
        return missingPlayerIds;
    }

    public SportCode sportCode() {
        return sportCode;
    }
}

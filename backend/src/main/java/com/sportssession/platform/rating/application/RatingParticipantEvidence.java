package com.sportssession.platform.rating.application;

import com.sportssession.platform.match.domain.TeamSide;

import java.util.UUID;

public record RatingParticipantEvidence(
        UUID playerId,
        TeamSide teamSide,
        int teamSlot
) {
    public RatingParticipantEvidence {
        if (playerId == null) {
            throw new InvalidCompletedMatchRatingEvidenceException(
                    "Rating participant playerId is required"
            );
        }
        if (teamSide == null) {
            throw new InvalidCompletedMatchRatingEvidenceException(
                    "Rating participant teamSide is required"
            );
        }
        if (teamSlot < 1) {
            throw new InvalidCompletedMatchRatingEvidenceException(
                    "Rating participant teamSlot must be positive"
            );
        }
    }
}

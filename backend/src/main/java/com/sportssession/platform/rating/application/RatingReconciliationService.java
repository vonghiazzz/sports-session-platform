package com.sportssession.platform.rating.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class RatingReconciliationService {

    private final RatingContextLock contextLock;
    private final PendingCompletedMatchRatingLookup pendingMatchLookup;
    private final RatingProcessingService ratingProcessingService;

    public RatingReconciliationService(
            RatingContextLock contextLock,
            PendingCompletedMatchRatingLookup pendingMatchLookup,
            RatingProcessingService ratingProcessingService
    ) {
        this.contextLock = contextLock;
        this.pendingMatchLookup = pendingMatchLookup;
        this.ratingProcessingService = ratingProcessingService;
    }

    @Transactional
    public RatingReconciliationResult reconcileOne(
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        if (!contextLock.tryAcquire(sportCode, matchFormat)) {
            return RatingReconciliationResult.BUSY;
        }

        Optional<UUID> candidate = pendingMatchLookup.findEarliestUnresolved(
                sportCode,
                matchFormat
        );
        if (candidate.isEmpty()) {
            return RatingReconciliationResult.IDLE;
        }

        return switch (ratingProcessingService.processRating(candidate.get())) {
            case APPLIED -> RatingReconciliationResult.APPLIED;
            case ALREADY_APPLIED -> RatingReconciliationResult.ALREADY_APPLIED;
        };
    }
}

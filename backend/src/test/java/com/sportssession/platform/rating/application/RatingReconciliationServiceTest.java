package com.sportssession.platform.rating.application;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingReconciliationServiceTest {

    @Mock
    private RatingContextLock contextLock;

    @Mock
    private PendingCompletedMatchRatingLookup pendingMatchLookup;

    @Mock
    private RatingProcessingService ratingProcessingService;

    @InjectMocks
    private RatingReconciliationService service;

    @Test
    void lockIsAcquiredBeforeDiscoveryAndAppliedProcessing() {
        UUID matchId = UUID.randomUUID();
        when(contextLock.tryAcquire(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(true);
        when(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(Optional.of(matchId));
        when(ratingProcessingService.processRating(matchId))
                .thenReturn(RatingProcessingResult.APPLIED);

        assertThat(service.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isEqualTo(RatingReconciliationResult.APPLIED);

        InOrder order = inOrder(
                contextLock,
                pendingMatchLookup,
                ratingProcessingService
        );
        order.verify(contextLock).tryAcquire(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
        order.verify(pendingMatchLookup).findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
        order.verify(ratingProcessingService).processRating(matchId);
    }

    @Test
    void lockContentionReturnsBusyWithoutDiscovery() {
        when(contextLock.tryAcquire(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(false);

        assertThat(service.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isEqualTo(RatingReconciliationResult.BUSY);

        verify(pendingMatchLookup, never()).findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
        verify(ratingProcessingService, never()).processRating(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void noCandidateReturnsIdleWithoutProcessing() {
        when(contextLock.tryAcquire(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(true);
        when(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(Optional.empty());

        assertThat(service.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isEqualTo(RatingReconciliationResult.IDLE);

        verify(ratingProcessingService, never()).processRating(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void delegatedAlreadyAppliedResultIsPreserved() {
        UUID matchId = UUID.randomUUID();
        when(contextLock.tryAcquire(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(true);
        when(pendingMatchLookup.findEarliestUnresolved(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(Optional.of(matchId));
        when(ratingProcessingService.processRating(matchId))
                .thenReturn(RatingProcessingResult.ALREADY_APPLIED);

        assertThat(service.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).isEqualTo(RatingReconciliationResult.ALREADY_APPLIED);
    }
}

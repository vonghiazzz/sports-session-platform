package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.application.RatingReconciliationResult;
import com.sportssession.platform.rating.application.RatingReconciliationService;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RatingReconciliationSchedulerTest {

    @ParameterizedTest
    @EnumSource(RatingReconciliationResult.class)
    void oneTriggerInvokesExactlyOneReconciliationForFixedV1Context(
            RatingReconciliationResult result
    ) {
        RatingReconciliationService service = mock(
                RatingReconciliationService.class
        );
        when(service.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        )).thenReturn(result);
        RatingReconciliationScheduler scheduler =
                new RatingReconciliationScheduler(service);

        scheduler.reconcileOnePendingMatch();

        verify(service, times(1)).reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
    }
}

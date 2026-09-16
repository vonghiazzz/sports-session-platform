package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.application.RatingReconciliationService;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "rating.reconciliation",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RatingReconciliationScheduler {

    private final RatingReconciliationService reconciliationService;

    public RatingReconciliationScheduler(
            RatingReconciliationService reconciliationService
    ) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${rating.reconciliation.fixed-delay-ms:5000}")
    public void reconcileOnePendingMatch() {
        reconciliationService.reconcileOne(
                SportCode.BADMINTON,
                MatchFormat.DOUBLES
        );
    }
}

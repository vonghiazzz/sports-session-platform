package com.sportssession.platform.matchplan.api;

import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.matchplan.application.MatchPlanDetails;
import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchPlanResponse(
        UUID id,
        UUID sessionId,
        UUID sessionCourtId,
        MatchSource source,
        MatchPlanStatus status,
        Integer queuePosition,
        UUID startedMatchId,
        List<MatchPlanParticipantResponse> participants,
        Instant createdAt,
        Instant startedAt,
        Instant cancelledAt,
        Instant updatedAt,
        long version
) {
    static MatchPlanResponse from(MatchPlanDetails details) {
        MatchPlan plan = details.plan();
        return new MatchPlanResponse(
                plan.id(), plan.sessionId(), plan.sessionCourtId(),
                plan.source(), plan.status(), plan.queuePosition(),
                plan.startedMatchId(), details.participants().stream()
                        .map(MatchPlanParticipantResponse::from)
                        .toList(),
                plan.createdAt(), plan.startedAt(), plan.cancelledAt(),
                plan.updatedAt(), plan.version()
        );
    }
}

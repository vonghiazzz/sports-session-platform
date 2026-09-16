package com.sportssession.platform.matchplan.application;

import com.sportssession.platform.matchplan.domain.MatchPlan;
import com.sportssession.platform.matchplan.domain.MatchPlanParticipant;

import java.util.List;

public record MatchPlanDetails(
        MatchPlan plan,
        List<MatchPlanParticipant> participants
) {
    public MatchPlanDetails {
        participants = List.copyOf(participants);
    }
}

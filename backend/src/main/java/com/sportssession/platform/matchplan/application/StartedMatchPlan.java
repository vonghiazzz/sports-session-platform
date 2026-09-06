package com.sportssession.platform.matchplan.application;

import com.sportssession.platform.match.application.StartedMatch;

public record StartedMatchPlan(
        MatchPlanDetails matchPlan,
        StartedMatch match
) {
}

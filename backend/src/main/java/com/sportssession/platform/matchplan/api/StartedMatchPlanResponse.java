package com.sportssession.platform.matchplan.api;

import com.sportssession.platform.match.api.MatchResponse;
import com.sportssession.platform.matchplan.application.StartedMatchPlan;

public record StartedMatchPlanResponse(
        MatchPlanResponse matchPlan,
        MatchResponse match
) {
    static StartedMatchPlanResponse from(StartedMatchPlan started) {
        return new StartedMatchPlanResponse(
                MatchPlanResponse.from(started.matchPlan()),
                MatchResponse.from(
                        started.match().match(),
                        started.match().participants()
                )
        );
    }
}

package com.sportssession.platform.matchplan.domain;

import java.util.UUID;

public class MatchPlanNotFoundException extends RuntimeException {
    public MatchPlanNotFoundException(UUID id) {
        super("MatchPlan not found: " + id);
    }
}

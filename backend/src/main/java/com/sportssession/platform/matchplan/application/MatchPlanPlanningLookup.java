package com.sportssession.platform.matchplan.application;

import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import com.sportssession.platform.matchplan.infrastructure.MatchPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchPlanPlanningLookup {

    private final MatchPlanRepository planRepository;

    public MatchPlanPlanningLookup(MatchPlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional(readOnly = true)
    public Set<UUID> queuedParticipantIds(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");

        return Set.copyOf(
                planRepository.findParticipantIdsBySessionAndStatus(
                        sessionId,
                        MatchPlanStatus.QUEUED
                )
        );
    }
}
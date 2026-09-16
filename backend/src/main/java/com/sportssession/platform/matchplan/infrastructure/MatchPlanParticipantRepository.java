package com.sportssession.platform.matchplan.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchPlanParticipantRepository
        extends JpaRepository<MatchPlanParticipantEntity, UUID> {

    List<MatchPlanParticipantEntity>
    findAllByMatchPlanIdOrderByTeamSideAscTeamSlotAsc(UUID matchPlanId);

    void deleteAllByMatchPlanId(UUID matchPlanId);
}

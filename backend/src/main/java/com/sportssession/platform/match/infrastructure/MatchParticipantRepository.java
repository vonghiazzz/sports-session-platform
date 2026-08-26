package com.sportssession.platform.match.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MatchParticipantRepository
        extends JpaRepository<MatchParticipantEntity, UUID> {

    List<MatchParticipantEntity> findAllByMatchIdOrderByTeamSideAscTeamSlotAsc(
            UUID matchId
    );
}

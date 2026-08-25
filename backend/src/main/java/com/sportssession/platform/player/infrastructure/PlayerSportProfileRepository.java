package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlayerSportProfileRepository
        extends JpaRepository<PlayerSportProfileEntity, UUID> {

    List<PlayerSportProfileEntity> findAllByPlayerIdOrderByCreatedAtAsc(UUID playerId);

    List<PlayerSportProfileEntity> findAllByPlayerIdInOrderByCreatedAtAsc(
            Collection<UUID> playerIds);

    boolean existsByPlayerIdAndSportCode(UUID playerId, SportCode sportCode);
}

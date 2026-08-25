package com.sportssession.platform.player.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    List<PlayerEntity> findAllByOrderByCreatedAtAscIdAsc();

    List<PlayerEntity> findByDisplayNameContainingIgnoreCaseOrderByCreatedAtAscIdAsc(String name);
}


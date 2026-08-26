package com.sportssession.platform.session.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionParticipantRepository
        extends JpaRepository<SessionParticipantEntity, UUID> {

    boolean existsBySessionIdAndPlayerId(UUID sessionId, UUID playerId);

    Optional<SessionParticipantEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    List<SessionParticipantEntity> findAllBySessionIdOrderByJoinedAtAscIdAsc(UUID sessionId);
}

package com.sportssession.platform.session.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionCourtRepository extends JpaRepository<SessionCourtEntity, UUID> {

    boolean existsBySessionIdAndCourtId(UUID sessionId, UUID courtId);

    Optional<SessionCourtEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    List<SessionCourtEntity> findAllBySessionIdOrderByAddedAtAscIdAsc(UUID sessionId);
}

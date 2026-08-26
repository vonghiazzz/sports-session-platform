package com.sportssession.platform.session.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionCourtRepository extends JpaRepository<SessionCourtEntity, UUID> {

    boolean existsBySessionIdAndCourtId(UUID sessionId, UUID courtId);

    Optional<SessionCourtEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select court from SessionCourtEntity court where court.id = :courtId")
    Optional<SessionCourtEntity> findByIdForUpdate(@Param("courtId") UUID courtId);

    List<SessionCourtEntity> findAllBySessionIdOrderByAddedAtAscIdAsc(UUID sessionId);
}

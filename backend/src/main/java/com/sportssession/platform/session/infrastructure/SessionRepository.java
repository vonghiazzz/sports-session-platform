package com.sportssession.platform.session.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {

    @Query("""
            select session
            from SessionEntity session
            order by
                case session.status
                    when com.sportssession.platform.session.domain.SessionStatus.IN_PROGRESS then 0
                    when com.sportssession.platform.session.domain.SessionStatus.PLANNED then 1
                    else 2
                end,
                session.plannedStartAt desc,
                session.id asc
            """)
    List<SessionEntity> findAllForDiscovery();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from SessionEntity session where session.id = :sessionId")
    Optional<SessionEntity> findByIdForUpdate(@Param("sessionId") UUID sessionId);
}

package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.MatchStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<MatchEntity, UUID> {

    boolean existsBySessionIdAndStatus(
            UUID sessionId,
            MatchStatus status
    );

    List<MatchEntity> findAllBySessionIdOrderByCreatedAtAscIdAsc(
            UUID sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select match from MatchEntity match where match.id = :matchId")
    Optional<MatchEntity> findByIdForUpdate(@Param("matchId") UUID matchId);
}

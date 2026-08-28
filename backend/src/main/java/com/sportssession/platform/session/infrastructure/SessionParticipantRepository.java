package com.sportssession.platform.session.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionParticipantRepository
        extends JpaRepository<SessionParticipantEntity, UUID> {

    boolean existsBySessionIdAndPlayerId(UUID sessionId, UUID playerId);

    Optional<SessionParticipantEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select participant
            from SessionParticipantEntity participant
            where participant.id in :participantIds
            order by participant.id
            """)
    List<SessionParticipantEntity> findAllByIdForUpdateOrderById(
            @Param("participantIds") List<UUID> participantIds
    );

    List<SessionParticipantEntity> findAllBySessionIdOrderByJoinedAtAscIdAsc(UUID sessionId);

    List<SessionParticipantEntity> findAllBySessionIdOrderByPlayerIdAscIdAsc(
            UUID sessionId
    );
}

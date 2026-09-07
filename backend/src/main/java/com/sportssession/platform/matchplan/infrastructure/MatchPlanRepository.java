package com.sportssession.platform.matchplan.infrastructure;

import com.sportssession.platform.matchplan.domain.MatchPlanStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchPlanRepository
        extends JpaRepository<MatchPlanEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from MatchPlanEntity plan where plan.id = :planId")
    Optional<MatchPlanEntity> findByIdForUpdate(@Param("planId") UUID planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select plan from MatchPlanEntity plan
            where plan.sessionCourtId = :courtId and plan.status = :status
            order by plan.queuePosition asc, plan.id asc
            """)
    List<MatchPlanEntity> findQueueForUpdate(
            @Param("courtId") UUID courtId,
            @Param("status") MatchPlanStatus status
    );

    long countBySessionCourtIdAndStatus(
            UUID sessionCourtId,
            MatchPlanStatus status
    );

    List<MatchPlanEntity>
    findAllBySessionIdOrderBySessionCourtIdAscQueuePositionAscCreatedAtAscIdAsc(
            UUID sessionId
    );

        @Query("""
            select distinct participant.sessionParticipantId
            from MatchPlanParticipantEntity participant, MatchPlanEntity plan
            where participant.matchPlanId = plan.id
              and participant.sessionId = :sessionId
              and plan.sessionId = :sessionId
              and plan.status = :status
            """)
    List<UUID> findParticipantIdsBySessionAndStatus(
            @Param("sessionId") UUID sessionId,
            @Param("status") MatchPlanStatus status
    );

    @Query("""
            select distinct participant.sessionParticipantId
            from MatchPlanParticipantEntity participant, MatchPlanEntity plan
            where participant.matchPlanId = plan.id
              and participant.sessionId = :sessionId
              and plan.sessionId = :sessionId
              and plan.status = :status
              and plan.id <> :excludedPlanId
            """)
    List<UUID> findParticipantIdsBySessionAndStatusExcludingPlan(
            @Param("sessionId") UUID sessionId,
            @Param("status") MatchPlanStatus status,
            @Param("excludedPlanId") UUID excludedPlanId
    );
}

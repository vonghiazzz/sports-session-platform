package com.sportssession.platform.rating.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RatingEventRepository
        extends JpaRepository<RatingEventEntity, UUID> {

    List<RatingEventEntity> findAllByMatchIdAndResultVersionOrderByPlayerRatingId(
            UUID matchId,
            int resultVersion
    );

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                  FROM rating_events event
                  JOIN matches match_record ON match_record.id = event.match_id
                 WHERE event.player_rating_id IN (:playerRatingIds)
                   AND (
                       match_record.completed_at > :completedAt
                       OR (
                           match_record.completed_at = :completedAt
                           AND match_record.id > :matchId
                       )
                   )
            )
            """, nativeQuery = true)
    boolean existsAppliedMatchAfter(
            @Param("playerRatingIds") Collection<UUID> playerRatingIds,
            @Param("completedAt") Instant completedAt,
            @Param("matchId") UUID matchId
    );
}

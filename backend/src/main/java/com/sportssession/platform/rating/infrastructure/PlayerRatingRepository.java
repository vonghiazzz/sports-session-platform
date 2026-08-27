package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PlayerRatingRepository
        extends JpaRepository<PlayerRatingEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT rating
            FROM PlayerRatingEntity rating
            WHERE rating.playerId IN :playerIds
              AND rating.sportCode = :sportCode
              AND rating.matchFormat = :matchFormat
            ORDER BY rating.playerId
            """)
    List<PlayerRatingEntity> findContextRatingsForUpdate(
            @Param("playerIds") Collection<UUID> playerIds,
            @Param("sportCode") SportCode sportCode,
            @Param("matchFormat") MatchFormat matchFormat
    );
}

package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.player.application.PlayerManagementRatingHistoryItem;
import com.sportssession.platform.player.application.PlayerManagementRatingHistoryReader;
import com.sportssession.platform.player.application.PlayerManagementRatingOutcome;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class PlayerManagementRatingHistoryReaderAdapter
        implements PlayerManagementRatingHistoryReader {

    private static final String READ_HISTORY_SQL = """
            SELECT event.match_id,
                   match_record.completed_at AS match_completed_at,
                   event.outcome,
                   event.before_rating,
                   event.before_uncertainty,
                   event.after_rating,
                   event.after_uncertainty,
                   event.result_version,
                   event.algorithm_version,
                   event.created_at
              FROM rating_events event
              JOIN player_ratings rating
                ON rating.id = event.player_rating_id
              JOIN matches match_record
                ON match_record.id = event.match_id
             WHERE rating.player_id = ?
               AND rating.sport_code = ?
               AND rating.match_format = ?
               AND match_record.status = 'COMPLETED'
             ORDER BY match_record.completed_at DESC,
                      match_record.id DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public PlayerManagementRatingHistoryReaderAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlayerManagementRatingHistoryItem> readHistory(
            UUID playerId,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");

        return List.copyOf(jdbcTemplate.query(
                READ_HISTORY_SQL,
                (resultSet, rowNumber) ->
                        new PlayerManagementRatingHistoryItem(
                                resultSet.getObject("match_id", UUID.class),
                                resultSet.getTimestamp(
                                        "match_completed_at"
                                ).toInstant(),
                                PlayerManagementRatingOutcome.valueOf(
                                        resultSet.getString("outcome")
                                ),
                                resultSet.getBigDecimal("before_rating"),
                                resultSet.getBigDecimal("before_uncertainty"),
                                resultSet.getBigDecimal("after_rating"),
                                resultSet.getBigDecimal("after_uncertainty"),
                                resultSet.getInt("result_version"),
                                resultSet.getString("algorithm_version"),
                                resultSet.getTimestamp("created_at").toInstant()
                        ),
                playerId,
                sportCode.name(),
                matchFormat.name()
        ));
    }
}

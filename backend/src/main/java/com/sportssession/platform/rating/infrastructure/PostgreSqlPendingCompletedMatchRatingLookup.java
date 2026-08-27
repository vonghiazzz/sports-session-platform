package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.application.PendingCompletedMatchRatingLookup;
import com.sportssession.platform.rating.application.UnsupportedRatingContextException;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgreSqlPendingCompletedMatchRatingLookup
        implements PendingCompletedMatchRatingLookup {

    private static final String FIND_EARLIEST_UNRESOLVED_SQL = """
            SELECT match_record.id
              FROM matches match_record
              JOIN sessions session_record
                ON session_record.id = match_record.session_id
             WHERE match_record.status = 'COMPLETED'
               AND session_record.sport_code = ?
               AND session_record.match_format = ?
               AND NOT (
                   (SELECT COUNT(*)
                      FROM match_participants assignment
                     WHERE assignment.match_id = match_record.id) = 4
                   AND
                   (SELECT COUNT(DISTINCT participant.player_id)
                      FROM match_participants assignment
                      JOIN session_participants participant
                        ON participant.id = assignment.session_participant_id
                     WHERE assignment.match_id = match_record.id) = 4
                   AND
                   (SELECT COUNT(*)
                      FROM rating_events event
                     WHERE event.match_id = match_record.id
                       AND event.result_version = match_record.result_version) = 4
                   AND
                   (SELECT COUNT(DISTINCT event.id)
                      FROM rating_events event
                      JOIN player_ratings rating
                        ON rating.id = event.player_rating_id
                     WHERE event.match_id = match_record.id
                       AND event.result_version = match_record.result_version
                       AND event.algorithm_version = ?
                       AND rating.algorithm_version = ?
                       AND rating.sport_code = session_record.sport_code
                       AND rating.match_format = session_record.match_format
                       AND EXISTS (
                           SELECT 1
                             FROM match_participants assignment
                             JOIN session_participants participant
                               ON participant.id = assignment.session_participant_id
                            WHERE assignment.match_id = match_record.id
                              AND participant.player_id = rating.player_id
                       )) = 4
               )
             ORDER BY match_record.completed_at ASC, match_record.id ASC
             LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlPendingCompletedMatchRatingLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UUID> findEarliestUnresolved(
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        requireSupportedContext(sportCode, matchFormat);
        List<UUID> matches = jdbcTemplate.query(
                FIND_EARLIEST_UNRESOLVED_SQL,
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                sportCode.name(),
                matchFormat.name(),
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION,
                WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION
        );
        return matches.stream().findFirst();
    }

    private void requireSupportedContext(
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        if (sportCode != SportCode.BADMINTON
                || matchFormat != MatchFormat.DOUBLES) {
            throw new UnsupportedRatingContextException(
                    "Rating V1 discovery supports only BADMINTON + DOUBLES"
            );
        }
    }
}

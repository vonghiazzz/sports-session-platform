package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.matchmaking.application.MatchmakingSessionMatchCountReader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class MatchmakingSessionMatchCountReaderAdapter
        implements MatchmakingSessionMatchCountReader {

    private static final String COMPLETED_MATCH_COUNTS_SQL = """
            SELECT mp.session_participant_id,
                   COUNT(DISTINCT m.id) AS completed_match_count
              FROM match_participants mp
              JOIN matches m
                ON m.id = mp.match_id
              JOIN session_participants sp
                ON sp.id = mp.session_participant_id
               AND sp.session_id = m.session_id
             WHERE m.session_id = :sessionId
               AND m.status = 'COMPLETED'
               AND mp.session_participant_id IN (:participantIds)
             GROUP BY mp.session_participant_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MatchmakingSessionMatchCountReaderAdapter(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Integer> readCompletedMatchCounts(
            UUID sessionId,
            Collection<UUID> sessionParticipantIds
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Set<UUID> requestedIds = validateAndCopy(sessionParticipantIds);
        if (requestedIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Integer> counts = new LinkedHashMap<>();
        requestedIds.forEach(participantId -> counts.put(participantId, 0));

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("participantIds", requestedIds);
        jdbcTemplate.query(
                COMPLETED_MATCH_COUNTS_SQL,
                parameters,
                resultSet -> {
                    UUID participantId = resultSet.getObject(
                            "session_participant_id",
                            UUID.class
                    );
                    if (!counts.containsKey(participantId)) {
                        throw new IllegalStateException(
                                "Match count query returned an unrequested "
                                        + "SessionParticipant"
                        );
                    }
                    int count = Math.toIntExact(
                            resultSet.getLong("completed_match_count")
                    );
                    counts.put(participantId, count);
                }
        );

        return Map.copyOf(counts);
    }

    private Set<UUID> validateAndCopy(
            Collection<UUID> sessionParticipantIds
    ) {
        Objects.requireNonNull(
                sessionParticipantIds,
                "sessionParticipantIds are required"
        );
        Set<UUID> requestedIds = new LinkedHashSet<>();
        for (UUID participantId : sessionParticipantIds) {
            Objects.requireNonNull(
                    participantId,
                    "sessionParticipantId is required"
            );
            if (!requestedIds.add(participantId)) {
                throw new IllegalArgumentException(
                        "sessionParticipantIds must not contain duplicates: "
                                + participantId
                );
            }
        }
        return Set.copyOf(requestedIds);
    }
}

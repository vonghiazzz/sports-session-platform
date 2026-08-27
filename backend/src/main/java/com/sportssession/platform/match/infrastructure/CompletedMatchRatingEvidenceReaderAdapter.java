package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.rating.application.CompletedMatchRatingContext;
import com.sportssession.platform.rating.application.CompletedMatchRatingEvidenceReader;
import com.sportssession.platform.rating.application.InvalidCompletedMatchRatingEvidenceException;
import com.sportssession.platform.rating.application.RatingParticipantEvidence;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class CompletedMatchRatingEvidenceReaderAdapter
        implements CompletedMatchRatingEvidenceReader {

    private static final String COMPLETED_MATCH_EVIDENCE_SQL = """
            SELECT m.id AS match_id,
                   m.session_id,
                   m.result_version,
                   m.completed_at,
                   m.source,
                   m.winner_team,
                   m.team_a_score,
                   m.team_b_score,
                   s.sport_code,
                   s.match_format,
                   mp.team_side,
                   mp.team_slot,
                   sp.player_id
              FROM matches m
              JOIN sessions s ON s.id = m.session_id
              LEFT JOIN match_participants mp ON mp.match_id = m.id
              LEFT JOIN session_participants sp
                ON sp.id = mp.session_participant_id
             WHERE m.id = ?
               AND m.status = 'COMPLETED'
             ORDER BY mp.team_side, mp.team_slot
            """;

    private final JdbcTemplate jdbcTemplate;

    public CompletedMatchRatingEvidenceReaderAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompletedMatchRatingContext> findCompletedMatch(UUID matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("matchId is required");
        }

        List<EvidenceRow> rows = jdbcTemplate.query(
                COMPLETED_MATCH_EVIDENCE_SQL,
                (resultSet, rowNumber) -> mapRow(resultSet),
                matchId
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        EvidenceRow match = rows.getFirst();
        List<RatingParticipantEvidence> teamA = new ArrayList<>(2);
        List<RatingParticipantEvidence> teamB = new ArrayList<>(2);
        for (EvidenceRow row : rows) {
            if (row.playerId() == null
                    || row.teamSide() == null
                    || row.teamSlot() == null) {
                throw new InvalidCompletedMatchRatingEvidenceException(
                        "Completed Match evidence contains an unresolved participant"
                );
            }
            RatingParticipantEvidence participant = new RatingParticipantEvidence(
                    row.playerId(),
                    row.teamSide(),
                    row.teamSlot()
            );
            switch (participant.teamSide()) {
                case A -> teamA.add(participant);
                case B -> teamB.add(participant);
            }
        }

        return Optional.of(new CompletedMatchRatingContext(
                match.matchId(),
                match.sessionId(),
                match.resultVersion(),
                match.completedAt(),
                match.source(),
                match.sportCode(),
                match.matchFormat(),
                match.winnerTeam(),
                match.teamAScore(),
                match.teamBScore(),
                teamA,
                teamB
        ));
    }

    private EvidenceRow mapRow(ResultSet resultSet) throws SQLException {
        return new EvidenceRow(
                resultSet.getObject("match_id", UUID.class),
                resultSet.getObject("session_id", UUID.class),
                resultSet.getInt("result_version"),
                instant(resultSet, "completed_at"),
                enumValue(resultSet, "source", MatchSource.class),
                enumValue(resultSet, "sport_code", SportCode.class),
                enumValue(resultSet, "match_format", MatchFormat.class),
                enumValue(resultSet, "winner_team", TeamSide.class),
                nullableInteger(resultSet, "team_a_score"),
                nullableInteger(resultSet, "team_b_score"),
                enumValue(resultSet, "team_side", TeamSide.class),
                nullableInteger(resultSet, "team_slot"),
                resultSet.getObject("player_id", UUID.class)
        );
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        var timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer nullableInteger(ResultSet resultSet, String column)
            throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private <E extends Enum<E>> E enumValue(
            ResultSet resultSet,
            String column,
            Class<E> enumType
    ) throws SQLException {
        String value = resultSet.getString(column);
        return value == null ? null : Enum.valueOf(enumType, value);
    }

    private record EvidenceRow(
            UUID matchId,
            UUID sessionId,
            int resultVersion,
            Instant completedAt,
            MatchSource source,
            SportCode sportCode,
            MatchFormat matchFormat,
            TeamSide winnerTeam,
            Integer teamAScore,
            Integer teamBScore,
            TeamSide teamSide,
            Integer teamSlot,
            UUID playerId
    ) {
    }
}

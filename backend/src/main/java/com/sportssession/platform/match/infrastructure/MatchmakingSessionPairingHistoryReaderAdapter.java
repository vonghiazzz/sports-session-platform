package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.matchmaking.application.MatchmakingSessionPairingHistoryReader;
import com.sportssession.platform.matchmaking.domain.CompletedMatchPairing;
import com.sportssession.platform.matchmaking.domain.MatchmakingSessionPairingHistory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class MatchmakingSessionPairingHistoryReaderAdapter
        implements MatchmakingSessionPairingHistoryReader {

    private static final String COMPLETED_PAIRING_HISTORY_SQL = """
            SELECT m.id AS match_id,
                   m.completed_at,
                   mp.session_participant_id,
                   mp.team_side,
                   mp.team_slot
              FROM matches m
              JOIN match_participants mp
                ON mp.match_id = m.id
              JOIN session_participants sp
                ON sp.id = mp.session_participant_id
               AND sp.session_id = m.session_id
             WHERE m.session_id = ?
               AND m.status = 'COMPLETED'
             ORDER BY m.completed_at DESC,
                      m.id DESC,
                      mp.team_side ASC,
                      mp.team_slot ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public MatchmakingSessionPairingHistoryReaderAdapter(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public MatchmakingSessionPairingHistory readCompletedPairingHistory(
            UUID sessionId
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Map<UUID, PairingBuilder> matches = new LinkedHashMap<>();
        jdbcTemplate.query(
                COMPLETED_PAIRING_HISTORY_SQL,
                resultSet -> {
                    UUID matchId = resultSet.getObject(
                            "match_id",
                            UUID.class
                    );
                    Instant completedAt = resultSet.getTimestamp(
                            "completed_at"
                    ).toInstant();
                    PairingBuilder match = matches.computeIfAbsent(
                            matchId,
                            ignored -> new PairingBuilder(
                                    matchId,
                                    completedAt
                            )
                    );
                    if (!match.completedAt().equals(completedAt)) {
                        throw new IllegalStateException(
                                "Completed Match has inconsistent timestamps"
                        );
                    }
                    match.add(
                            TeamSide.valueOf(
                                    resultSet.getString("team_side")
                            ),
                            resultSet.getObject(
                                    "session_participant_id",
                                    UUID.class
                            )
                    );
                },
                sessionId
        );

        return new MatchmakingSessionPairingHistory(
                sessionId,
                matches.values().stream()
                        .map(PairingBuilder::build)
                        .toList()
        );
    }

    private static final class PairingBuilder {
        private final UUID matchId;
        private final Instant completedAt;
        private final List<UUID> teamA = new ArrayList<>();
        private final List<UUID> teamB = new ArrayList<>();

        private PairingBuilder(UUID matchId, Instant completedAt) {
            this.matchId = matchId;
            this.completedAt = completedAt;
        }

        private Instant completedAt() {
            return completedAt;
        }

        private void add(TeamSide teamSide, UUID participantId) {
            (teamSide == TeamSide.A ? teamA : teamB).add(participantId);
        }

        private CompletedMatchPairing build() {
            return new CompletedMatchPairing(
                    matchId,
                    completedAt,
                    teamA,
                    teamB
            );
        }
    }
}

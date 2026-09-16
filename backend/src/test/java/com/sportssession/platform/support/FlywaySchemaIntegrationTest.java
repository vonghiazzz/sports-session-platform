package com.sportssession.platform.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import org.flywaydb.core.Flyway;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesRuntimeTablesThroughGlobalPlayerCodeFoundation() {
        String serverVersion = jdbcTemplate.queryForObject(
                "SHOW server_version", String.class);
        assertThat(serverVersion).startsWith("18.4");

        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                ORDER BY table_name
                """, String.class);

        assertThat(tables).contains(
                "flyway_schema_history",
                "players",
                "player_sport_profiles",
                "venues",
                "courts",
                "sessions",
                "session_participants",
                "session_courts",
                "matches",
                "match_participants",
                "player_ratings",
                "rating_events",
                "match_plans",
                "match_plan_participants");
        assertThat(tables).doesNotContain(
                "match_results",
                "ratings",
                "rating_jobs",
                "recommendations",
                "payments",
                "bookings",
                "memberships");

        Integer migrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """, Integer.class);
        assertThat(migrationCount).isEqualTo(1);

        Integer sessionMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '2' AND success = true
                """, Integer.class);
        assertThat(sessionMigrationCount).isEqualTo(1);

        Integer matchMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '3' AND success = true
                """, Integer.class);
        assertThat(matchMigrationCount).isEqualTo(1);

        Integer ratingMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '4' AND success = true
                """, Integer.class);
        assertThat(ratingMigrationCount).isEqualTo(1);

        Integer matchPlanMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '5' AND success = true
                """, Integer.class);
        assertThat(matchPlanMigrationCount).isEqualTo(1);

        Integer buddyPairMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '6' AND success = true
                """, Integer.class);
        assertThat(buddyPairMigrationCount).isEqualTo(1);

        Integer participantCodeMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '7' AND success = true
                """, Integer.class);
        assertThat(participantCodeMigrationCount).isEqualTo(1);

        Integer personalAccessTokenMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '8' AND success = true
                """, Integer.class);
        assertThat(personalAccessTokenMigrationCount).isEqualTo(1);

        Integer playerCodeMigrationCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '9' AND success = true
                """, Integer.class);
        assertThat(playerCodeMigrationCount).isEqualTo(1);

        assertThat(playerCodeColumnCount("public")).isEqualTo(1);

        List<String> playerCodeConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'players'
                  AND constraint_name IN (
                    'ck_players_player_code_positive',
                    'uk_players_player_code'
                  )
                ORDER BY constraint_name
                """, String.class);
        assertThat(playerCodeConstraints).containsExactly(
                "ck_players_player_code_positive",
                "uk_players_player_code"
        );

        Integer playerCodeSequenceCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_sequences
                WHERE schemaname = 'public'
                  AND sequencename = 'players_player_code_seq'
                """, Integer.class);
        assertThat(playerCodeSequenceCount).isEqualTo(1);

        Integer participantCodeColumnCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'session_participants'
                  AND column_name = 'participant_code'
                  AND data_type = 'integer'
                  AND is_nullable = 'NO'
                """, Integer.class);
        assertThat(participantCodeColumnCount).isEqualTo(1);

        List<String> participantCodeConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'session_participants'
                  AND constraint_name IN (
                    'ck_session_participants_code_positive',
                    'uk_session_participants_session_code'
                  )
                ORDER BY constraint_name
                """, String.class);
        assertThat(participantCodeConstraints).containsExactly(
                "ck_session_participants_code_positive",
                "uk_session_participants_session_code"
        );

        Integer personalAccessTokenColumnCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'session_participants'
                  AND column_name = 'personal_access_token'
                  AND data_type = 'uuid'
                  AND is_nullable = 'NO'
                """, Integer.class);
        assertThat(personalAccessTokenColumnCount).isEqualTo(1);

        Integer personalAccessTokenConstraintCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = 'session_participants'
                  AND constraint_name = 'uk_session_participants_personal_access_token'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(personalAccessTokenConstraintCount).isEqualTo(1);

        List<String> buddyPairColumns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'session_participants'
                  AND column_name = 'buddy_pair_id'
                  AND data_type = 'uuid'
                  AND is_nullable = 'YES'
                """, String.class);
        assertThat(buddyPairColumns).containsExactly("buddy_pair_id");

        Integer buddyPairIndexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_session_participants_session_buddy_pair'
                  AND indexdef ILIKE '%session_id%'
                  AND indexdef ILIKE '%buddy_pair_id%'
                  AND indexdef ILIKE '%WHERE%'
                """, Integer.class);
        assertThat(buddyPairIndexCount).isEqualTo(1);

        List<String> matchPlanConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name IN ('match_plans', 'match_plan_participants')
                ORDER BY constraint_name
                """, String.class);
        assertThat(matchPlanConstraints).contains(
                "fk_match_plans_session",
                "fk_match_plans_session_court",
                "fk_match_plans_started_match",
                "uk_match_plans_started_match",
                "chk_match_plans_state_consistency",
                "fk_match_plan_participants_plan_session",
                "fk_match_plan_participants_participant_session",
                "uk_match_plan_participants_plan_participant",
                "uk_match_plan_participants_plan_team_slot");

        Integer queuedPositionIndexCount = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uk_match_plans_queued_court_position'
                  AND indexdef ILIKE '%UNIQUE INDEX%'
                  AND indexdef ILIKE '%WHERE%'
                  AND indexdef ILIKE '%QUEUED%'
                """, Integer.class);
        assertThat(queuedPositionIndexCount).isEqualTo(1);

        List<String> ratingConstraints = jdbcTemplate.queryForList("""
                SELECT constraint_name
                FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name IN ('player_ratings', 'rating_events')
                ORDER BY constraint_name
                """, String.class);

        assertThat(ratingConstraints).contains(
                "fk_player_ratings_player",
                "uk_player_ratings_player_sport_format",
                "fk_rating_events_player_rating",
                "fk_rating_events_match",
                "uk_rating_events_match_version_player_rating",
                "chk_player_ratings_rating_value_not_nan",
                "chk_player_ratings_uncertainty_positive",
                "chk_rating_events_before_rating_not_nan",
                "chk_rating_events_after_uncertainty_positive");

        List<String> ratingIndexes = jdbcTemplate.queryForList("""
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN (
                    'idx_rating_events_player_rating_id_created_at_id',
                    'idx_matches_completed_at_id_result_version'
                  )
                ORDER BY indexname
                """, String.class);

        assertThat(ratingIndexes).containsExactly(
                "idx_matches_completed_at_id_result_version",
                "idx_rating_events_player_rating_id_created_at_id");
    }

    @Test
void participantCodeMigrationBackfillsExistingParticipantsDeterministically() {
    String schemaName =
            "participant_code_backfill_"
                    + UUID.randomUUID()
                    .toString()
                    .replace("-", "");

    UUID venueId = UUID.fromString(
            "00000000-0000-0000-0000-000000000100");

    UUID firstSessionId = UUID.fromString(
            "00000000-0000-0000-0000-000000000200");
    UUID secondSessionId = UUID.fromString(
            "00000000-0000-0000-0000-000000000201");

    UUID firstPlayerId = UUID.fromString(
            "00000000-0000-0000-0000-000000000301");
    UUID secondPlayerId = UUID.fromString(
            "00000000-0000-0000-0000-000000000302");
    UUID thirdPlayerId = UUID.fromString(
            "00000000-0000-0000-0000-000000000303");
    UUID fourthPlayerId = UUID.fromString(
            "00000000-0000-0000-0000-000000000304");

    UUID lowerParticipantId = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");
    UUID higherParticipantId = UUID.fromString(
            "00000000-0000-0000-0000-000000000002");
    UUID laterParticipantId = UUID.fromString(
            "00000000-0000-0000-0000-000000000003");
    UUID otherSessionParticipantId = UUID.fromString(
            "00000000-0000-0000-0000-000000000004");

    Instant baseTime = Instant.parse("2026-01-01T00:00:00Z");
    Instant sameJoinedAt = Instant.parse("2026-01-01T08:00:00Z");
    Instant laterJoinedAt = Instant.parse("2026-01-01T09:00:00Z");

    try {
        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .target("6")
                .load()
                .migrate();

        assertThat(participantCodeColumnCount(schemaName))
                .isZero();

        insertLegacyVenue(
                schemaName,
                venueId,
                baseTime
        );

        insertLegacyPlayer(
                schemaName,
                firstPlayerId,
                "Player 1",
                baseTime
        );
        insertLegacyPlayer(
                schemaName,
                secondPlayerId,
                "Player 2",
                baseTime
        );
        insertLegacyPlayer(
                schemaName,
                thirdPlayerId,
                "Player 3",
                baseTime
        );
        insertLegacyPlayer(
                schemaName,
                fourthPlayerId,
                "Player 4",
                baseTime
        );

        insertLegacySession(
                schemaName,
                firstSessionId,
                venueId,
                "Session A",
                baseTime
        );
        insertLegacySession(
                schemaName,
                secondSessionId,
                venueId,
                "Session B",
                baseTime
        );

        /*
         * Insert the higher UUID first on purpose.
         *
         * Both rows have the same joined_at, so V7 must use id ASC
         * instead of insertion order to choose participant codes.
         */
        insertLegacyParticipant(
                schemaName,
                higherParticipantId,
                firstSessionId,
                secondPlayerId,
                sameJoinedAt
        );

        insertLegacyParticipant(
                schemaName,
                lowerParticipantId,
                firstSessionId,
                firstPlayerId,
                sameJoinedAt
        );

        insertLegacyParticipant(
                schemaName,
                laterParticipantId,
                firstSessionId,
                thirdPlayerId,
                laterJoinedAt
        );

        insertLegacyParticipant(
                schemaName,
                otherSessionParticipantId,
                secondSessionId,
                fourthPlayerId,
                laterJoinedAt
        );

        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .target("7")
                .load()
                .migrate();

        assertThat(participantCodeColumnCount(schemaName))
                .isEqualTo(1);

        assertThat(participantCode(
                schemaName,
                lowerParticipantId
        )).isEqualTo(1);

        assertThat(participantCode(
                schemaName,
                higherParticipantId
        )).isEqualTo(2);

        assertThat(participantCode(
                schemaName,
                laterParticipantId
        )).isEqualTo(3);

        assertThat(participantCode(
                schemaName,
                otherSessionParticipantId
        )).isEqualTo(1);

        Integer nullCodeCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM %s.session_participants
                WHERE participant_code IS NULL
                """.formatted(schemaName),
                Integer.class
        );

        assertThat(nullCodeCount).isZero();

    } finally {
        jdbcTemplate.execute(
                "DROP SCHEMA IF EXISTS "
                        + schemaName
                        + " CASCADE"
        );
    }
}

@Test
    void personalAccessTokenMigrationBackfillsExistingParticipantsGloballyUniquely() {
    String schemaName =
            "personal_access_token_backfill_"
                    + UUID.randomUUID().toString().replace("-", "");
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    UUID venueId = UUID.randomUUID();
    UUID firstSessionId = UUID.randomUUID();
    UUID secondSessionId = UUID.randomUUID();
    UUID firstParticipantId = UUID.randomUUID();
    UUID secondParticipantId = UUID.randomUUID();
    UUID thirdParticipantId = UUID.randomUUID();

    try {
        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .target("7")
                .load()
                .migrate();

        insertLegacyVenue(schemaName, venueId, now);
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        UUID thirdPlayerId = UUID.randomUUID();
        insertLegacyPlayer(schemaName, firstPlayerId, "Player 1", now);
        insertLegacyPlayer(schemaName, secondPlayerId, "Player 2", now);
        insertLegacyPlayer(schemaName, thirdPlayerId, "Player 3", now);
        insertLegacySession(
                schemaName, firstSessionId, venueId, "Session A", now
        );
        insertLegacySession(
                schemaName, secondSessionId, venueId, "Session B", now
        );
        insertV7Participant(
                schemaName, firstParticipantId, firstSessionId,
                firstPlayerId, 1, now
        );
        insertV7Participant(
                schemaName, secondParticipantId, firstSessionId,
                secondPlayerId, 2, now.plusSeconds(1)
        );
        insertV7Participant(
                schemaName, thirdParticipantId, secondSessionId,
                thirdPlayerId, 1, now.plusSeconds(2)
        );

        assertThat(personalAccessTokenColumnCount(schemaName)).isZero();

        Flyway.configure()
                .dataSource(
                        POSTGRESQL.getJdbcUrl(),
                        POSTGRESQL.getUsername(),
                        POSTGRESQL.getPassword()
                )
                .locations("classpath:db/migration")
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .target("8")
                .load()
                .migrate();

        assertThat(personalAccessTokenColumnCount(schemaName)).isEqualTo(1);
        List<UUID> tokens = jdbcTemplate.queryForList(
                """
                SELECT personal_access_token
                FROM %s.session_participants
                ORDER BY id
                """.formatted(schemaName),
                UUID.class
        );
        assertThat(tokens)
                .hasSize(3)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    } finally {
        jdbcTemplate.execute(
                "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE"
        );
    }
}

    @Test
    void playerCodeMigrationBackfillsByCreationTimeThenIdAndAdvancesSequence() {
        String schemaName = "player_code_backfill_"
                + UUID.randomUUID().toString().replace("-", "");
        UUID lowerId = UUID.fromString(
                "00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString(
                "00000000-0000-0000-0000-000000000002");
        UUID laterId = UUID.fromString(
                "00000000-0000-0000-0000-000000000003");
        Instant sameCreatedAt = Instant.parse("2026-01-01T08:00:00Z");
        Instant laterCreatedAt = sameCreatedAt.plusSeconds(1);

        try {
            Flyway.configure()
                    .dataSource(
                            POSTGRESQL.getJdbcUrl(),
                            POSTGRESQL.getUsername(),
                            POSTGRESQL.getPassword()
                    )
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .target("8")
                    .load()
                    .migrate();

            assertThat(playerCodeColumnCount(schemaName)).isZero();

            insertLegacyPlayer(
                    schemaName, higherId, "Higher UUID", sameCreatedAt
            );
            insertLegacyPlayer(
                    schemaName, lowerId, "Lower UUID", sameCreatedAt
            );
            insertLegacyPlayer(
                    schemaName, laterId, "Later Player", laterCreatedAt
            );

            Flyway.configure()
                    .dataSource(
                            POSTGRESQL.getJdbcUrl(),
                            POSTGRESQL.getUsername(),
                            POSTGRESQL.getPassword()
                    )
                    .locations("classpath:db/migration")
                    .schemas(schemaName)
                    .defaultSchema(schemaName)
                    .target("9")
                    .load()
                    .migrate();

            assertThat(playerCodeColumnCount(schemaName)).isEqualTo(1);
            assertThat(playerCode(schemaName, lowerId)).isEqualTo(1L);
            assertThat(playerCode(schemaName, higherId)).isEqualTo(2L);
            assertThat(playerCode(schemaName, laterId)).isEqualTo(3L);

            List<Long> codes = jdbcTemplate.queryForList(
                    "SELECT player_code FROM %s.players ORDER BY player_code"
                            .formatted(schemaName),
                    Long.class
            );
            assertThat(codes)
                    .containsExactly(1L, 2L, 3L)
                    .doesNotContainNull()
                    .doesNotHaveDuplicates();

            Long nextCode = jdbcTemplate.queryForObject(
                    "SELECT nextval('%s.players_player_code_seq')"
                            .formatted(schemaName),
                    Long.class
            );
            assertThat(nextCode).isEqualTo(4L);
        } finally {
            jdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS " + schemaName + " CASCADE"
            );
        }
    }

private int participantCodeColumnCount(String schemaName) {
    Integer count = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = 'session_participants'
              AND column_name = 'participant_code'
            """,
            Integer.class,
            schemaName
    );

    return count == null ? 0 : count;
}

    private int personalAccessTokenColumnCount(String schemaName) {
    Integer count = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM information_schema.columns
            WHERE table_schema = ?
              AND table_name = 'session_participants'
              AND column_name = 'personal_access_token'
              AND data_type = 'uuid'
              AND is_nullable = 'NO'
            """,
            Integer.class,
            schemaName
    );

        return count == null ? 0 : count;
    }

    private int playerCodeColumnCount(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = 'players'
                  AND column_name = 'player_code'
                  AND data_type = 'bigint'
                  AND is_nullable = 'NO'
                """,
                Integer.class,
                schemaName
        );

        return count == null ? 0 : count;
    }

    private long playerCode(String schemaName, UUID playerId) {
        Long playerCode = jdbcTemplate.queryForObject(
                "SELECT player_code FROM %s.players WHERE id = ?"
                        .formatted(schemaName),
                Long.class,
                playerId
        );
        return playerCode;
    }

private int participantCode(
        String schemaName,
        UUID participantId
) {
    Integer participantCode = jdbcTemplate.queryForObject(
            """
            SELECT participant_code
            FROM %s.session_participants
            WHERE id = ?
            """.formatted(schemaName),
            Integer.class,
            participantId
    );

    return participantCode;
}

private void insertLegacyPlayer(
        String schemaName,
        UUID playerId,
        String displayName,
        Instant now
) {
    jdbcTemplate.update(
            """
            INSERT INTO %s.players (
                id,
                display_name,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?)
            """.formatted(schemaName),
            playerId,
            displayName,
            Timestamp.from(now),
            Timestamp.from(now)
    );
}

private void insertLegacyVenue(
        String schemaName,
        UUID venueId,
        Instant now
) {
    jdbcTemplate.update(
            """
            INSERT INTO %s.venues (
                id,
                name,
                location_text,
                active,
                created_at,
                updated_at
            )
            VALUES (?, ?, NULL, TRUE, ?, ?)
            """.formatted(schemaName),
            venueId,
            "Migration Test Venue",
            Timestamp.from(now),
            Timestamp.from(now)
    );
}

private void insertLegacySession(
        String schemaName,
        UUID sessionId,
        UUID venueId,
        String title,
        Instant now
) {
    jdbcTemplate.update(
            """
            INSERT INTO %s.sessions (
                id,
                venue_id,
                title,
                sport_code,
                match_format,
                planned_start_at,
                planned_end_at,
                status,
                started_at,
                completed_at,
                cancelled_at,
                version,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                ?,
                ?,
                'BADMINTON',
                'DOUBLES',
                ?,
                ?,
                'PLANNED',
                NULL,
                NULL,
                NULL,
                0,
                ?,
                ?
            )
            """.formatted(schemaName),
            sessionId,
            venueId,
            title,
            Timestamp.from(now.plusSeconds(3600)),
            Timestamp.from(now.plusSeconds(7200)),
            Timestamp.from(now),
            Timestamp.from(now)
    );
}

private void insertLegacyParticipant(
        String schemaName,
        UUID participantId,
        UUID sessionId,
        UUID playerId,
        Instant joinedAt
) {
    jdbcTemplate.update(
            """
            INSERT INTO %s.session_participants (
                id,
                session_id,
                player_id,
                status,
                joined_at,
                checked_in_at,
                waiting_since,
                paused_at,
                total_paused_seconds,
                left_at,
                version,
                created_at,
                updated_at
            )
            VALUES (
                ?,
                ?,
                ?,
                'REGISTERED',
                ?,
                NULL,
                NULL,
                NULL,
                0,
                NULL,
                0,
                ?,
                ?
            )
            """.formatted(schemaName),
            participantId,
            sessionId,
            playerId,
            Timestamp.from(joinedAt),
            Timestamp.from(joinedAt),
            Timestamp.from(joinedAt)
    );
}

private void insertV7Participant(
        String schemaName,
        UUID participantId,
        UUID sessionId,
        UUID playerId,
        int participantCode,
        Instant joinedAt
) {
    jdbcTemplate.update(
            """
            INSERT INTO %s.session_participants (
                id,
                session_id,
                player_id,
                participant_code,
                status,
                joined_at,
                checked_in_at,
                waiting_since,
                paused_at,
                total_paused_seconds,
                left_at,
                version,
                created_at,
                updated_at
            )
            VALUES (
                ?, ?, ?, ?, 'REGISTERED', ?, NULL, NULL, NULL, 0, NULL, 0, ?, ?
            )
            """.formatted(schemaName),
            participantId,
            sessionId,
            playerId,
            participantCode,
            Timestamp.from(joinedAt),
            Timestamp.from(joinedAt),
            Timestamp.from(joinedAt)
    );
}
}

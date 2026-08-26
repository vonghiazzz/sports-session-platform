package com.sportssession.platform.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywaySchemaIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesRuntimeTablesThroughRatingFoundation() {
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
                "rating_events");
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
}

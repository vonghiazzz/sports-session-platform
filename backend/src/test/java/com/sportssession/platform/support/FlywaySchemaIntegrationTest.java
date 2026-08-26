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
    void flywayCreatesFoundationSessionAndMatchRuntimeTablesOnly() {
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
                "match_participants");
        assertThat(tables).doesNotContain(
                "match_results",
                "ratings",
                "rating_events",
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
    }
}

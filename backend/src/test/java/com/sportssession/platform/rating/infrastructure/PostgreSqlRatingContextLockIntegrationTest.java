package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlRatingContextLockIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void transactionScopedLockContendsAcrossConnectionsAndReleasesOnRollback()
            throws Exception {
        try (Connection owner = dataSource.getConnection();
             Connection contender = dataSource.getConnection()) {
            owner.setAutoCommit(false);
            contender.setAutoCommit(false);

            assertThat(tryAcquire(owner)).isTrue();
            assertThat(tryAcquire(contender)).isFalse();

            owner.rollback();
            assertThat(tryAcquire(contender)).isTrue();
            contender.rollback();
        }

        try (Connection nextTransaction = dataSource.getConnection()) {
            nextTransaction.setAutoCommit(false);
            assertThat(tryAcquire(nextTransaction)).isTrue();
            nextTransaction.rollback();
        }
    }

    private boolean tryAcquire(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_try_advisory_xact_lock(?, ?)"
        )) {
            statement.setInt(
                    1,
                    PostgreSqlRatingContextLock.RATING_V1_LOCK_NAMESPACE
            );
            statement.setInt(
                    2,
                    PostgreSqlRatingContextLock.BADMINTON_DOUBLES_CONTEXT_KEY
            );
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getBoolean(1);
            }
        }
    }
}

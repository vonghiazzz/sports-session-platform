package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.application.RatingContextLock;
import com.sportssession.platform.rating.application.UnsupportedRatingContextException;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PostgreSqlRatingContextLock implements RatingContextLock {

    // Stable four-byte namespace "RTV1". Never derive advisory keys by hashing.
    static final int RATING_V1_LOCK_NAMESPACE = 1_381_258_801;
    static final int BADMINTON_DOUBLES_CONTEXT_KEY = 1;

    private static final String TRY_ACQUIRE_SQL =
            "SELECT pg_try_advisory_xact_lock(?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlRatingContextLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(SportCode sportCode, MatchFormat matchFormat) {
        requireSupportedContext(sportCode, matchFormat);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Rating advisory lock requires an active transaction"
            );
        }
        Boolean acquired = jdbcTemplate.queryForObject(
                TRY_ACQUIRE_SQL,
                Boolean.class,
                RATING_V1_LOCK_NAMESPACE,
                BADMINTON_DOUBLES_CONTEXT_KEY
        );
        return Boolean.TRUE.equals(acquired);
    }

    private void requireSupportedContext(
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        if (sportCode != SportCode.BADMINTON
                || matchFormat != MatchFormat.DOUBLES) {
            throw new UnsupportedRatingContextException(
                    "Rating V1 lock supports only BADMINTON + DOUBLES"
            );
        }
    }
}

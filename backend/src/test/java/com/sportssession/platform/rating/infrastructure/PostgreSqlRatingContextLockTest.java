package com.sportssession.platform.rating.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgreSqlRatingContextLockTest {

    @Test
    void usesDocumentedStableRatingV1Keys() {
        assertThat(PostgreSqlRatingContextLock.RATING_V1_LOCK_NAMESPACE)
                .isEqualTo(1_381_258_801);
        assertThat(PostgreSqlRatingContextLock.BADMINTON_DOUBLES_CONTEXT_KEY)
                .isEqualTo(1);
    }
}

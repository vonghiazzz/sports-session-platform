package com.sportssession.platform.matchplan.domain;

import com.sportssession.platform.match.domain.MatchSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchPlanTest {

    @Test
    void queuedPlanMovesReordersStartsAndCancelsWithoutRuntimeState() {
        Instant createdAt = Instant.parse("2026-09-06T01:00:00Z");
        MatchPlan plan = MatchPlan.queueManual(
                UUID.randomUUID(), UUID.randomUUID(), 2, createdAt
        );

        assertThat(plan.source()).isEqualTo(MatchSource.MANUAL);
        assertThat(plan.status()).isEqualTo(MatchPlanStatus.QUEUED);
        assertThat(plan.reorder(1, createdAt.plusSeconds(1)).queuePosition())
                .isEqualTo(1);
        assertThat(plan.move(UUID.randomUUID(), 3, createdAt.plusSeconds(2))
                .queuePosition()).isEqualTo(3);

        UUID matchId = UUID.randomUUID();
        MatchPlan started = plan.start(matchId, createdAt.plusSeconds(3));
        assertThat(started.status()).isEqualTo(MatchPlanStatus.STARTED);
        assertThat(started.queuePosition()).isNull();
        assertThat(started.startedMatchId()).isEqualTo(matchId);

        MatchPlan cancelled = plan.cancel(createdAt.plusSeconds(4));
        assertThat(cancelled.status()).isEqualTo(MatchPlanStatus.CANCELLED);
        assertThat(cancelled.queuePosition()).isNull();
        assertThat(cancelled.startedMatchId()).isNull();
    }

    @Test
    void terminalPlanRejectsFurtherQueueActions() {
        Instant now = Instant.parse("2026-09-06T01:00:00Z");
        MatchPlan started = MatchPlan.queueManual(
                UUID.randomUUID(), UUID.randomUUID(), 1, now
        ).start(UUID.randomUUID(), now.plusSeconds(1));

        assertThatThrownBy(() -> started.cancel(now.plusSeconds(2)))
                .isInstanceOf(MatchPlanConflictException.class);
        assertThatThrownBy(() -> started.reorder(2, now.plusSeconds(2)))
                .isInstanceOf(MatchPlanConflictException.class);
    }

    @Test
    void recommendationSourceChangesOnlyWhenCompositionIsEdited() {
        Instant now = Instant.parse("2026-09-06T01:00:00Z");
        MatchPlan recommendation = MatchPlan.queueRecommendation(
                UUID.randomUUID(), UUID.randomUUID(), 1, now
        );

        assertThat(recommendation.source())
                .isEqualTo(MatchSource.RECOMMENDATION);
        assertThat(recommendation.edit(now.plusSeconds(1)).source())
                .isEqualTo(MatchSource.MODIFIED_RECOMMENDATION);
        assertThat(recommendation.move(
                UUID.randomUUID(), 1, now.plusSeconds(1)
        ).source()).isEqualTo(MatchSource.RECOMMENDATION);
        assertThat(recommendation.reorder(1, now.plusSeconds(1)).source())
                .isEqualTo(MatchSource.RECOMMENDATION);
        assertThat(MatchPlan.queueManual(
                UUID.randomUUID(), UUID.randomUUID(), 1, now
        ).edit(now.plusSeconds(1)).source()).isEqualTo(MatchSource.MANUAL);
    }
}

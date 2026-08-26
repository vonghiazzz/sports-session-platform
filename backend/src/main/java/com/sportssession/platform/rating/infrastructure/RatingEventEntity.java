package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.rating.domain.RatingOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rating_events")
public class RatingEventEntity {

    @Id
    private UUID id;

    @Column(name = "player_rating_id", nullable = false)
    private UUID playerRatingId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "result_version", nullable = false)
    private int resultVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 16)
    private RatingOutcome outcome;

    @Column(name = "before_rating", nullable = false, precision = 18, scale = 9)
    private BigDecimal beforeRating;

    @Column(name = "after_rating", nullable = false, precision = 18, scale = 9)
    private BigDecimal afterRating;

    @Column(name = "before_uncertainty", nullable = false, precision = 18, scale = 9)
    private BigDecimal beforeUncertainty;

    @Column(name = "after_uncertainty", nullable = false, precision = 18, scale = 9)
    private BigDecimal afterUncertainty;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RatingEventEntity() {
    }

    RatingEventEntity(
            UUID id,
            UUID playerRatingId,
            UUID matchId,
            int resultVersion,
            RatingOutcome outcome,
            BigDecimal beforeRating,
            BigDecimal afterRating,
            BigDecimal beforeUncertainty,
            BigDecimal afterUncertainty,
            String algorithmVersion,
            Instant createdAt
    ) {
        this.id = id;
        this.playerRatingId = playerRatingId;
        this.matchId = matchId;
        this.resultVersion = resultVersion;
        this.outcome = outcome;
        this.beforeRating = beforeRating;
        this.afterRating = afterRating;
        this.beforeUncertainty = beforeUncertainty;
        this.afterUncertainty = afterUncertainty;
        this.algorithmVersion = algorithmVersion;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerRatingId() {
        return playerRatingId;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public int getResultVersion() {
        return resultVersion;
    }

    public RatingOutcome getOutcome() {
        return outcome;
    }

    public BigDecimal getBeforeRating() {
        return beforeRating;
    }

    public BigDecimal getAfterRating() {
        return afterRating;
    }

    public BigDecimal getBeforeUncertainty() {
        return beforeUncertainty;
    }

    public BigDecimal getAfterUncertainty() {
        return afterUncertainty;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

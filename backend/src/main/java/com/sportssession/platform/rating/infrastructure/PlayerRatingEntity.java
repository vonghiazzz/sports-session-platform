package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player_ratings")
public class PlayerRatingEntity {

    @Id
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sport_code", nullable = false, length = 32)
    private SportCode sportCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_format", nullable = false, length = 32)
    private MatchFormat matchFormat;

    @Column(name = "rating_value", nullable = false, precision = 18, scale = 9)
    private BigDecimal ratingValue;

    @Column(name = "uncertainty", nullable = false, precision = 18, scale = 9)
    private BigDecimal uncertainty;

    @Column(name = "rated_matches", nullable = false)
    private int ratedMatches;

    @Enumerated(EnumType.STRING)
    @Column(name = "initial_skill_level", nullable = false, length = 32)
    private SkillLevel initialSkillLevel;

    @Column(name = "algorithm_version", nullable = false, length = 64)
    private String algorithmVersion;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerRatingEntity() {
    }

    PlayerRatingEntity(
            UUID id,
            UUID playerId,
            SportCode sportCode,
            MatchFormat matchFormat,
            BigDecimal ratingValue,
            BigDecimal uncertainty,
            int ratedMatches,
            SkillLevel initialSkillLevel,
            String algorithmVersion,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.playerId = playerId;
        this.sportCode = sportCode;
        this.matchFormat = matchFormat;
        this.ratingValue = ratingValue;
        this.uncertainty = uncertainty;
        this.ratedMatches = ratedMatches;
        this.initialSkillLevel = initialSkillLevel;
        this.algorithmVersion = algorithmVersion;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public SportCode getSportCode() {
        return sportCode;
    }

    public MatchFormat getMatchFormat() {
        return matchFormat;
    }

    public BigDecimal getRatingValue() {
        return ratingValue;
    }

    public BigDecimal getUncertainty() {
        return uncertainty;
    }

    public int getRatedMatches() {
        return ratedMatches;
    }

    public SkillLevel getInitialSkillLevel() {
        return initialSkillLevel;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

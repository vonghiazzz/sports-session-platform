CREATE TABLE player_ratings (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    sport_code VARCHAR(32) NOT NULL,
    match_format VARCHAR(32) NOT NULL,
    rating_value NUMERIC(18,9) NOT NULL,
    uncertainty NUMERIC(18,9) NOT NULL,
    rated_matches INTEGER NOT NULL,
    initial_skill_level VARCHAR(32) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_player_ratings_player
        FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT uk_player_ratings_player_sport_format
        UNIQUE (player_id, sport_code, match_format),
    CONSTRAINT chk_player_ratings_sport_code
        CHECK (sport_code IN ('BADMINTON')),
    CONSTRAINT chk_player_ratings_match_format
        CHECK (match_format IN ('DOUBLES')),
    CONSTRAINT chk_player_ratings_initial_skill_level
        CHECK (initial_skill_level IN (
            'WEAK',
            'WEAK_PLUS',
            'INTERMEDIATE_MINUS',
            'INTERMEDIATE',
            'INTERMEDIATE_PLUS',
            'GOOD'
        )),
    CONSTRAINT chk_player_ratings_rating_value_not_nan
        CHECK (rating_value <> 'NaN'::numeric),
    CONSTRAINT chk_player_ratings_uncertainty_positive
        CHECK (uncertainty <> 'NaN'::numeric AND uncertainty > 0),
    CONSTRAINT chk_player_ratings_rated_matches_non_negative
        CHECK (rated_matches >= 0),
    CONSTRAINT chk_player_ratings_version_non_negative
        CHECK (version >= 0),
    CONSTRAINT chk_player_ratings_algorithm_version_not_blank
        CHECK (btrim(algorithm_version) <> ''),
    CONSTRAINT chk_player_ratings_timestamp_order
        CHECK (updated_at >= created_at)
);

CREATE TABLE rating_events (
    id UUID PRIMARY KEY,
    player_rating_id UUID NOT NULL,
    match_id UUID NOT NULL,
    result_version INTEGER NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    before_rating NUMERIC(18,9) NOT NULL,
    after_rating NUMERIC(18,9) NOT NULL,
    before_uncertainty NUMERIC(18,9) NOT NULL,
    after_uncertainty NUMERIC(18,9) NOT NULL,
    algorithm_version VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_rating_events_player_rating
        FOREIGN KEY (player_rating_id) REFERENCES player_ratings (id),
    CONSTRAINT fk_rating_events_match
        FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT uk_rating_events_match_version_player_rating
        UNIQUE (match_id, result_version, player_rating_id),
    CONSTRAINT chk_rating_events_result_version_positive
        CHECK (result_version >= 1),
    CONSTRAINT chk_rating_events_outcome
        CHECK (outcome IN ('WIN', 'LOSS')),
    CONSTRAINT chk_rating_events_before_rating_not_nan
        CHECK (before_rating <> 'NaN'::numeric),
    CONSTRAINT chk_rating_events_after_rating_not_nan
        CHECK (after_rating <> 'NaN'::numeric),
    CONSTRAINT chk_rating_events_before_uncertainty_positive
        CHECK (before_uncertainty <> 'NaN'::numeric AND before_uncertainty > 0),
    CONSTRAINT chk_rating_events_after_uncertainty_positive
        CHECK (after_uncertainty <> 'NaN'::numeric AND after_uncertainty > 0),
    CONSTRAINT chk_rating_events_algorithm_version_not_blank
        CHECK (btrim(algorithm_version) <> '')
);

CREATE INDEX idx_rating_events_player_rating_id_created_at_id
    ON rating_events (player_rating_id, created_at, id);

CREATE INDEX idx_matches_completed_at_id_result_version
    ON matches (completed_at, id, result_version)
    WHERE status = 'COMPLETED';

CREATE TABLE matches (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    session_court_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    winner_team VARCHAR(32),
    team_a_score INTEGER,
    team_b_score INTEGER,
    result_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_matches_session
        FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_matches_session_court
        FOREIGN KEY (session_court_id) REFERENCES session_courts (id),
    CONSTRAINT chk_matches_status CHECK (
        status IN ('CREATED', 'PLAYING', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_matches_source CHECK (
        source IN ('MANUAL', 'RECOMMENDATION', 'MODIFIED_RECOMMENDATION')
    ),
    CONSTRAINT chk_matches_winner_team CHECK (
        winner_team IS NULL OR winner_team IN ('A', 'B')
    ),
    CONSTRAINT chk_matches_result_version_non_negative CHECK (result_version >= 0),
    CONSTRAINT chk_matches_version_non_negative CHECK (version >= 0),
    CONSTRAINT chk_matches_score_pair CHECK (
        (team_a_score IS NULL AND team_b_score IS NULL)
        OR
        (team_a_score IS NOT NULL AND team_b_score IS NOT NULL)
    ),
    CONSTRAINT chk_matches_scores_non_negative CHECK (
        (team_a_score IS NULL OR team_a_score >= 0)
        AND (team_b_score IS NULL OR team_b_score >= 0)
    ),
    CONSTRAINT chk_matches_scores_not_tied CHECK (
        team_a_score IS NULL OR team_a_score <> team_b_score
    ),
    CONSTRAINT chk_matches_winner_score_consistency CHECK (
        winner_team IS NULL
        OR team_a_score IS NULL
        OR (winner_team = 'A' AND team_a_score > team_b_score)
        OR (winner_team = 'B' AND team_b_score > team_a_score)
    ),
    CONSTRAINT chk_matches_state_consistency CHECK (
        (status <> 'CREATED' OR (
            started_at IS NULL
            AND completed_at IS NULL
            AND cancelled_at IS NULL
            AND winner_team IS NULL
            AND team_a_score IS NULL
            AND team_b_score IS NULL
            AND result_version = 0
        ))
        AND
        (status <> 'PLAYING' OR (
            started_at IS NOT NULL
            AND completed_at IS NULL
            AND cancelled_at IS NULL
            AND winner_team IS NULL
            AND team_a_score IS NULL
            AND team_b_score IS NULL
            AND result_version = 0
        ))
        AND
        (status <> 'COMPLETED' OR (
            started_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND cancelled_at IS NULL
            AND winner_team IS NOT NULL
            AND result_version >= 1
        ))
        AND
        (status <> 'CANCELLED' OR (
            completed_at IS NULL
            AND cancelled_at IS NOT NULL
            AND winner_team IS NULL
            AND team_a_score IS NULL
            AND team_b_score IS NULL
            AND result_version = 0
        ))
    ),
    CONSTRAINT chk_matches_completion_order CHECK (
        completed_at IS NULL OR completed_at >= started_at
    ),
    CONSTRAINT chk_matches_cancellation_order CHECK (
        cancelled_at IS NULL
        OR started_at IS NULL
        OR cancelled_at >= started_at
    )
);

CREATE INDEX idx_matches_session_id ON matches (session_id);
CREATE INDEX idx_matches_session_court_id ON matches (session_court_id);
CREATE UNIQUE INDEX uk_matches_playing_session_court
    ON matches (session_court_id)
    WHERE status = 'PLAYING';

CREATE TABLE match_participants (
    id UUID PRIMARY KEY,
    match_id UUID NOT NULL,
    session_participant_id UUID NOT NULL,
    team_side VARCHAR(32) NOT NULL,
    team_slot INTEGER NOT NULL,
    CONSTRAINT fk_match_participants_match
        FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_participants_session_participant
        FOREIGN KEY (session_participant_id) REFERENCES session_participants (id),
    CONSTRAINT chk_match_participants_team_side CHECK (team_side IN ('A', 'B')),
    CONSTRAINT chk_match_participants_team_slot CHECK (team_slot IN (1, 2)),
    CONSTRAINT uk_match_participants_match_session_participant
        UNIQUE (match_id, session_participant_id),
    CONSTRAINT uk_match_participants_match_team_slot
        UNIQUE (match_id, team_side, team_slot)
);

CREATE INDEX idx_match_participants_session_participant_id
    ON match_participants (session_participant_id);

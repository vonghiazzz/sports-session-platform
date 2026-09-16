CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    sport_code VARCHAR(32) NOT NULL,
    match_format VARCHAR(32) NOT NULL,
    planned_start_at TIMESTAMPTZ NOT NULL,
    planned_end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_sessions_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
    CONSTRAINT chk_sessions_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT chk_sessions_sport_code CHECK (sport_code IN ('BADMINTON')),
    CONSTRAINT chk_sessions_match_format CHECK (match_format IN ('DOUBLES')),
    CONSTRAINT chk_sessions_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_sessions_planned_time_range CHECK (planned_end_at > planned_start_at),
    CONSTRAINT chk_sessions_state_timestamps CHECK (
        (status = 'PLANNED'
            AND started_at IS NULL
            AND completed_at IS NULL
            AND cancelled_at IS NULL)
        OR
        (status = 'IN_PROGRESS'
            AND started_at IS NOT NULL
            AND completed_at IS NULL
            AND cancelled_at IS NULL)
        OR
        (status = 'COMPLETED'
            AND started_at IS NOT NULL
            AND completed_at IS NOT NULL
            AND cancelled_at IS NULL)
        OR
        (status = 'CANCELLED'
            AND completed_at IS NULL
            AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT chk_sessions_completion_order CHECK (
        completed_at IS NULL OR completed_at >= started_at
    ),
    CONSTRAINT chk_sessions_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_sessions_venue_id ON sessions (venue_id);

CREATE TABLE session_participants (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    player_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    checked_in_at TIMESTAMPTZ,
    waiting_since TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    total_paused_seconds BIGINT NOT NULL,
    left_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_session_participants_session
        FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_session_participants_player
        FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT uk_session_participants_session_player UNIQUE (session_id, player_id),
    CONSTRAINT chk_session_participants_status CHECK (
        status IN ('REGISTERED', 'WAITING', 'PLAYING', 'PAUSED', 'LEFT')
    ),
    CONSTRAINT chk_session_participants_total_paused_non_negative
        CHECK (total_paused_seconds >= 0),
    CONSTRAINT chk_session_participants_state_timestamps CHECK (
        (status = 'REGISTERED'
            AND checked_in_at IS NULL
            AND waiting_since IS NULL
            AND paused_at IS NULL
            AND left_at IS NULL)
        OR
        (status = 'WAITING'
            AND checked_in_at IS NOT NULL
            AND waiting_since IS NOT NULL
            AND paused_at IS NULL
            AND left_at IS NULL)
        OR
        (status = 'PLAYING'
            AND checked_in_at IS NOT NULL
            AND waiting_since IS NULL
            AND paused_at IS NULL
            AND left_at IS NULL)
        OR
        (status = 'PAUSED'
            AND checked_in_at IS NOT NULL
            AND waiting_since IS NULL
            AND paused_at IS NOT NULL
            AND left_at IS NULL)
        OR
        (status = 'LEFT'
            AND waiting_since IS NULL
            AND paused_at IS NULL
            AND left_at IS NOT NULL)
    ),
    CONSTRAINT chk_session_participants_joined_order CHECK (
        (checked_in_at IS NULL OR checked_in_at >= joined_at)
        AND (waiting_since IS NULL OR waiting_since >= joined_at)
        AND (paused_at IS NULL OR paused_at >= joined_at)
        AND (left_at IS NULL OR left_at >= joined_at)
    ),
    CONSTRAINT chk_session_participants_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_session_participants_session_id
    ON session_participants (session_id);
CREATE INDEX idx_session_participants_player_id
    ON session_participants (player_id);

CREATE TABLE session_courts (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    court_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_session_courts_session
        FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_session_courts_court
        FOREIGN KEY (court_id) REFERENCES courts (id),
    CONSTRAINT uk_session_courts_session_court UNIQUE (session_id, court_id),
    CONSTRAINT chk_session_courts_status CHECK (
        status IN ('AVAILABLE', 'PLAYING', 'UNAVAILABLE')
    ),
    CONSTRAINT chk_session_courts_version_non_negative CHECK (version >= 0)
);

CREATE INDEX idx_session_courts_session_id ON session_courts (session_id);
CREATE INDEX idx_session_courts_court_id ON session_courts (court_id);

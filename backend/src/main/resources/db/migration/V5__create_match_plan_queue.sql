ALTER TABLE session_courts
    ADD CONSTRAINT uk_session_courts_id_session UNIQUE (id, session_id);

ALTER TABLE session_participants
    ADD CONSTRAINT uk_session_participants_id_session UNIQUE (id, session_id);

CREATE TABLE match_plans (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    session_court_id UUID NOT NULL,
    source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    queue_position INTEGER,
    started_match_id UUID,
    started_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_match_plans_session
        FOREIGN KEY (session_id) REFERENCES sessions (id),
    CONSTRAINT fk_match_plans_session_court
        FOREIGN KEY (session_court_id, session_id)
        REFERENCES session_courts (id, session_id),
    CONSTRAINT fk_match_plans_started_match
        FOREIGN KEY (started_match_id) REFERENCES matches (id),
    CONSTRAINT uk_match_plans_id_session UNIQUE (id, session_id),
    CONSTRAINT uk_match_plans_started_match UNIQUE (started_match_id),
    CONSTRAINT chk_match_plans_source CHECK (
        source IN ('MANUAL', 'RECOMMENDATION', 'MODIFIED_RECOMMENDATION')
    ),
    CONSTRAINT chk_match_plans_status CHECK (
        status IN ('QUEUED', 'STARTED', 'CANCELLED')
    ),
    CONSTRAINT chk_match_plans_version_non_negative CHECK (version >= 0),
    CONSTRAINT chk_match_plans_state_consistency CHECK (
        (status = 'QUEUED'
            AND queue_position > 0
            AND started_match_id IS NULL
            AND started_at IS NULL
            AND cancelled_at IS NULL)
        OR
        (status = 'STARTED'
            AND queue_position IS NULL
            AND started_match_id IS NOT NULL
            AND started_at IS NOT NULL
            AND cancelled_at IS NULL)
        OR
        (status = 'CANCELLED'
            AND queue_position IS NULL
            AND started_match_id IS NULL
            AND started_at IS NULL
            AND cancelled_at IS NOT NULL)
    )
);

CREATE INDEX idx_match_plans_session_id ON match_plans (session_id);
CREATE INDEX idx_match_plans_session_court_status_position
    ON match_plans (session_court_id, status, queue_position);
CREATE UNIQUE INDEX uk_match_plans_queued_court_position
    ON match_plans (session_court_id, queue_position)
    WHERE status = 'QUEUED';

CREATE TABLE match_plan_participants (
    id UUID PRIMARY KEY,
    match_plan_id UUID NOT NULL,
    session_id UUID NOT NULL,
    session_participant_id UUID NOT NULL,
    team_side VARCHAR(32) NOT NULL,
    team_slot INTEGER NOT NULL,
    CONSTRAINT fk_match_plan_participants_plan_session
        FOREIGN KEY (match_plan_id, session_id)
        REFERENCES match_plans (id, session_id),
    CONSTRAINT fk_match_plan_participants_participant_session
        FOREIGN KEY (session_participant_id, session_id)
        REFERENCES session_participants (id, session_id),
    CONSTRAINT chk_match_plan_participants_team_side
        CHECK (team_side IN ('A', 'B')),
    CONSTRAINT chk_match_plan_participants_team_slot
        CHECK (team_slot IN (1, 2)),
    CONSTRAINT uk_match_plan_participants_plan_participant
        UNIQUE (match_plan_id, session_participant_id),
    CONSTRAINT uk_match_plan_participants_plan_team_slot
        UNIQUE (match_plan_id, team_side, team_slot)
);

CREATE INDEX idx_match_plan_participants_session_participant
    ON match_plan_participants (session_participant_id);

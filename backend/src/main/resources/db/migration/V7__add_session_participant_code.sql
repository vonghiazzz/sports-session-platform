ALTER TABLE session_participants
    ADD COLUMN participant_code INTEGER;

WITH ranked AS (
    SELECT
        id,
        (ROW_NUMBER() OVER (
            PARTITION BY session_id
            ORDER BY joined_at ASC, id ASC
        ))::INTEGER AS participant_code
    FROM session_participants
)
UPDATE session_participants participant
SET participant_code = ranked.participant_code
FROM ranked
WHERE participant.id = ranked.id;

ALTER TABLE session_participants
    ALTER COLUMN participant_code SET NOT NULL,
    ADD CONSTRAINT ck_session_participants_code_positive
        CHECK (participant_code > 0),
    ADD CONSTRAINT uk_session_participants_session_code
        UNIQUE (session_id, participant_code);

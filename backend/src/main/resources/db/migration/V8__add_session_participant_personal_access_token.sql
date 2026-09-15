ALTER TABLE session_participants
    ADD COLUMN personal_access_token UUID;

UPDATE session_participants
SET personal_access_token = gen_random_uuid()
WHERE personal_access_token IS NULL;

ALTER TABLE session_participants
    ALTER COLUMN personal_access_token SET NOT NULL,
    ADD CONSTRAINT uk_session_participants_personal_access_token
        UNIQUE (personal_access_token);

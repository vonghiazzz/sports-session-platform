ALTER TABLE session_participants
    ADD COLUMN buddy_pair_id UUID;

CREATE INDEX idx_session_participants_session_buddy_pair
    ON session_participants (session_id, buddy_pair_id)
    WHERE buddy_pair_id IS NOT NULL;

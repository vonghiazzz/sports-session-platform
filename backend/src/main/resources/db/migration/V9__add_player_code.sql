CREATE SEQUENCE players_player_code_seq AS BIGINT;

ALTER TABLE players
    ADD COLUMN player_code BIGINT;

WITH ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            ORDER BY created_at ASC, id ASC
        ) AS player_code
    FROM players
)
UPDATE players player
SET player_code = ranked.player_code
FROM ranked
WHERE player.id = ranked.id;

SELECT setval(
    'players_player_code_seq',
    COALESCE((SELECT MAX(player_code) FROM players), 0) + 1,
    FALSE
);

ALTER SEQUENCE players_player_code_seq
    OWNED BY players.player_code;

ALTER TABLE players
    ALTER COLUMN player_code SET DEFAULT nextval('players_player_code_seq'),
    ALTER COLUMN player_code SET NOT NULL,
    ADD CONSTRAINT ck_players_player_code_positive
        CHECK (player_code > 0),
    ADD CONSTRAINT uk_players_player_code
        UNIQUE (player_code);

CREATE TABLE players (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_players_display_name_not_blank CHECK (btrim(display_name) <> '')
);

CREATE TABLE player_sport_profiles (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    sport_code VARCHAR(32) NOT NULL,
    skill_level VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_player_sport_profiles_player
        FOREIGN KEY (player_id) REFERENCES players (id),
    CONSTRAINT uk_player_sport_profiles_player_sport UNIQUE (player_id, sport_code),
    CONSTRAINT chk_player_sport_profiles_sport_code
        CHECK (sport_code IN ('BADMINTON')),
    CONSTRAINT chk_player_sport_profiles_skill_level
        CHECK (skill_level IN (
            'WEAK',
            'WEAK_PLUS',
            'INTERMEDIATE_MINUS',
            'INTERMEDIATE',
            'INTERMEDIATE_PLUS',
            'GOOD'
        ))
);

CREATE INDEX idx_player_sport_profiles_player_id
    ON player_sport_profiles (player_id);

CREATE TABLE venues (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    location_text VARCHAR(500),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_venues_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE courts (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    sport_code VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_courts_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
    CONSTRAINT uk_courts_venue_name UNIQUE (venue_id, name),
    CONSTRAINT chk_courts_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT chk_courts_sport_code CHECK (sport_code IN ('BADMINTON'))
);

CREATE INDEX idx_courts_venue_id ON courts (venue_id);

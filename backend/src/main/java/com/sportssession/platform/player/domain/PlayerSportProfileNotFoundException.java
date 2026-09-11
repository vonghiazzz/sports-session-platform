package com.sportssession.platform.player.domain;

import com.sportssession.platform.shared.domain.SportCode;

import java.util.UUID;

public class PlayerSportProfileNotFoundException extends RuntimeException {

    public PlayerSportProfileNotFoundException(
            UUID playerId,
            SportCode sportCode
    ) {
        super("Player sport profile not found: " + playerId + " / " + sportCode);
    }
}

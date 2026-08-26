package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.player.application.PlayerLookup;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PlayerLookupAdapter implements PlayerLookup {

    private final PlayerRepository playerRepository;

    public PlayerLookupAdapter(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    @Override
    public boolean exists(UUID playerId) {
        return playerRepository.existsById(playerId);
    }
}
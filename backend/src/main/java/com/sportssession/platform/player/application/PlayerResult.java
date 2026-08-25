package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;

import java.util.List;

public record PlayerResult(
        Player player,
        List<PlayerSportProfile> sportProfiles
) {
    public PlayerResult {
        sportProfiles = List.copyOf(sportProfiles);
    }
}


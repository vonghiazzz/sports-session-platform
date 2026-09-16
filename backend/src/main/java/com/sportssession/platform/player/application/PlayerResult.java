package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.Player;
import java.util.List;

public record PlayerResult(
        Player player,
        List<PlayerSportProfileResult> sportProfiles
) {
    public PlayerResult {
        sportProfiles = List.copyOf(sportProfiles);
    }
}

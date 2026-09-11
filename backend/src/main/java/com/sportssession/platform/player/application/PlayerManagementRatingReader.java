package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.shared.domain.MatchFormat;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface PlayerManagementRatingReader {

    Map<UUID, PlayerManagementRatingSnapshot> readEffectiveRatings(
            Collection<PlayerSportProfile> profiles,
            MatchFormat matchFormat
    );
}

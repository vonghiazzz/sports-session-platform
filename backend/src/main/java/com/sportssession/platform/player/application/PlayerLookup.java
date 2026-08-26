package com.sportssession.platform.player.application;

import java.util.UUID;

public interface PlayerLookup {

    boolean exists(UUID playerId);
}
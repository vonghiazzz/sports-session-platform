package com.sportssession.platform.session.application;

import java.util.UUID;

public interface SessionActiveMatchLookup {

    boolean hasPlayingMatch(UUID sessionId);
}

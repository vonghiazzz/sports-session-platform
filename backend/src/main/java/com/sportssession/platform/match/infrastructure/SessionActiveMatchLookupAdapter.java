package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.session.application.SessionActiveMatchLookup;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SessionActiveMatchLookupAdapter implements SessionActiveMatchLookup {

    private final MatchRepository matchRepository;

    public SessionActiveMatchLookupAdapter(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Override
    public boolean hasPlayingMatch(UUID sessionId) {
        return matchRepository.existsBySessionIdAndStatus(
                sessionId,
                MatchStatus.PLAYING
        );
    }
}

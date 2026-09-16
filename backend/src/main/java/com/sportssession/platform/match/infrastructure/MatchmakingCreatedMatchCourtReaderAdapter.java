package com.sportssession.platform.match.infrastructure;

import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.matchmaking.application.MatchmakingCreatedMatchCourtReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class MatchmakingCreatedMatchCourtReaderAdapter
        implements MatchmakingCreatedMatchCourtReader {

    private final MatchRepository matchRepository;

    public MatchmakingCreatedMatchCourtReaderAdapter(
            MatchRepository matchRepository
    ) {
        this.matchRepository = matchRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> createdMatchCourtIds(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        return Set.copyOf(
                matchRepository.findCourtIdsBySessionIdAndStatus(
                        sessionId,
                        MatchStatus.CREATED
                )
        );
    }
}

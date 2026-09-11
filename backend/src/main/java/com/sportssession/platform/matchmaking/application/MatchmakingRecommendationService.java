package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class MatchmakingRecommendationService {

    private final MatchmakingSessionSnapshotReader sessionSnapshotReader;
    private final MatchmakingCandidatePreparationService candidatePreparation;
    private final MatchmakingEngine matchmakingEngine;
    private final Clock clock;

    public MatchmakingRecommendationService(
            MatchmakingSessionSnapshotReader sessionSnapshotReader,
            MatchmakingCandidatePreparationService candidatePreparation,
            MatchmakingEngine matchmakingEngine,
            Clock clock
    ) {
        this.sessionSnapshotReader = sessionSnapshotReader;
        this.candidatePreparation = candidatePreparation;
        this.matchmakingEngine = matchmakingEngine;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MatchmakingResult recommend(
            UUID sessionId,
            UUID sessionCourtId
    ) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Objects.requireNonNull(sessionCourtId, "sessionCourtId is required");

        Instant evaluationTime = clock.instant();
        MatchmakingSessionSnapshot snapshot = sessionSnapshotReader.load(
                sessionId,
                sessionCourtId
        );
        PreparedMatchmakingCandidates prepared = candidatePreparation.prepare(
                snapshot.sessionEvidence(),
                evaluationTime
        );
        MatchmakingContext context = new MatchmakingContext(
                prepared.sessionId(),
                snapshot.sessionCourtId(),
                prepared.sportCode(),
                prepared.matchFormat(),
                prepared.evaluationTime(),
                prepared.candidates()
        );
        return matchmakingEngine.recommend(context);
    }
}

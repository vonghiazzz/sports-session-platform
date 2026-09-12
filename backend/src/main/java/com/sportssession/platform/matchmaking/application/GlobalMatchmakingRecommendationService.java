package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.MatchRecommendation;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchmaking.domain.RecommendedPlayer;
import com.sportssession.platform.matchplan.application.MatchPlanPlanningLookup;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class GlobalMatchmakingRecommendationService {

    public static final String ORCHESTRATION_VERSION =
            "global-greedy-available-unplanned-v1";

    private static final Comparator<GlobalMatchmakingCourtSnapshot> COURT_ORDER =
            Comparator.comparing(GlobalMatchmakingCourtSnapshot::addedAt)
                    .thenComparing(
                            GlobalMatchmakingCourtSnapshot::sessionCourtId
                    );

    private final GlobalMatchmakingSessionSnapshotReader snapshotReader;
    private final MatchmakingCandidatePreparationService candidatePreparation;
    private final MatchPlanPlanningLookup matchPlanPlanningLookup;
    private final MatchmakingCreatedMatchCourtReader createdMatchCourtReader;
    private final MatchmakingEngine matchmakingEngine;
    private final Clock clock;

    public GlobalMatchmakingRecommendationService(
            GlobalMatchmakingSessionSnapshotReader snapshotReader,
            MatchmakingCandidatePreparationService candidatePreparation,
            MatchPlanPlanningLookup matchPlanPlanningLookup,
            MatchmakingCreatedMatchCourtReader createdMatchCourtReader,
            MatchmakingEngine matchmakingEngine,
            Clock clock
    ) {
        this.snapshotReader = snapshotReader;
        this.candidatePreparation = candidatePreparation;
        this.matchPlanPlanningLookup = matchPlanPlanningLookup;
        this.createdMatchCourtReader = createdMatchCourtReader;
        this.matchmakingEngine = matchmakingEngine;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GlobalMatchmakingPreview preview(UUID sessionId) {
        Objects.requireNonNull(sessionId, "sessionId is required");
        Instant evaluationTime = clock.instant();
        GlobalMatchmakingSessionSnapshot snapshot = snapshotReader.load(
                sessionId
        );
        PreparedMatchmakingCandidates prepared = candidatePreparation.prepare(
                snapshot.sessionEvidence(),
                evaluationTime
        );

        Set<UUID> queuedCourtIds =
                matchPlanPlanningLookup.queuedCourtIds(sessionId);
        Set<UUID> createdMatchCourtIds =
                createdMatchCourtReader.createdMatchCourtIds(sessionId);
        List<GlobalMatchmakingCourtSnapshot> targetCourts = snapshot.courts()
                .stream()
                .filter(court -> court.status() == SessionCourtStatus.AVAILABLE)
                .filter(court -> !queuedCourtIds.contains(
                        court.sessionCourtId()
                ))
                .filter(court -> !createdMatchCourtIds.contains(
                        court.sessionCourtId()
                ))
                .sorted(COURT_ORDER)
                .toList();

        List<MatchmakingCandidate> remainingCandidates = new ArrayList<>(
                prepared.candidates()
        );
        List<MatchmakingResult> courtResults = new ArrayList<>();
        for (GlobalMatchmakingCourtSnapshot court : targetCourts) {
            MatchmakingResult result = matchmakingEngine.recommend(
                    new MatchmakingContext(
                            prepared.sessionId(),
                            court.sessionCourtId(),
                            prepared.sportCode(),
                            prepared.matchFormat(),
                            prepared.evaluationTime(),
                            remainingCandidates,
                            prepared.pairingHistory()
                    )
            );
            courtResults.add(result);
            if (result instanceof MatchRecommendation recommendation) {
                Set<UUID> selectedIds = selectedParticipantIds(
                        recommendation
                );
                remainingCandidates.removeIf(candidate ->
                        selectedIds.contains(candidate.sessionParticipantId())
                );
            }
        }

        boolean hasRecommendation = courtResults.stream()
                .anyMatch(MatchRecommendation.class::isInstance);
        GlobalMatchmakingOutcome outcome = hasRecommendation
                ? GlobalMatchmakingOutcome.RECOMMENDED
                : GlobalMatchmakingOutcome.UNAVAILABLE;
        GlobalMatchmakingUnavailableReason reason = outcome
                == GlobalMatchmakingOutcome.RECOMMENDED
                ? null
                : targetCourts.isEmpty()
                        ? GlobalMatchmakingUnavailableReason.NO_ELIGIBLE_COURTS
                        : GlobalMatchmakingUnavailableReason
                                .INSUFFICIENT_ELIGIBLE_PLAYERS;

        return new GlobalMatchmakingPreview(
                outcome,
                ORCHESTRATION_VERSION,
                MatchmakingEngine.ALGORITHM_VERSION,
                evaluationTime,
                sessionId,
                prepared.candidates().size(),
                courtResults,
                reason
        );
    }

    private Set<UUID> selectedParticipantIds(
            MatchRecommendation recommendation
    ) {
        List<RecommendedPlayer> players = List.of(
                recommendation.teamA().slot1(),
                recommendation.teamA().slot2(),
                recommendation.teamB().slot1(),
                recommendation.teamB().slot2()
        );
        Set<UUID> selectedIds = new HashSet<>();
        for (RecommendedPlayer player : players) {
            if (!selectedIds.add(player.sessionParticipantId())) {
                throw new IllegalStateException(
                        "MatchmakingEngine selected a duplicate "
                                + "SessionParticipant"
                );
            }
        }
        return Set.copyOf(selectedIds);
    }
}

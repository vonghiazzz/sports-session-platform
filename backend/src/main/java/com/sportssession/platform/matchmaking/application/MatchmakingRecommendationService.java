package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionCourtStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchmakingRecommendationService {

    private final MatchmakingSessionSnapshotReader sessionSnapshotReader;
    private final MatchmakingRatingReader ratingReader;
    private final MatchmakingEngine matchmakingEngine;
    private final Clock clock;

    public MatchmakingRecommendationService(
            MatchmakingSessionSnapshotReader sessionSnapshotReader,
            MatchmakingRatingReader ratingReader,
            MatchmakingEngine matchmakingEngine,
            Clock clock
    ) {
        this.sessionSnapshotReader = sessionSnapshotReader;
        this.ratingReader = ratingReader;
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
        validateOperationalContext(snapshot);

        List<MatchmakingSessionParticipantSnapshot> waitingParticipants =
                snapshot.participants().stream()
                        .filter(participant -> participant.participantStatus()
                                == ParticipantStatus.WAITING)
                        .toList();
        validateWaitingEvidence(waitingParticipants, evaluationTime);
        validateUniqueWaitingIdentities(waitingParticipants);

        List<UUID> waitingPlayerIds = waitingParticipants.stream()
                .map(MatchmakingSessionParticipantSnapshot::playerId)
                .toList();
        Map<UUID, MatchmakingRatingSnapshot> ratings =
                ratingReader.readEffectiveRatings(
                        waitingPlayerIds,
                        snapshot.sportCode(),
                        snapshot.matchFormat()
                );
        validateCompleteRatingBatch(waitingPlayerIds, ratings);

        List<MatchmakingCandidate> candidates = waitingParticipants.stream()
                .map(participant -> candidate(
                        participant,
                        ratings.get(participant.playerId())
                ))
                .toList();
        MatchmakingContext context = new MatchmakingContext(
                snapshot.sessionId(),
                snapshot.sessionCourtId(),
                snapshot.sportCode(),
                snapshot.matchFormat(),
                evaluationTime,
                candidates
        );
        return matchmakingEngine.recommend(context);
    }

    private void validateOperationalContext(
            MatchmakingSessionSnapshot snapshot
    ) {
        if (snapshot.sessionStatus() != SessionStatus.IN_PROGRESS) {
            throw new MatchmakingRecommendationException(
                    MatchmakingRecommendationFailureReason
                            .SESSION_NOT_IN_PROGRESS,
                    "Matchmaking requires an IN_PROGRESS Session: "
                            + snapshot.sessionId()
            );
        }
        if (snapshot.sessionCourtStatus() != SessionCourtStatus.AVAILABLE) {
            throw new MatchmakingRecommendationException(
                    MatchmakingRecommendationFailureReason
                            .SESSION_COURT_NOT_AVAILABLE,
                    "Matchmaking requires an AVAILABLE SessionCourt: "
                            + snapshot.sessionCourtId()
            );
        }
    }

    private void validateWaitingEvidence(
            List<MatchmakingSessionParticipantSnapshot> waitingParticipants,
            Instant evaluationTime
    ) {
        for (MatchmakingSessionParticipantSnapshot participant
                : waitingParticipants) {
            if (participant.waitingSince() == null) {
                throw new MatchmakingRecommendationException(
                        MatchmakingRecommendationFailureReason
                                .WAITING_PARTICIPANT_MISSING_WAITING_SINCE,
                        "WAITING SessionParticipant is missing waitingSince: "
                                + participant.sessionParticipantId()
                );
            }
            if (participant.waitingSince().isAfter(evaluationTime)) {
                throw new MatchmakingRecommendationException(
                        MatchmakingRecommendationFailureReason
                                .WAITING_PARTICIPANT_WAITING_SINCE_AFTER_EVALUATION_TIME,
                        "WAITING SessionParticipant has waitingSince after "
                                + "evaluationTime: "
                                + participant.sessionParticipantId()
                );
            }
        }
    }

    private void validateUniqueWaitingIdentities(
            List<MatchmakingSessionParticipantSnapshot> waitingParticipants
    ) {
        Set<UUID> participantIds = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        for (MatchmakingSessionParticipantSnapshot participant
                : waitingParticipants) {
            if (!participantIds.add(participant.sessionParticipantId())) {
                throw new InvalidMatchmakingInputException(
                        "sessionParticipantId must be unique"
                );
            }
            if (!playerIds.add(participant.playerId())) {
                throw new InvalidMatchmakingInputException(
                        "playerId must be unique"
                );
            }
        }
    }

    private void validateCompleteRatingBatch(
            List<UUID> waitingPlayerIds,
            Map<UUID, MatchmakingRatingSnapshot> ratings
    ) {
        Set<UUID> requestedPlayerIds = Set.copyOf(waitingPlayerIds);
        boolean complete = ratings != null
                && ratings.keySet().equals(requestedPlayerIds)
                && requestedPlayerIds.stream().allMatch(playerId -> {
                    MatchmakingRatingSnapshot rating = ratings.get(playerId);
                    return rating != null && rating.playerId().equals(playerId);
                });
        if (!complete) {
            throw new MatchmakingRecommendationException(
                    MatchmakingRecommendationFailureReason
                            .RATING_BATCH_INCOMPLETE,
                    "Effective Rating batch must exactly match WAITING Players"
            );
        }
    }

    private MatchmakingCandidate candidate(
            MatchmakingSessionParticipantSnapshot participant,
            MatchmakingRatingSnapshot rating
    ) {
        return new MatchmakingCandidate(
                participant.sessionParticipantId(),
                participant.playerId(),
                participant.waitingSince(),
                rating.ratingValue(),
                rating.uncertainty(),
                rating.ratedMatches(),
                rating.ratingBasis()
        );
    }
}

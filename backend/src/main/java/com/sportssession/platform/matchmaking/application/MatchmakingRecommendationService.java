package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchmaking.domain.MatchmakingContext;
import com.sportssession.platform.matchmaking.domain.MatchmakingEngine;
import com.sportssession.platform.matchmaking.domain.MatchmakingResult;
import com.sportssession.platform.matchplan.application.MatchPlanPlanningLookup;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.session.domain.ParticipantStatus;
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
    private final MatchmakingSkillLevelReader skillLevelReader;
    private final MatchmakingSessionMatchCountReader sessionMatchCountReader;
    private final MatchmakingEngine matchmakingEngine;
    private final MatchPlanPlanningLookup matchPlanPlanningLookup;
    private final Clock clock;

    public MatchmakingRecommendationService(
            MatchmakingSessionSnapshotReader sessionSnapshotReader,
            MatchmakingRatingReader ratingReader,
            MatchmakingSkillLevelReader skillLevelReader,
            MatchmakingSessionMatchCountReader sessionMatchCountReader,
            MatchmakingEngine matchmakingEngine,
            MatchPlanPlanningLookup matchPlanPlanningLookup,
            Clock clock
    ) {
        this.sessionSnapshotReader = sessionSnapshotReader;
        this.ratingReader = ratingReader;
        this.skillLevelReader = skillLevelReader;
        this.sessionMatchCountReader = sessionMatchCountReader;
        this.matchmakingEngine = matchmakingEngine;
        this.matchPlanPlanningLookup = matchPlanPlanningLookup;
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

        // 1. Lấy toàn bộ người đang WAITING.
        List<MatchmakingSessionParticipantSnapshot> waitingParticipants =
                snapshot.participants().stream()
                        .filter(participant ->
                                participant.participantStatus()
                                        == ParticipantStatus.WAITING
                        )
                        .toList();

        // 2. Validate dữ liệu WAITING trước.
        // Không để record WAITING lỗi bị che chỉ vì nó đang nằm trong Queue.
        validateWaitingEvidence(
                waitingParticipants,
                evaluationTime
        );
        validateUniqueWaitingIdentities(waitingParticipants);

        // 3. Những người đã nằm trong một MatchPlan QUEUED
        // không được đưa vào recommendation tiếp theo.
        Set<UUID> queuedParticipantIds =
                matchPlanPlanningLookup.queuedParticipantIds(sessionId);

        List<MatchmakingSessionParticipantSnapshot>
                eligibleWaitingParticipants =
                waitingParticipants.stream()
                        .filter(participant ->
                                !queuedParticipantIds.contains(
                                        participant.sessionParticipantId()
                                )
                        )
                        .toList();

        // 4. Rating chỉ đọc cho những người thực sự eligible.
        List<UUID> eligiblePlayerIds =
                eligibleWaitingParticipants.stream()
                        .map(MatchmakingSessionParticipantSnapshot::playerId)
                        .toList();
        List<UUID> eligibleParticipantIds =
                eligibleWaitingParticipants.stream()
                        .map(MatchmakingSessionParticipantSnapshot::
                                sessionParticipantId)
                        .toList();
        Map<UUID, Integer> sessionMatchCounts =
                sessionMatchCountReader.readCompletedMatchCounts(
                        sessionId,
                        eligibleParticipantIds
                );
        validateCompleteSessionMatchCountBatch(
                eligibleParticipantIds,
                sessionMatchCounts
        );
        Map<UUID, SkillLevel> skillLevels =
                skillLevelReader.readSkillLevels(
                        eligiblePlayerIds,
                        snapshot.sportCode()
                );

        validateCompleteSkillLevelBatch(
                eligiblePlayerIds,
                skillLevels
        );

        Map<UUID, MatchmakingRatingSnapshot> ratings =
                ratingReader.readEffectiveRatings(
                        eligiblePlayerIds,
                        snapshot.sportCode(),
                        snapshot.matchFormat()
                );

        validateCompleteRatingBatch(
                eligiblePlayerIds,
                ratings
        );

        // 5. Engine cũng chỉ nhận eligible players.
        List<MatchmakingCandidate> candidates =
                eligibleWaitingParticipants.stream()
                        .map(participant -> candidate(
                                participant,
                                sessionMatchCounts.get(
                                        participant.sessionParticipantId()
                                ),
                                skillLevels.get(participant.playerId()),
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

    private void validateCompleteSkillLevelBatch(
            List<UUID> playerIds,
            Map<UUID, SkillLevel> skillLevels
    ) {
        Set<UUID> requestedPlayerIds = Set.copyOf(playerIds);

        boolean complete = skillLevels != null
                && skillLevels.keySet().equals(requestedPlayerIds)
                && requestedPlayerIds.stream()
                .allMatch(playerId ->
                        skillLevels.get(playerId) != null
                );

        if (!complete) {
            throw new InvalidMatchmakingInputException(
                    "Skill Level batch must exactly match eligible Players"
            );
        }
    }

    private void validateCompleteSessionMatchCountBatch(
            List<UUID> participantIds,
            Map<UUID, Integer> counts
    ) {
        Set<UUID> requestedIds = Set.copyOf(participantIds);
        boolean complete = counts != null
                && counts.keySet().equals(requestedIds)
                && requestedIds.stream().allMatch(participantId -> {
                    Integer count = counts.get(participantId);
                    return count != null && count >= 0;
                });
        if (!complete) {
            throw new InvalidMatchmakingInputException(
                    "Session Match count batch must exactly match eligible "
                            + "SessionParticipants"
            );
        }
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
            int sessionMatchesPlayed,
            SkillLevel skillLevel,
            MatchmakingRatingSnapshot rating
    ) {
        return new MatchmakingCandidate(
                participant.sessionParticipantId(),
                participant.playerId(),
                participant.waitingSince(),
                skillLevel,
                sessionMatchesPlayed,
                rating.ratingValue(),
                rating.uncertainty(),
                rating.ratedMatches(),
                rating.ratingBasis()
        );
    }
}

package com.sportssession.platform.matchmaking.application;

import com.sportssession.platform.matchmaking.domain.InvalidMatchmakingInputException;
import com.sportssession.platform.matchmaking.domain.MatchmakingCandidate;
import com.sportssession.platform.matchplan.application.MatchPlanPlanningLookup;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.session.domain.ParticipantStatus;
import com.sportssession.platform.session.domain.SessionStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class MatchmakingCandidatePreparationService {

    private final MatchmakingRatingReader ratingReader;
    private final MatchmakingSkillLevelReader skillLevelReader;
    private final MatchmakingSessionMatchCountReader sessionMatchCountReader;
    private final MatchPlanPlanningLookup matchPlanPlanningLookup;

    public MatchmakingCandidatePreparationService(
            MatchmakingRatingReader ratingReader,
            MatchmakingSkillLevelReader skillLevelReader,
            MatchmakingSessionMatchCountReader sessionMatchCountReader,
            MatchPlanPlanningLookup matchPlanPlanningLookup
    ) {
        this.ratingReader = ratingReader;
        this.skillLevelReader = skillLevelReader;
        this.sessionMatchCountReader = sessionMatchCountReader;
        this.matchPlanPlanningLookup = matchPlanPlanningLookup;
    }

    public PreparedMatchmakingCandidates prepare(
            MatchmakingSessionEvidence evidence,
            Instant evaluationTime
    ) {
        Objects.requireNonNull(evidence, "evidence is required");
        Objects.requireNonNull(evaluationTime, "evaluationTime is required");
        validateOperationalContext(evidence);

        List<MatchmakingSessionParticipantSnapshot> waitingParticipants =
                evidence.participants().stream()
                        .filter(participant ->
                                participant.participantStatus()
                                        == ParticipantStatus.WAITING
                        )
                        .toList();
        validateWaitingEvidence(waitingParticipants, evaluationTime);
        validateUniqueWaitingIdentities(waitingParticipants);

        Set<UUID> queuedParticipantIds =
                matchPlanPlanningLookup.queuedParticipantIds(
                        evidence.sessionId()
                );
        List<MatchmakingSessionParticipantSnapshot> eligibleParticipants =
                waitingParticipants.stream()
                        .filter(participant ->
                                !queuedParticipantIds.contains(
                                        participant.sessionParticipantId()
                                )
                        )
                        .toList();

        List<UUID> playerIds = eligibleParticipants.stream()
                .map(MatchmakingSessionParticipantSnapshot::playerId)
                .toList();
        List<UUID> participantIds = eligibleParticipants.stream()
                .map(MatchmakingSessionParticipantSnapshot::
                        sessionParticipantId)
                .toList();

        Map<UUID, Integer> matchCounts =
                sessionMatchCountReader.readCompletedMatchCounts(
                        evidence.sessionId(),
                        participantIds
                );
        validateCompleteSessionMatchCountBatch(participantIds, matchCounts);

        Map<UUID, SkillLevel> skillLevels =
                skillLevelReader.readSkillLevels(
                        playerIds,
                        evidence.sportCode()
                );
        validateCompleteSkillLevelBatch(playerIds, skillLevels);

        Map<UUID, MatchmakingRatingSnapshot> ratings =
                ratingReader.readEffectiveRatings(
                        playerIds,
                        evidence.sportCode(),
                        evidence.matchFormat()
                );
        validateCompleteRatingBatch(playerIds, ratings);

        List<MatchmakingCandidate> candidates = eligibleParticipants.stream()
                .map(participant -> candidate(
                        participant,
                        matchCounts.get(participant.sessionParticipantId()),
                        skillLevels.get(participant.playerId()),
                        ratings.get(participant.playerId())
                ))
                .toList();

        return new PreparedMatchmakingCandidates(
                evidence.sessionId(),
                evidence.sportCode(),
                evidence.matchFormat(),
                evaluationTime,
                candidates
        );
    }

    private void validateOperationalContext(
            MatchmakingSessionEvidence evidence
    ) {
        if (evidence.sessionStatus() != SessionStatus.IN_PROGRESS) {
            throw new MatchmakingRecommendationException(
                    MatchmakingRecommendationFailureReason
                            .SESSION_NOT_IN_PROGRESS,
                    "Matchmaking requires an IN_PROGRESS Session: "
                            + evidence.sessionId()
            );
        }
    }

    private void validateWaitingEvidence(
            List<MatchmakingSessionParticipantSnapshot> participants,
            Instant evaluationTime
    ) {
        for (MatchmakingSessionParticipantSnapshot participant : participants) {
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
            List<MatchmakingSessionParticipantSnapshot> participants
    ) {
        Set<UUID> participantIds = new HashSet<>();
        Set<UUID> playerIds = new HashSet<>();
        for (MatchmakingSessionParticipantSnapshot participant : participants) {
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

    private void validateCompleteSkillLevelBatch(
            List<UUID> playerIds,
            Map<UUID, SkillLevel> skillLevels
    ) {
        Set<UUID> requestedIds = Set.copyOf(playerIds);
        boolean complete = skillLevels != null
                && skillLevels.keySet().equals(requestedIds)
                && requestedIds.stream().allMatch(playerId ->
                        skillLevels.get(playerId) != null
                );
        if (!complete) {
            throw new InvalidMatchmakingInputException(
                    "Skill Level batch must exactly match eligible Players"
            );
        }
    }

    private void validateCompleteRatingBatch(
            List<UUID> playerIds,
            Map<UUID, MatchmakingRatingSnapshot> ratings
    ) {
        Set<UUID> requestedIds = Set.copyOf(playerIds);
        boolean complete = ratings != null
                && ratings.keySet().equals(requestedIds)
                && requestedIds.stream().allMatch(playerId -> {
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

package com.sportssession.platform.rating.application;

import com.sportssession.platform.match.domain.TeamSide;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.rating.domain.RatingEngine;
import com.sportssession.platform.rating.domain.RatingInitializer;
import com.sportssession.platform.rating.domain.RatingOutcome;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.domain.RatingUpdate;
import com.sportssession.platform.rating.domain.WinningTeam;
import com.sportssession.platform.rating.infrastructure.PlayerRatingEntity;
import com.sportssession.platform.rating.infrastructure.PlayerRatingRepository;
import com.sportssession.platform.rating.infrastructure.RatingEventEntity;
import com.sportssession.platform.rating.infrastructure.RatingEventRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RatingProcessingService {

    private static final int SUPPORTED_RESULT_VERSION = 1;

    private final CompletedMatchRatingEvidenceReader evidenceReader;
    private final PlayerSkillLevelLookup skillLevelLookup;
    private final PlayerRatingRepository playerRatingRepository;
    private final RatingEventRepository ratingEventRepository;
    private final RatingEngine ratingEngine;
    private final Clock clock;

    public RatingProcessingService(
            CompletedMatchRatingEvidenceReader evidenceReader,
            PlayerSkillLevelLookup skillLevelLookup,
            PlayerRatingRepository playerRatingRepository,
            RatingEventRepository ratingEventRepository,
            RatingEngine ratingEngine,
            Clock clock
    ) {
        this.evidenceReader = evidenceReader;
        this.skillLevelLookup = skillLevelLookup;
        this.playerRatingRepository = playerRatingRepository;
        this.ratingEventRepository = ratingEventRepository;
        this.ratingEngine = ratingEngine;
        this.clock = clock;
    }

    @Transactional
    public RatingProcessingResult processRating(UUID matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("matchId is required");
        }

        CompletedMatchRatingContext context = evidenceReader
                .findCompletedMatch(matchId)
                .orElseThrow(() -> new RatingEvidenceUnavailableException(matchId));
        validateSupportedContext(context);

        List<RatingParticipantEvidence> teamA = orderedTeam(context.teamA());
        List<RatingParticipantEvidence> teamB = orderedTeam(context.teamB());
        List<UUID> playerIds = participantPlayerIds(teamA, teamB);

        if (alreadyApplied(context, playerIds, null)) {
            return RatingProcessingResult.ALREADY_APPLIED;
        }

        List<PlayerRatingEntity> existingRatings = lockContextRatings(
                playerIds,
                context
        );
        validateAlgorithmVersions(existingRatings);

        Set<UUID> existingPlayerIds = new HashSet<>();
        existingRatings.forEach(rating -> existingPlayerIds.add(
                rating.getPlayerId()
        ));
        List<UUID> missingPlayerIds = playerIds.stream()
                .filter(playerId -> !existingPlayerIds.contains(playerId))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();

        Instant processingTime = clock.instant();
        initializeMissingRatings(
                missingPlayerIds,
                context,
                processingTime
        );

        List<PlayerRatingEntity> lockedRatings = lockContextRatings(
                playerIds,
                context
        );
        if (lockedRatings.size() != 4) {
            throw new RatingProcessingIntegrityException(
                    "Rating context must resolve exactly four PlayerRatings"
            );
        }
        validateAlgorithmVersions(lockedRatings);

        if (alreadyApplied(context, playerIds, lockedRatings)) {
            return RatingProcessingResult.ALREADY_APPLIED;
        }

        List<UUID> lockedRatingIds = lockedRatings.stream()
                .map(PlayerRatingEntity::getId)
                .toList();
        if (ratingEventRepository.existsAppliedMatchAfter(
                lockedRatingIds,
                context.completedAt(),
                context.matchId()
        )) {
            throw new RatingHistoryOrderingException(context.matchId());
        }

        Map<UUID, PlayerRatingEntity> ratingByPlayerId = indexByPlayerId(
                lockedRatings,
                playerIds
        );
        List<RatingState> teamAStates = statesFor(teamA, ratingByPlayerId);
        List<RatingState> teamBStates = statesFor(teamB, ratingByPlayerId);
        List<RatingUpdate> updates = ratingEngine.rate(
                teamAStates,
                teamBStates,
                winner(context.winnerTeam())
        );
        if (updates.size() != 4) {
            throw new RatingProcessingIntegrityException(
                    "RatingEngine must return exactly four updates"
            );
        }

        List<RatingParticipantEvidence> participants = new ArrayList<>(4);
        participants.addAll(teamA);
        participants.addAll(teamB);
        List<RatingEventEntity> events = new ArrayList<>(4);
        for (int index = 0; index < participants.size(); index++) {
            RatingParticipantEvidence participant = participants.get(index);
            PlayerRatingEntity rating = ratingByPlayerId.get(
                    participant.playerId()
            );
            RatingUpdate update = updates.get(index);
            RatingState persistedBefore = rating.toRatingState();
            if (!persistedBefore.equals(update.before())) {
                throw new RatingProcessingIntegrityException(
                        "RatingEngine before-state does not match persisted state"
                );
            }
            rating.applyRating(update.after(), processingTime);
            events.add(RatingEventEntity.create(
                    rating.getId(),
                    context.matchId(),
                    context.resultVersion(),
                    outcome(participant.teamSide(), context.winnerTeam()),
                    persistedBefore,
                    update.after(),
                    ratingEngine.algorithmVersion(),
                    processingTime
            ));
        }

        ratingEventRepository.saveAllAndFlush(events);
        return RatingProcessingResult.APPLIED;
    }

    private void validateSupportedContext(CompletedMatchRatingContext context) {
        if (context.resultVersion() != SUPPORTED_RESULT_VERSION) {
            throw new UnsupportedRatingResultVersionException(
                    context.resultVersion()
            );
        }
        if (context.sportCode() != SportCode.BADMINTON) {
            throw new UnsupportedRatingContextException(
                    "Rating V1 supports only BADMINTON"
            );
        }
        if (context.matchFormat() != MatchFormat.DOUBLES) {
            throw new UnsupportedRatingContextException(
                    "Rating V1 supports only DOUBLES"
            );
        }
    }

    private boolean alreadyApplied(
            CompletedMatchRatingContext context,
            List<UUID> expectedPlayerIds,
            List<PlayerRatingEntity> knownRatings
    ) {
        List<RatingEventEntity> events = ratingEventRepository
                .findAllByMatchIdAndResultVersionOrderByPlayerRatingId(
                        context.matchId(),
                        context.resultVersion()
                );
        if (events.isEmpty()) {
            return false;
        }
        if (events.size() != 4) {
            throw new RatingProcessingIntegrityException(
                    "Expected either zero or four RatingEvents for Match result"
            );
        }
        if (events.stream().anyMatch(event -> !ratingEngine.algorithmVersion()
                .equals(event.getAlgorithmVersion()))) {
            throw new RatingProcessingIntegrityException(
                    "RatingEvent algorithmVersion is inconsistent"
            );
        }

        Set<UUID> eventRatingIds = new HashSet<>();
        events.forEach(event -> eventRatingIds.add(event.getPlayerRatingId()));
        if (eventRatingIds.size() != 4) {
            throw new RatingProcessingIntegrityException(
                    "RatingEvents do not reference four distinct PlayerRatings"
            );
        }

        List<PlayerRatingEntity> ratings = knownRatings == null
                ? playerRatingRepository.findAllById(eventRatingIds)
                : knownRatings;
        if (ratings.size() != 4) {
            throw new RatingProcessingIntegrityException(
                    "RatingEvents do not resolve four PlayerRatings"
            );
        }
        validateAlgorithmVersions(ratings);

        Set<RatingIdentity> expectedIdentities = new HashSet<>();
        expectedPlayerIds.forEach(playerId -> expectedIdentities.add(
                new RatingIdentity(
                        playerId,
                        context.sportCode(),
                        context.matchFormat()
                )
        ));
        Set<RatingIdentity> actualIdentities = new HashSet<>();
        ratings.stream()
                .filter(rating -> eventRatingIds.contains(rating.getId()))
                .forEach(rating -> actualIdentities.add(identity(rating)));

        if (!eventRatingIds.equals(ratings.stream()
                        .map(PlayerRatingEntity::getId)
                        .collect(java.util.stream.Collectors.toSet()))
                || !actualIdentities.equals(expectedIdentities)) {
            throw new RatingProcessingIntegrityException(
                    "RatingEvents do not match the completed Match identities"
            );
        }
        return true;
    }

    private List<PlayerRatingEntity> lockContextRatings(
            Collection<UUID> playerIds,
            CompletedMatchRatingContext context
    ) {
        return playerRatingRepository.findContextRatingsForUpdate(
                playerIds,
                context.sportCode(),
                context.matchFormat()
        );
    }

    private void initializeMissingRatings(
            List<UUID> missingPlayerIds,
            CompletedMatchRatingContext context,
            Instant processingTime
    ) {
        if (missingPlayerIds.isEmpty()) {
            return;
        }
        Map<UUID, SkillLevel> skillLevels = skillLevelLookup.requireSkillLevels(
                missingPlayerIds,
                context.sportCode()
        );
        List<PlayerRatingEntity> initialized = missingPlayerIds.stream()
                .map(playerId -> PlayerRatingEntity.initialize(
                        playerId,
                        context.sportCode(),
                        context.matchFormat(),
                        skillLevels.get(playerId),
                        RatingInitializer.initialize(skillLevels.get(playerId)),
                        ratingEngine.algorithmVersion(),
                        processingTime
                ))
                .toList();
        playerRatingRepository.saveAllAndFlush(initialized);
    }

    private void validateAlgorithmVersions(List<PlayerRatingEntity> ratings) {
        if (ratings.stream().anyMatch(rating -> !ratingEngine.algorithmVersion()
                .equals(rating.getAlgorithmVersion()))) {
            throw new RatingProcessingIntegrityException(
                    "PlayerRating algorithmVersion is inconsistent"
            );
        }
    }

    private Map<UUID, PlayerRatingEntity> indexByPlayerId(
            List<PlayerRatingEntity> ratings,
            List<UUID> expectedPlayerIds
    ) {
        Map<UUID, PlayerRatingEntity> result = new HashMap<>();
        ratings.forEach(rating -> result.put(rating.getPlayerId(), rating));
        if (result.size() != 4
                || !result.keySet().equals(Set.copyOf(expectedPlayerIds))) {
            throw new RatingProcessingIntegrityException(
                    "Locked PlayerRatings do not match completed Match Players"
            );
        }
        return result;
    }

    private List<RatingState> statesFor(
            List<RatingParticipantEvidence> participants,
            Map<UUID, PlayerRatingEntity> ratingByPlayerId
    ) {
        return participants.stream()
                .map(participant -> ratingByPlayerId
                        .get(participant.playerId())
                        .toRatingState())
                .toList();
    }

    private List<RatingParticipantEvidence> orderedTeam(
            List<RatingParticipantEvidence> team
    ) {
        return team.stream()
                .sorted(Comparator.comparingInt(
                        RatingParticipantEvidence::teamSlot
                ))
                .toList();
    }

    private List<UUID> participantPlayerIds(
            List<RatingParticipantEvidence> teamA,
            List<RatingParticipantEvidence> teamB
    ) {
        List<UUID> result = new ArrayList<>(4);
        teamA.forEach(participant -> result.add(participant.playerId()));
        teamB.forEach(participant -> result.add(participant.playerId()));
        return List.copyOf(result);
    }

    private WinningTeam winner(TeamSide winnerTeam) {
        return switch (winnerTeam) {
            case A -> WinningTeam.A;
            case B -> WinningTeam.B;
        };
    }

    private RatingOutcome outcome(TeamSide side, TeamSide winnerTeam) {
        return side == winnerTeam ? RatingOutcome.WIN : RatingOutcome.LOSS;
    }

    private RatingIdentity identity(PlayerRatingEntity rating) {
        return new RatingIdentity(
                rating.getPlayerId(),
                rating.getSportCode(),
                rating.getMatchFormat()
        );
    }

    private record RatingIdentity(
            UUID playerId,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
    }
}

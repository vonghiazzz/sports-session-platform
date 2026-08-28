package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.matchmaking.application.MatchmakingRatingReader;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionException;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingResolutionFailureReason;
import com.sportssession.platform.matchmaking.application.MatchmakingRatingSnapshot;
import com.sportssession.platform.matchmaking.domain.RatingBasis;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.rating.application.MissingPlayerRatingPriorException;
import com.sportssession.platform.rating.application.PlayerSkillLevelLookup;
import com.sportssession.platform.rating.domain.RatingInitializer;
import com.sportssession.platform.rating.domain.RatingNumericNormalizer;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.rating.domain.WengLinPlackettLuceRatingEngine;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class MatchmakingRatingReaderAdapter implements MatchmakingRatingReader {

    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerSkillLevelLookup playerSkillLevelLookup;

    public MatchmakingRatingReaderAdapter(
            PlayerRatingRepository playerRatingRepository,
            PlayerSkillLevelLookup playerSkillLevelLookup
    ) {
        this.playerRatingRepository = playerRatingRepository;
        this.playerSkillLevelLookup = playerSkillLevelLookup;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MatchmakingRatingSnapshot> readEffectiveRatings(
            Collection<UUID> playerIds,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        Set<UUID> requestedPlayerIds = validateAndCopyPlayerIds(playerIds);
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");
        if (requestedPlayerIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, MatchmakingRatingSnapshot> resolved = new LinkedHashMap<>();
        playerRatingRepository.findContextRatings(
                requestedPlayerIds,
                sportCode,
                matchFormat
        ).forEach(rating -> addPersistedRating(
                resolved,
                requestedPlayerIds,
                rating,
                sportCode,
                matchFormat
        ));

        Set<UUID> missingPlayerIds = new LinkedHashSet<>(requestedPlayerIds);
        missingPlayerIds.removeAll(resolved.keySet());
        if (!missingPlayerIds.isEmpty()) {
            addInitialPriors(
                    resolved,
                    missingPlayerIds,
                    sportCode,
                    matchFormat
            );
        }
        if (resolved.size() != requestedPlayerIds.size()
                || !resolved.keySet().equals(requestedPlayerIds)) {
            throw new IllegalStateException(
                    "Effective Rating batch is incomplete"
            );
        }
        return Map.copyOf(resolved);
    }

    private void addPersistedRating(
            Map<UUID, MatchmakingRatingSnapshot> resolved,
            Set<UUID> requestedPlayerIds,
            PlayerRatingEntity rating,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        UUID playerId = rating.getPlayerId();
        if (!requestedPlayerIds.contains(playerId)) {
            throw new IllegalStateException(
                    "Rating query returned an unrequested Player"
            );
        }
        if (!WengLinPlackettLuceRatingEngine.ALGORITHM_VERSION.equals(
                rating.getAlgorithmVersion()
        )) {
            throw new MatchmakingRatingResolutionException(
                    MatchmakingRatingResolutionFailureReason
                            .UNSUPPORTED_PERSISTED_ALGORITHM_VERSION,
                    Set.of(playerId),
                    sportCode,
                    matchFormat,
                    "Persisted Rating for Player " + playerId
                            + " uses unsupported algorithm version "
                            + rating.getAlgorithmVersion()
            );
        }
        MatchmakingRatingSnapshot previous = resolved.put(
                playerId,
                new MatchmakingRatingSnapshot(
                        playerId,
                        rating.getRatingValue(),
                        rating.getUncertainty(),
                        rating.getRatedMatches(),
                        RatingBasis.PERSISTED
                )
        );
        if (previous != null) {
            throw new IllegalStateException(
                    "Rating query returned duplicate Player " + playerId
            );
        }
    }

    private void addInitialPriors(
            Map<UUID, MatchmakingRatingSnapshot> resolved,
            Set<UUID> missingPlayerIds,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        Map<UUID, SkillLevel> skillLevels;
        try {
            skillLevels = playerSkillLevelLookup.requireSkillLevels(
                    missingPlayerIds,
                    sportCode
            );
        } catch (MissingPlayerRatingPriorException exception) {
            throw new MatchmakingRatingResolutionException(
                    MatchmakingRatingResolutionFailureReason
                            .MISSING_INITIAL_PRIOR,
                    exception.missingPlayerIds(),
                    sportCode,
                    matchFormat,
                    "Effective Rating prior is unavailable for Players "
                            + exception.missingPlayerIds()
            );
        }

        for (UUID playerId : missingPlayerIds) {
            SkillLevel skillLevel = skillLevels.get(playerId);
            if (skillLevel == null) {
                throw new MatchmakingRatingResolutionException(
                        MatchmakingRatingResolutionFailureReason
                                .MISSING_INITIAL_PRIOR,
                        Set.of(playerId),
                        sportCode,
                        matchFormat,
                        "Effective Rating prior is unavailable for Player "
                                + playerId
                );
            }
            RatingState initialState = RatingInitializer.initialize(skillLevel);
            resolved.put(
                    playerId,
                    new MatchmakingRatingSnapshot(
                            playerId,
                            RatingNumericNormalizer.normalizeToDecimal(
                                    initialState.mu()
                            ),
                            RatingNumericNormalizer.normalizeToDecimal(
                                    initialState.sigma()
                            ),
                            0,
                            RatingBasis.INITIAL_PRIOR
                    )
            );
        }
    }

    private Set<UUID> validateAndCopyPlayerIds(Collection<UUID> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds are required");
        Set<UUID> requestedPlayerIds = new LinkedHashSet<>();
        for (UUID playerId : playerIds) {
            Objects.requireNonNull(playerId, "playerId is required");
            if (!requestedPlayerIds.add(playerId)) {
                throw new IllegalArgumentException(
                        "playerIds must not contain duplicates: " + playerId
                );
            }
        }
        return Set.copyOf(requestedPlayerIds);
    }
}

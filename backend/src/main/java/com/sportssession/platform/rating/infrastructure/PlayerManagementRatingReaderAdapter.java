package com.sportssession.platform.rating.infrastructure;

import com.sportssession.platform.player.application.PlayerManagementRatingBasis;
import com.sportssession.platform.player.application.PlayerManagementRatingReader;
import com.sportssession.platform.player.application.PlayerManagementRatingSnapshot;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.rating.domain.RatingInitializer;
import com.sportssession.platform.rating.domain.RatingNumericNormalizer;
import com.sportssession.platform.rating.domain.RatingState;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlayerManagementRatingReaderAdapter
        implements PlayerManagementRatingReader {

    private final PlayerRatingRepository playerRatingRepository;

    public PlayerManagementRatingReaderAdapter(
            PlayerRatingRepository playerRatingRepository
    ) {
        this.playerRatingRepository = playerRatingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PlayerManagementRatingSnapshot> readEffectiveRatings(
            Collection<PlayerSportProfile> profiles,
            MatchFormat matchFormat
    ) {
        Objects.requireNonNull(profiles, "profiles are required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");

        List<PlayerSportProfile> requestedProfiles = new ArrayList<>();
        Set<UUID> profileIds = new LinkedHashSet<>();
        for (PlayerSportProfile profile : profiles) {
            Objects.requireNonNull(profile, "profile is required");
            if (!profileIds.add(profile.id())) {
                throw new IllegalArgumentException(
                        "profiles must not contain duplicate IDs: " + profile.id()
                );
            }
            requestedProfiles.add(profile);
        }
        if (requestedProfiles.isEmpty()) {
            return Map.of();
        }

        Map<UUID, PlayerManagementRatingSnapshot> result = new LinkedHashMap<>();
        Map<SportCode, List<PlayerSportProfile>> profilesBySport =
                requestedProfiles.stream().collect(Collectors.groupingBy(
                        PlayerSportProfile::sportCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        profilesBySport.forEach((sportCode, sportProfiles) -> addSportRatings(
                result,
                sportProfiles,
                sportCode,
                matchFormat
        ));

        if (!result.keySet().equals(profileIds)) {
            throw new IllegalStateException(
                    "Player management Rating batch is incomplete"
            );
        }
        return Map.copyOf(result);
    }

    private void addSportRatings(
            Map<UUID, PlayerManagementRatingSnapshot> result,
            List<PlayerSportProfile> profiles,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        Set<UUID> requestedPlayerIds = profiles.stream()
                .map(PlayerSportProfile::playerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedPlayerIds.size() != profiles.size()) {
            throw new IllegalArgumentException(
                    "profiles must contain one profile per Player and sport"
            );
        }

        Map<UUID, PlayerRatingEntity> persistedByPlayerId = new LinkedHashMap<>();
        playerRatingRepository.findContextRatings(
                requestedPlayerIds,
                sportCode,
                matchFormat
        ).forEach(rating -> {
            UUID playerId = rating.getPlayerId();
            if (!requestedPlayerIds.contains(playerId)
                    || rating.getSportCode() != sportCode
                    || rating.getMatchFormat() != matchFormat
                    || persistedByPlayerId.put(playerId, rating) != null) {
                throw new IllegalStateException(
                        "Rating query returned inconsistent Player context"
                );
            }
        });

        for (PlayerSportProfile profile : profiles) {
            PlayerRatingEntity persisted = persistedByPlayerId.get(
                    profile.playerId()
            );
            PlayerManagementRatingSnapshot snapshot = persisted == null
                    ? initialPrior(profile, matchFormat)
                    : persistedRating(persisted);
            if (result.put(profile.id(), snapshot) != null) {
                throw new IllegalStateException(
                        "Duplicate profile Rating result: " + profile.id()
                );
            }
        }
    }

    private PlayerManagementRatingSnapshot persistedRating(
            PlayerRatingEntity rating
    ) {
        return new PlayerManagementRatingSnapshot(
                rating.getPlayerId(),
                rating.getSportCode(),
                rating.getMatchFormat(),
                rating.getRatingValue(),
                rating.getUncertainty(),
                rating.getRatedMatches(),
                PlayerManagementRatingBasis.PERSISTED,
                rating.getAlgorithmVersion()
        );
    }

    private PlayerManagementRatingSnapshot initialPrior(
            PlayerSportProfile profile,
            MatchFormat matchFormat
    ) {
        RatingState state = RatingInitializer.initialize(profile.skillLevel());
        return new PlayerManagementRatingSnapshot(
                profile.playerId(),
                profile.sportCode(),
                matchFormat,
                RatingNumericNormalizer.normalizeToDecimal(state.mu()),
                RatingNumericNormalizer.normalizeToDecimal(state.sigma()),
                0,
                PlayerManagementRatingBasis.INITIAL_PRIOR,
                null
        );
    }
}

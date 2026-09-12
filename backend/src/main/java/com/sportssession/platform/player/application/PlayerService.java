package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.DuplicatePlayerSportProfileException;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerNotFoundException;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.PlayerSportProfileNotFoundException;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.shared.domain.MatchFormat;
import com.sportssession.platform.shared.domain.SportCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private static final String PLAYER_SPORT_UNIQUE_CONSTRAINT =
            "uk_player_sport_profiles_player_sport";

    private final PlayerRepository playerRepository;
    private final PlayerSportProfileRepository profileRepository;
    private final PlayerManagementRatingReader ratingReader;
    private final PlayerManagementRatingHistoryReader ratingHistoryReader;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerSportProfileRepository profileRepository,
            PlayerManagementRatingReader ratingReader,
            PlayerManagementRatingHistoryReader ratingHistoryReader
    ) {
        this.playerRepository = playerRepository;
        this.profileRepository = profileRepository;
        this.ratingReader = ratingReader;
        this.ratingHistoryReader = ratingHistoryReader;
    }

    @Transactional
    public PlayerResult createPlayer(CreatePlayerCommand command) {
        Instant now = Instant.now();
        Player player = Player.create(command.displayName(), now);
        PlayerSportProfile profile = PlayerSportProfile.create(
                player.id(), command.sport(), command.skillLevel(), now);

        playerRepository.save(PlayerEntity.from(player));
        try {
            profileRepository.saveAndFlush(PlayerSportProfileEntity.from(profile));
        } catch (DataIntegrityViolationException exception) {
            if (violatesPlayerSportUniqueConstraint(exception)) {
                throw new DuplicatePlayerSportProfileException(exception);
            }
            throw exception;
        }

        return result(player, List.of(profile));
    }

    @Transactional(readOnly = true)
    public PlayerResult getPlayer(UUID playerId) {
        Player player = playerRepository.findById(playerId)
                .map(PlayerEntity::toDomain)
                .orElseThrow(() -> new PlayerNotFoundException(playerId));
        List<PlayerSportProfile> profiles = profileRepository
                .findAllByPlayerIdOrderByCreatedAtAsc(playerId)
                .stream()
                .map(PlayerSportProfileEntity::toDomain)
                .toList();
        return result(player, profiles);
    }

    @Transactional(readOnly = true)
    public List<PlayerResult> searchPlayers(String name) {
        String normalizedName = name == null ? null : name.strip();
        List<PlayerEntity> entities = normalizedName == null || normalizedName.isEmpty()
                ? playerRepository.findAllByOrderByCreatedAtAscIdAsc()
                : playerRepository
                        .findByDisplayNameContainingIgnoreCaseOrderByCreatedAtAscIdAsc(
                                normalizedName);

        if (entities.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<PlayerSportProfile>> profilesByPlayer = profileRepository
                .findAllByPlayerIdInOrderByCreatedAtAsc(
                        entities.stream().map(PlayerEntity::getId).toList())
                .stream()
                .map(PlayerSportProfileEntity::toDomain)
                .collect(Collectors.groupingBy(PlayerSportProfile::playerId));

        List<Player> players = entities.stream()
                .map(PlayerEntity::toDomain)
                .toList();
        List<PlayerSportProfile> profiles = players.stream()
                .flatMap(player -> profilesByPlayer
                        .getOrDefault(player.id(), List.of())
                        .stream())
                .toList();
        Map<UUID, PlayerManagementRatingSnapshot> ratings =
                readEffectiveRatings(profiles);

        return players.stream()
                .map(player -> result(
                        player,
                        profilesByPlayer.getOrDefault(player.id(), List.of()),
                        ratings
                ))
                .toList();
    }

    @Transactional
    public PlayerResult updateSkillLevel(UpdatePlayerSkillLevelCommand command) {
        Objects.requireNonNull(command, "command is required");
        Objects.requireNonNull(command.playerId(), "playerId is required");
        Objects.requireNonNull(command.sportCode(), "sportCode is required");
        Objects.requireNonNull(command.skillLevel(), "skillLevel is required");

        Player player = playerRepository.findById(command.playerId())
                .map(PlayerEntity::toDomain)
                .orElseThrow(() -> new PlayerNotFoundException(command.playerId()));
        PlayerSportProfileEntity profileEntity = profileRepository
                .findByPlayerIdAndSportCode(
                        command.playerId(),
                        command.sportCode()
                )
                .orElseThrow(() -> new PlayerSportProfileNotFoundException(
                        command.playerId(),
                        command.sportCode()
                ));

        PlayerSportProfile updated = profileEntity.toDomain().changeSkillLevel(
                command.skillLevel(),
                Instant.now()
        );
        profileEntity.applyProfile(updated);
        profileRepository.flush();

        List<PlayerSportProfile> profiles = profileRepository
                .findAllByPlayerIdOrderByCreatedAtAsc(command.playerId())
                .stream()
                .map(PlayerSportProfileEntity::toDomain)
                .toList();
        return result(player, profiles);
    }

    @Transactional(readOnly = true)
    public PlayerManagementRatingHistoryResult getRatingHistory(
            UUID playerId,
            SportCode sportCode,
            MatchFormat matchFormat
    ) {
        Objects.requireNonNull(playerId, "playerId is required");
        Objects.requireNonNull(sportCode, "sportCode is required");
        Objects.requireNonNull(matchFormat, "matchFormat is required");

        if (!playerRepository.existsById(playerId)) {
            throw new PlayerNotFoundException(playerId);
        }
        if (!profileRepository.existsByPlayerIdAndSportCode(
                playerId,
                sportCode
        )) {
            throw new PlayerSportProfileNotFoundException(
                    playerId,
                    sportCode
            );
        }
        return new PlayerManagementRatingHistoryResult(
                playerId,
                sportCode,
                matchFormat,
                ratingHistoryReader.readHistory(
                        playerId,
                        sportCode,
                        matchFormat
                )
        );
    }

    private PlayerResult result(
            Player player,
            List<PlayerSportProfile> profiles
    ) {
        return result(player, profiles, readEffectiveRatings(profiles));
    }

    private PlayerResult result(
            Player player,
            List<PlayerSportProfile> profiles,
            Map<UUID, PlayerManagementRatingSnapshot> ratings
    ) {
        return new PlayerResult(
                player,
                profiles.stream()
                        .map(profile -> new PlayerSportProfileResult(
                                profile,
                                Objects.requireNonNull(
                                        ratings.get(profile.id()),
                                        "Rating is required for profile " + profile.id()
                                )
                        ))
                        .toList()
        );
    }

    private Map<UUID, PlayerManagementRatingSnapshot> readEffectiveRatings(
            List<PlayerSportProfile> profiles
    ) {
        return ratingReader.readEffectiveRatings(
                profiles,
                MatchFormat.DOUBLES
        );
    }

    private boolean violatesPlayerSportUniqueConstraint(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && PLAYER_SPORT_UNIQUE_CONSTRAINT.equals(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

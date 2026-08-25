package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.DuplicatePlayerSportProfileException;
import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerNotFoundException;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.infrastructure.PlayerEntity;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlayerService {

    private static final String PLAYER_SPORT_UNIQUE_CONSTRAINT =
            "uk_player_sport_profiles_player_sport";

    private final PlayerRepository playerRepository;
    private final PlayerSportProfileRepository profileRepository;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerSportProfileRepository profileRepository
    ) {
        this.playerRepository = playerRepository;
        this.profileRepository = profileRepository;
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

        return new PlayerResult(player, List.of(profile));
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
        return new PlayerResult(player, profiles);
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

        return entities.stream()
                .map(PlayerEntity::toDomain)
                .map(player -> new PlayerResult(
                        player, profilesByPlayer.getOrDefault(player.id(), List.of())))
                .toList();
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

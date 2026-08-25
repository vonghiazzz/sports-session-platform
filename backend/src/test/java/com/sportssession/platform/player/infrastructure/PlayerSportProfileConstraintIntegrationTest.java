package com.sportssession.platform.player.infrastructure;

import com.sportssession.platform.player.domain.Player;
import com.sportssession.platform.player.domain.PlayerSportProfile;
import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerSportProfileConstraintIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSportProfileRepository profileRepository;

    @BeforeEach
    void cleanDatabase() {
        profileRepository.deleteAll();
        playerRepository.deleteAll();
    }

    @Test
    void samePlayerCannotHaveDuplicateProfilesForOneSport() {
        Instant now = Instant.now();
        Player player = Player.create("Player A", now);
        playerRepository.saveAndFlush(PlayerEntity.from(player));

        PlayerSportProfile first = PlayerSportProfile.create(
                player.id(), SportCode.BADMINTON, SkillLevel.WEAK, now);
        PlayerSportProfile duplicate = PlayerSportProfile.create(
                player.id(), SportCode.BADMINTON, SkillLevel.GOOD, now);
        profileRepository.saveAndFlush(PlayerSportProfileEntity.from(first));

        assertThatThrownBy(() -> profileRepository.saveAndFlush(
                PlayerSportProfileEntity.from(duplicate)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

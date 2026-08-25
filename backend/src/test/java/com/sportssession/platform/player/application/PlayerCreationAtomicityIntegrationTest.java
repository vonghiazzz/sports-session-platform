package com.sportssession.platform.player.application;

import com.sportssession.platform.player.domain.SkillLevel;
import com.sportssession.platform.shared.domain.SportCode;
import com.sportssession.platform.player.infrastructure.PlayerRepository;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileEntity;
import com.sportssession.platform.player.infrastructure.PlayerSportProfileRepository;
import com.sportssession.platform.support.PostgreSqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlayerCreationAtomicityIntegrationTest extends PostgreSqlIntegrationTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private PlayerRepository playerRepository;

    @MockitoBean
    private PlayerSportProfileRepository profileRepository;

    @BeforeEach
    void cleanPlayers() {
        playerRepository.deleteAll();
    }

    @Test
    void playerInsertRollsBackWhenSportProfileInsertFails() {
        when(profileRepository.saveAndFlush(any(PlayerSportProfileEntity.class)))
                .thenThrow(new DataIntegrityViolationException("simulated profile failure"));

        CreatePlayerCommand command = new CreatePlayerCommand(
                "Player A", SportCode.BADMINTON, SkillLevel.INTERMEDIATE);

        assertThatThrownBy(() -> playerService.createPlayer(command))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(playerRepository.count()).isZero();
    }
}

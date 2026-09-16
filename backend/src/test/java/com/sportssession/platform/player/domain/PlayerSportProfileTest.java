package com.sportssession.platform.player.domain;

import com.sportssession.platform.shared.domain.SportCode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerSportProfileTest {

    @Test
    void changesOnlyHostAssessedSkillLevelAndUpdatedAt() {
        Instant createdAt = Instant.parse("2026-09-11T01:00:00Z");
        PlayerSportProfile profile = new PlayerSportProfile(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SportCode.BADMINTON,
                SkillLevel.INTERMEDIATE,
                createdAt,
                createdAt
        );
        Instant updatedAt = createdAt.plusSeconds(60);

        PlayerSportProfile changed = profile.changeSkillLevel(
                SkillLevel.INTERMEDIATE_PLUS,
                updatedAt
        );

        assertThat(changed.id()).isEqualTo(profile.id());
        assertThat(changed.playerId()).isEqualTo(profile.playerId());
        assertThat(changed.sportCode()).isEqualTo(profile.sportCode());
        assertThat(changed.skillLevel()).isEqualTo(SkillLevel.INTERMEDIATE_PLUS);
        assertThat(changed.createdAt()).isEqualTo(createdAt);
        assertThat(changed.updatedAt()).isEqualTo(updatedAt);
        assertThat(profile.skillLevel()).isEqualTo(SkillLevel.INTERMEDIATE);
    }

    @Test
    void rejectsAnUpdateTimeBeforeProfileCreation() {
        Instant createdAt = Instant.parse("2026-09-11T01:00:00Z");
        PlayerSportProfile profile = PlayerSportProfile.create(
                UUID.randomUUID(),
                SportCode.BADMINTON,
                SkillLevel.INTERMEDIATE,
                createdAt
        );

        assertThatThrownBy(() -> profile.changeSkillLevel(
                SkillLevel.GOOD,
                createdAt.minusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("updatedAt must not be before createdAt");
    }
}

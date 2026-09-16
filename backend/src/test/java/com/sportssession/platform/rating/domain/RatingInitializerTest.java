package com.sportssession.platform.rating.domain;

import com.sportssession.platform.player.domain.SkillLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class RatingInitializerTest {

    @ParameterizedTest
    @MethodSource("skillPriors")
    void initializesEverySkillLevelWithLockedPrior(
            SkillLevel skillLevel,
            double expectedMu
    ) {
        RatingState state = RatingInitializer.initialize(skillLevel);

        assertThat(state.mu()).isEqualTo(expectedMu);
        assertThat(state.sigma()).isEqualTo(8.333333333);
    }

    @Test
    void rejectsNullSkillLevel() {
        assertThatNullPointerException()
                .isThrownBy(() -> RatingInitializer.initialize(null))
                .withMessage("skillLevel is required");
    }

    private static Stream<Arguments> skillPriors() {
        return Stream.of(
                Arguments.of(SkillLevel.WEAK, 15.0),
                Arguments.of(SkillLevel.WEAK_PLUS, 19.0),
                Arguments.of(SkillLevel.INTERMEDIATE_MINUS, 23.0),
                Arguments.of(SkillLevel.INTERMEDIATE, 27.0),
                Arguments.of(SkillLevel.INTERMEDIATE_PLUS, 31.0),
                Arguments.of(SkillLevel.GOOD, 35.0)
        );
    }
}

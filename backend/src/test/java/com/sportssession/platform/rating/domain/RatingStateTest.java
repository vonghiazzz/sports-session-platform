package com.sportssession.platform.rating.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingStateTest {

    @Test
    void normalizesCanonicalStateToNineDecimalPlaces() {
        RatingState state = new RatingState(
                25.1234567896,
                8.3333333334
        );

        assertThat(state.mu()).isEqualTo(25.123456790);
        assertThat(state.sigma()).isEqualTo(8.333333333);
    }

    @ParameterizedTest
    @ValueSource(doubles = {
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
    })
    void rejectsNonFiniteMu(double mu) {
        assertThatThrownBy(() -> new RatingState(mu, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mu must be finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
    })
    void rejectsNonFiniteSigma(double sigma) {
        assertThatThrownBy(() -> new RatingState(25.0, sigma))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sigma must be finite");
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.000000001, -1.0})
    void rejectsNonPositiveSigma(double sigma) {
        assertThatThrownBy(() -> new RatingState(25.0, sigma))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sigma must be greater than zero");
    }

    @Test
    void rejectsPositiveSigmaThatNormalizesToZero() {
        assertThatThrownBy(() -> new RatingState(25.0, 0.0000000001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("normalized sigma must be greater than zero");
    }
}

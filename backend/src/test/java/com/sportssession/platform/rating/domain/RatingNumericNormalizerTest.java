package com.sportssession.platform.rating.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RatingNumericNormalizerTest {

    @Test
    void usesScaleNineAndHalfEven() {
        BigDecimal normalized = RatingNumericNormalizer.normalizeToDecimal(
                1.2345678905
        );

        assertThat(normalized).isEqualTo(new BigDecimal("1.234567890"));
        assertThat(normalized.scale()).isEqualTo(9);
        assertThat(RatingNumericNormalizer.ROUNDING_MODE)
                .isEqualTo(RoundingMode.HALF_EVEN);
    }

    @Test
    void roundsPositiveAndNegativeValues() {
        assertThat(RatingNumericNormalizer.normalizeToDecimal(1.2345678906))
                .isEqualTo(new BigDecimal("1.234567891"));
        assertThat(RatingNumericNormalizer.normalizeToDecimal(-1.2345678906))
                .isEqualTo(new BigDecimal("-1.234567891"));
    }

    @Test
    void roundsHalfEvenToAnEvenLastDigitInEitherDirection() {
        assertThat(RatingNumericNormalizer.normalizeToDecimal(2.0000000025))
                .isEqualTo(new BigDecimal("2.000000002"));
        assertThat(RatingNumericNormalizer.normalizeToDecimal(2.0000000035))
                .isEqualTo(new BigDecimal("2.000000004"));
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatThrownBy(() -> RatingNumericNormalizer.normalize(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatingNumericNormalizer.normalize(
                Double.POSITIVE_INFINITY
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RatingNumericNormalizer.normalize(
                Double.NEGATIVE_INFINITY
        )).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.sportssession.platform.rating.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class RatingNumericNormalizer {

    public static final int SCALE = 9;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private RatingNumericNormalizer() {
    }

    public static double normalize(double value) {
        return normalizeToDecimal(value).doubleValue();
    }

    public static BigDecimal normalizeToDecimal(double value) {
        requireFinite(value, "rating value");
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING_MODE);
    }

    static void requireFinite(double value, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
    }
}

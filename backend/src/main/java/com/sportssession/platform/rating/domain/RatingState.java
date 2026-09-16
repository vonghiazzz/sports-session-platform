package com.sportssession.platform.rating.domain;

public record RatingState(double mu, double sigma) {

    public RatingState {
        RatingNumericNormalizer.requireFinite(mu, "mu");
        RatingNumericNormalizer.requireFinite(sigma, "sigma");
        if (sigma <= 0.0) {
            throw new IllegalArgumentException("sigma must be greater than zero");
        }

        mu = RatingNumericNormalizer.normalize(mu);
        sigma = RatingNumericNormalizer.normalize(sigma);
        if (sigma <= 0.0) {
            throw new IllegalArgumentException(
                    "normalized sigma must be greater than zero");
        }
    }
}

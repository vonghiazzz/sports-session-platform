package com.sportssession.platform.rating.application;

public class UnsupportedRatingResultVersionException extends RuntimeException {

    public UnsupportedRatingResultVersionException(int resultVersion) {
        super("Rating V1 supports only resultVersion 1, received " + resultVersion);
    }
}

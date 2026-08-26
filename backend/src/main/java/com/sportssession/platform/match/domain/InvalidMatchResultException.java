package com.sportssession.platform.match.domain;

public class InvalidMatchResultException extends IllegalArgumentException {

    public InvalidMatchResultException(String message) {
        super(message);
    }
}

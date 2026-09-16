package com.sportssession.platform.match.domain;

public class InvalidMatchStateException extends RuntimeException {

    public InvalidMatchStateException(String message) {
        super(message);
    }
}

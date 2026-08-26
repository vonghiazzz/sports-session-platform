package com.sportssession.platform.match.domain;

public class InvalidManualMatchRequestException extends RuntimeException {

    public InvalidManualMatchRequestException(String message) {
        super(message);
    }
}

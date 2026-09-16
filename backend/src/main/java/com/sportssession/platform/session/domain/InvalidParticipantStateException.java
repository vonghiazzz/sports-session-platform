package com.sportssession.platform.session.domain;

public class InvalidParticipantStateException extends RuntimeException {

    public InvalidParticipantStateException(String message) {
        super(message);
    }
}

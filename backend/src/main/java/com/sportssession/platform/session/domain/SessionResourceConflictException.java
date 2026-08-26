package com.sportssession.platform.session.domain;

public class SessionResourceConflictException extends RuntimeException {

    public SessionResourceConflictException(String message) {
        super(message);
    }
}

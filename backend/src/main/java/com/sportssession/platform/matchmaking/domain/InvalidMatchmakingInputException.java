package com.sportssession.platform.matchmaking.domain;

public class InvalidMatchmakingInputException extends RuntimeException {

    public InvalidMatchmakingInputException(String message) {
        super(message);
    }
}

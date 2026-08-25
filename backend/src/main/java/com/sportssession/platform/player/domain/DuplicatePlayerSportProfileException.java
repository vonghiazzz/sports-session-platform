package com.sportssession.platform.player.domain;

public class DuplicatePlayerSportProfileException extends RuntimeException {

    public DuplicatePlayerSportProfileException(Throwable cause) {
        super("A player can have only one profile for each sport", cause);
    }
}


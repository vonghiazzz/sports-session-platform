package com.sportssession.platform.session.domain;

public class PlayerSessionAccessNotFoundException extends RuntimeException {

    public PlayerSessionAccessNotFoundException() {
        super("Player Session access was not found");
    }
}

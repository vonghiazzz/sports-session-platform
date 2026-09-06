package com.sportssession.platform.matchplan.domain;

public class InvalidMatchPlanRequestException extends RuntimeException {
    public InvalidMatchPlanRequestException(String message) {
        super(message);
    }
}

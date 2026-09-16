package com.sportssession.platform.session.domain;

public class InvalidSessionTimeRangeException extends RuntimeException {

    public InvalidSessionTimeRangeException() {
        super("plannedEndAt must be after plannedStartAt");
    }
}

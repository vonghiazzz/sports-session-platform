package com.sportssession.platform.matchmaking.application;

public enum MatchmakingRecommendationFailureReason {
    SESSION_NOT_IN_PROGRESS,
    SESSION_COURT_NOT_AVAILABLE,
    WAITING_PARTICIPANT_MISSING_WAITING_SINCE,
    WAITING_PARTICIPANT_WAITING_SINCE_AFTER_EVALUATION_TIME,
    RATING_BATCH_INCOMPLETE
}

package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;

import java.util.List;

public record CreatedManualMatch(
        Match match,
        List<MatchParticipant> participants
) {
    public CreatedManualMatch {
        participants = List.copyOf(participants);
    }
}

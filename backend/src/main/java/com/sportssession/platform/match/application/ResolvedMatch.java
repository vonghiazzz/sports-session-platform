package com.sportssession.platform.match.application;

import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;

import java.util.List;

public record ResolvedMatch(
        Match match,
        List<MatchParticipant> participants
) {
    public ResolvedMatch {
        participants = List.copyOf(participants);
    }
}

package com.sportssession.platform.match.api;

import com.sportssession.platform.match.application.CreatedManualMatch;
import com.sportssession.platform.match.domain.Match;
import com.sportssession.platform.match.domain.MatchParticipant;
import com.sportssession.platform.match.domain.MatchSource;
import com.sportssession.platform.match.domain.MatchStatus;
import com.sportssession.platform.match.domain.TeamSide;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID sessionId,
        UUID sessionCourtId,
        MatchStatus status,
        MatchSource source,
        TeamSide winnerTeam,
        Integer teamAScore,
        Integer teamBScore,
        int resultVersion,
        List<MatchParticipantResponse> participants,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant updatedAt,
        long version
) {
    static MatchResponse from(CreatedManualMatch created) {
        return from(created.match(), created.participants());
    }

    public static MatchResponse from(
            Match match,
            List<MatchParticipant> participants
    ) {

        return new MatchResponse(
                match.id(),
                match.sessionId(),
                match.sessionCourtId(),
                match.status(),
                match.source(),
                match.result() == null ? null : match.result().winnerTeam(),
                match.result() == null ? null : match.result().teamAScore(),
                match.result() == null ? null : match.result().teamBScore(),
                match.resultVersion(),
                participants.stream()
                        .map(MatchParticipantResponse::from)
                        .toList(),
                match.createdAt(),
                match.startedAt(),
                match.completedAt(),
                match.cancelledAt(),
                match.updatedAt(),
                match.version()
        );
    }
}

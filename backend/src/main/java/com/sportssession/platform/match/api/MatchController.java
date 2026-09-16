package com.sportssession.platform.match.api;

import com.sportssession.platform.match.application.CreateManualMatchCommand;
import com.sportssession.platform.match.application.CreatedManualMatch;
import com.sportssession.platform.match.application.CompleteMatchCommand;
import com.sportssession.platform.match.application.MatchService;
import com.sportssession.platform.match.application.ResolvedMatch;
import com.sportssession.platform.match.application.StartedMatch;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/api/sessions/{sessionId}/matches")
    public List<MatchResponse> getSessionMatches(@PathVariable UUID sessionId) {
        return matchService.getSessionMatches(sessionId)
                .stream()
                .map(match -> MatchResponse.from(
                        match.match(),
                        match.participants()
                ))
                .toList();
    }

    @PostMapping("/api/sessions/{sessionId}/matches")
    public ResponseEntity<MatchResponse> createManualMatch(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CreateManualMatchRequest request
    ) {
        CreatedManualMatch created = matchService.createManualMatch(
                new CreateManualMatchCommand(
                        sessionId,
                        request.sessionCourtId(),
                        request.participants().stream()
                                .map(MatchParticipantRequest::toAssignment)
                                .toList()
                )
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{matchId}")
                .buildAndExpand(created.match().id())
                .toUri();

        return ResponseEntity.created(location)
                .body(MatchResponse.from(created));
    }

    @PostMapping("/api/matches/{matchId}/start")
    public MatchResponse startMatch(@PathVariable UUID matchId) {
        StartedMatch started = matchService.startMatch(matchId);
        return MatchResponse.from(
                started.match(),
                started.participants()
        );
    }

    @PostMapping("/api/matches/{matchId}/complete")
    public MatchResponse completeMatch(
            @PathVariable UUID matchId,
            @Valid @RequestBody CompleteMatchRequest request
    ) {
        ResolvedMatch completed = matchService.completeMatch(
                new CompleteMatchCommand(
                        matchId,
                        request.winnerTeam(),
                        request.teamAScore(),
                        request.teamBScore()
                )
        );
        return MatchResponse.from(
                completed.match(),
                completed.participants()
        );
    }

    @PostMapping("/api/matches/{matchId}/cancel")
    public MatchResponse cancelMatch(@PathVariable UUID matchId) {
        ResolvedMatch cancelled = matchService.cancelMatch(matchId);
        return MatchResponse.from(
                cancelled.match(),
                cancelled.participants()
        );
    }
}

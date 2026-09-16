package com.sportssession.platform.matchplan.api;

import com.sportssession.platform.matchplan.application.CreateMatchPlanCommand;
import com.sportssession.platform.matchplan.application.MatchPlanService;
import com.sportssession.platform.matchplan.application.UpdateMatchPlanCommand;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
public class MatchPlanController {

    private final MatchPlanService service;

    public MatchPlanController(MatchPlanService service) {
        this.service = service;
    }

    @PostMapping("/api/sessions/{sessionId}/courts/{sessionCourtId}/match-plans")
    public ResponseEntity<MatchPlanResponse> create(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId,
            @Valid @RequestBody CreateMatchPlanRequest request
    ) {
        MatchPlanResponse response = MatchPlanResponse.from(service.create(
                new CreateMatchPlanCommand(
                        sessionId, sessionCourtId,
                        request.participants().stream()
                                .map(MatchPlanParticipantRequest::toAssignment)
                                .toList()
                )
        ));
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/match-plans/{planId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/api/sessions/{sessionId}/match-plans")
    public List<MatchPlanResponse> list(@PathVariable UUID sessionId) {
        return service.list(sessionId).stream()
                .map(MatchPlanResponse::from)
                .toList();
    }

    @PutMapping("/api/match-plans/{planId}")
    public MatchPlanResponse update(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdateMatchPlanRequest request
    ) {
        return MatchPlanResponse.from(service.update(
                new UpdateMatchPlanCommand(
                        planId,
                        request.participants().stream()
                                .map(MatchPlanParticipantRequest::toAssignment)
                                .toList()
                )
        ));
    }

    @PostMapping("/api/match-plans/{planId}/move")
    public MatchPlanResponse move(
            @PathVariable UUID planId,
            @Valid @RequestBody MoveMatchPlanRequest request
    ) {
        return MatchPlanResponse.from(service.move(
                planId, request.targetSessionCourtId()
        ));
    }

    @PostMapping("/api/match-plans/{planId}/reorder")
    public MatchPlanResponse reorder(
            @PathVariable UUID planId,
            @Valid @RequestBody ReorderMatchPlanRequest request
    ) {
        return MatchPlanResponse.from(service.reorder(
                planId, request.targetPosition()
        ));
    }

    @PostMapping("/api/match-plans/{planId}/cancel")
    public MatchPlanResponse cancel(@PathVariable UUID planId) {
        return MatchPlanResponse.from(service.cancel(planId));
    }

    @PostMapping("/api/match-plans/{planId}/start")
    public StartedMatchPlanResponse start(@PathVariable UUID planId) {
        return StartedMatchPlanResponse.from(service.start(planId));
    }
}

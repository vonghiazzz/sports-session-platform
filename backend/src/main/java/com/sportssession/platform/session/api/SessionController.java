package com.sportssession.platform.session.api;

import com.sportssession.platform.session.application.AddParticipantCommand;
import com.sportssession.platform.session.application.AddSessionCourtCommand;
import com.sportssession.platform.session.application.CreateSessionCommand;
import com.sportssession.platform.session.application.SessionService;
import com.sportssession.platform.session.domain.Session;
import com.sportssession.platform.session.domain.SessionCourt;
import com.sportssession.platform.session.domain.SessionParticipant;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @Valid @RequestBody CreateSessionRequest request
    ) {
        Session created = sessionService.createSession(new CreateSessionCommand(
                request.venueId(),
                request.title(),
                request.sport(),
                request.matchFormat(),
                request.plannedStartAt(),
                request.plannedEndAt()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(SessionResponse.from(created));
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(@PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.getSession(sessionId));
    }

    @PostMapping("/{sessionId}/start")
    public SessionResponse startSession(@PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.startSession(sessionId));
    }

    @PostMapping("/{sessionId}/complete")
    public SessionResponse completeSession(@PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.completeSession(sessionId));
    }

    @PostMapping("/{sessionId}/cancel")
    public SessionResponse cancelSession(@PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.cancelSession(sessionId));
    }

    @PostMapping("/{sessionId}/participants")
    public ResponseEntity<SessionParticipantResponse> addParticipant(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AddParticipantRequest request
    ) {
        SessionParticipant created = sessionService.addParticipant(
                new AddParticipantCommand(sessionId, request.playerId()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{participantId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location)
                .body(SessionParticipantResponse.from(created));
    }

    @GetMapping("/{sessionId}/participants")
    public List<SessionParticipantResponse> listParticipants(
            @PathVariable UUID sessionId
    ) {
        return sessionService.listParticipants(sessionId).stream()
                .map(SessionParticipantResponse::from)
                .toList();
    }

    @PostMapping("/{sessionId}/participants/{participantId}/check-in")
    public SessionParticipantResponse checkInParticipant(
            @PathVariable UUID sessionId,
            @PathVariable UUID participantId
    ) {
        return SessionParticipantResponse.from(
                sessionService.checkInParticipant(sessionId, participantId));
    }

    @PostMapping("/{sessionId}/participants/{participantId}/pause")
    public SessionParticipantResponse pauseParticipant(
            @PathVariable UUID sessionId,
            @PathVariable UUID participantId
    ) {
        return SessionParticipantResponse.from(
                sessionService.pauseParticipant(sessionId, participantId));
    }

    @PostMapping("/{sessionId}/participants/{participantId}/resume")
    public SessionParticipantResponse resumeParticipant(
            @PathVariable UUID sessionId,
            @PathVariable UUID participantId
    ) {
        return SessionParticipantResponse.from(
                sessionService.resumeParticipant(sessionId, participantId));
    }

    @PostMapping("/{sessionId}/participants/{participantId}/leave")
    public SessionParticipantResponse leaveParticipant(
            @PathVariable UUID sessionId,
            @PathVariable UUID participantId
    ) {
        return SessionParticipantResponse.from(
                sessionService.leaveParticipant(sessionId, participantId));
    }

    @PostMapping("/{sessionId}/courts")
    public ResponseEntity<SessionCourtResponse> addCourt(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AddSessionCourtRequest request
    ) {
        SessionCourt created = sessionService.addCourt(
                new AddSessionCourtCommand(sessionId, request.courtId()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{sessionCourtId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(SessionCourtResponse.from(created));
    }

    @GetMapping("/{sessionId}/courts")
    public List<SessionCourtResponse> listCourts(@PathVariable UUID sessionId) {
        return sessionService.listCourts(sessionId).stream()
                .map(SessionCourtResponse::from)
                .toList();
    }

    @PostMapping("/{sessionId}/courts/{sessionCourtId}/disable")
    public SessionCourtResponse disableCourt(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId
    ) {
        return SessionCourtResponse.from(
                sessionService.disableCourt(sessionId, sessionCourtId));
    }

    @PostMapping("/{sessionId}/courts/{sessionCourtId}/enable")
    public SessionCourtResponse enableCourt(
            @PathVariable UUID sessionId,
            @PathVariable UUID sessionCourtId
    ) {
        return SessionCourtResponse.from(
                sessionService.enableCourt(sessionId, sessionCourtId));
    }
}

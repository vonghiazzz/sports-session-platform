package com.sportssession.platform.session.api;

import com.sportssession.platform.session.application.SessionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/player-session-access")
public class PlayerSessionAccessController {

    private final SessionService sessionService;

    public PlayerSessionAccessController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/{token}")
    public PlayerSessionAccessResponse resolve(@PathVariable UUID token) {
        return PlayerSessionAccessResponse.from(
                sessionService.resolvePlayerSessionAccess(token)
        );
    }
}

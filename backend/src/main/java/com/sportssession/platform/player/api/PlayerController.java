package com.sportssession.platform.player.api;

import com.sportssession.platform.player.application.CreatePlayerCommand;
import com.sportssession.platform.player.application.PlayerResult;
import com.sportssession.platform.player.application.PlayerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping
    public ResponseEntity<PlayerResponse> createPlayer(
            @Valid @RequestBody CreatePlayerRequest request
    ) {
        PlayerResult created = playerService.createPlayer(new CreatePlayerCommand(
                request.displayName(), request.sport(), request.skillLevel()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{playerId}")
                .buildAndExpand(created.player().id())
                .toUri();
        return ResponseEntity.created(location).body(PlayerResponse.from(created));
    }

    @GetMapping("/{playerId}")
    public PlayerResponse getPlayer(@PathVariable UUID playerId) {
        return PlayerResponse.from(playerService.getPlayer(playerId));
    }

    @GetMapping
    public List<PlayerResponse> searchPlayers(
            @RequestParam(required = false) String name
    ) {
        return playerService.searchPlayers(name).stream()
                .map(PlayerResponse::from)
                .toList();
    }
}


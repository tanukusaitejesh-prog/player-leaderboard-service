package dev.tejesh.leaderboard.player;

import dev.tejesh.leaderboard.player.dto.CreatePlayerRequest;
import dev.tejesh.leaderboard.player.dto.PlayerResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/players")
public class PlayerController {

  private final PlayerService players;

  public PlayerController(PlayerService players) {
    this.players = players;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PlayerResponse create(@Valid @RequestBody CreatePlayerRequest request) {
    return PlayerResponse.from(players.create(request.username()));
  }

  @GetMapping("/{id}")
  public PlayerResponse get(@PathVariable UUID id) {
    return PlayerResponse.from(players.get(id));
  }
}

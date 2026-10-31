package dev.tejesh.leaderboard.session;

import dev.tejesh.leaderboard.session.dto.SessionResponse;
import dev.tejesh.leaderboard.session.dto.StartSessionRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

  private final SessionService sessions;

  public SessionController(SessionService sessions) {
    this.sessions = sessions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SessionResponse start(@Valid @RequestBody StartSessionRequest request) {
    return SessionResponse.from(sessions.start(request.playerId(), request.gameId()));
  }

  @PostMapping("/{id}/end")
  public SessionResponse end(@PathVariable UUID id) {
    return SessionResponse.from(sessions.end(id));
  }
}

package dev.tejesh.leaderboard.session;

import dev.tejesh.leaderboard.player.PlayerRepository;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

  private final GameSessionRepository sessions;
  private final PlayerRepository players;
  private final Clock clock;

  public SessionService(GameSessionRepository sessions, PlayerRepository players, Clock clock) {
    this.sessions = sessions;
    this.players = players;
    this.clock = clock;
  }

  @Transactional
  public GameSession start(UUID playerId, String gameId) {
    if (!players.existsById(playerId)) {
      throw new NotFoundException("Player " + playerId + " not found");
    }
    return sessions.save(new GameSession(UUID.randomUUID(), playerId, gameId, clock.instant()));
  }

  @Transactional
  public GameSession end(UUID sessionId) {
    GameSession session =
        sessions
            .findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session " + sessionId + " not found"));
    session.end(clock.instant());
    return session;
  }
}

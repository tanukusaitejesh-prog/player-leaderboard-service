package dev.tejesh.leaderboard.player;

import dev.tejesh.leaderboard.web.ConflictException;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class PlayerService {

  private final PlayerRepository players;
  private final Clock clock;

  public PlayerService(PlayerRepository players, Clock clock) {
    this.players = players;
    this.clock = clock;
  }

  /** Relies on the UNIQUE constraint rather than check-then-insert, which would race. */
  public Player create(String username) {
    try {
      return players.saveAndFlush(new Player(UUID.randomUUID(), username, clock.instant()));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException("Username '" + username + "' is already taken");
    }
  }

  public Player get(UUID id) {
    return players
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Player " + id + " not found"));
  }
}

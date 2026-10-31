package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

  private final LeaderboardStore store;

  public LeaderboardService(LeaderboardStore store) {
    this.store = store;
  }

  public List<LeaderboardEntry> top(String gameId, int limit) {
    return store.top(gameId, limit);
  }

  public PlayerStanding standing(String gameId, UUID playerId) {
    return store
        .standing(gameId, playerId)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Player " + playerId + " is not on the board for game " + gameId));
  }
}

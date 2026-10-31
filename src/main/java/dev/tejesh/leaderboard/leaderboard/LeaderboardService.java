package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import dev.tejesh.leaderboard.web.NotFoundException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

  private final LeaderboardStore store;
  private final Timer topTimer;
  private final Timer standingTimer;

  public LeaderboardService(LeaderboardStore store, MeterRegistry registry) {
    this.store = store;
    this.topTimer = readTimer(registry, "top");
    this.standingTimer = readTimer(registry, "standing");
  }

  private static Timer readTimer(MeterRegistry registry, String operation) {
    return Timer.builder("leaderboard.read.duration")
        .tag("operation", operation)
        .publishPercentileHistogram()
        .register(registry);
  }

  public List<LeaderboardEntry> top(String gameId, int limit) {
    return topTimer.record(() -> store.top(gameId, limit));
  }

  public PlayerStanding standing(String gameId, UUID playerId) {
    return standingTimer
        .record(() -> store.standing(gameId, playerId))
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Player " + playerId + " is not on the board for game " + gameId));
  }
}

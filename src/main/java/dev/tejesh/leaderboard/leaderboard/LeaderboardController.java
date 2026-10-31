package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import dev.tejesh.leaderboard.leaderboard.dto.RebuildResponse;
import dev.tejesh.leaderboard.web.GameIds;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LeaderboardController {

  private final LeaderboardService leaderboards;
  private final RebuildService rebuilds;

  public LeaderboardController(LeaderboardService leaderboards, RebuildService rebuilds) {
    this.leaderboards = leaderboards;
    this.rebuilds = rebuilds;
  }

  @GetMapping("/leaderboards/{gameId}/top")
  public List<LeaderboardEntry> top(
      @PathVariable @Pattern(regexp = GameIds.PATTERN) String gameId,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
    return leaderboards.top(gameId, limit);
  }

  @GetMapping("/leaderboards/{gameId}/players/{playerId}")
  public PlayerStanding standing(
      @PathVariable @Pattern(regexp = GameIds.PATTERN) String gameId, @PathVariable UUID playerId) {
    return leaderboards.standing(gameId, playerId);
  }

  @PostMapping("/admin/leaderboards/{gameId}/rebuild")
  public RebuildResponse rebuild(@PathVariable @Pattern(regexp = GameIds.PATTERN) String gameId) {
    return new RebuildResponse(gameId, rebuilds.rebuild(gameId));
  }
}

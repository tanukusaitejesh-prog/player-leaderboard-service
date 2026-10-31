package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port for the leaderboard read model. Redis is one adapter; tests use an in-memory one. */
public interface LeaderboardStore {

  /** Raises the player's score if this one is higher. Safe to repeat and to apply out of order. */
  void recordBest(String gameId, BoardScore score);

  List<LeaderboardEntry> top(String gameId, int limit);

  Optional<PlayerStanding> standing(String gameId, UUID playerId);

  /** Atomically swaps the whole board for these scores. */
  void replaceBoard(String gameId, List<BoardScore> scores);
}

package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** In-memory adapter with the same best-score semantics as the Redis one. */
class FakeLeaderboardStore implements LeaderboardStore {

  private final Map<String, Map<UUID, BoardScore>> boards = new HashMap<>();

  @Override
  public void recordBest(String gameId, BoardScore score) {
    boards
        .computeIfAbsent(gameId, g -> new HashMap<>())
        .merge(score.playerId(), score, (a, b) -> a.score() >= b.score() ? a : b);
  }

  @Override
  public List<LeaderboardEntry> top(String gameId, int limit) {
    List<LeaderboardEntry> entries = new ArrayList<>();
    long rank = 1;
    for (BoardScore score : sorted(gameId)) {
      if (rank > limit) {
        break;
      }
      entries.add(new LeaderboardEntry(rank++, score.playerId(), score.username(), score.score()));
    }
    return entries;
  }

  @Override
  public Optional<PlayerStanding> standing(String gameId, UUID playerId) {
    return top(gameId, Integer.MAX_VALUE).stream()
        .filter(entry -> entry.playerId().equals(playerId))
        .map(entry -> new PlayerStanding(entry.rank(), entry.score()))
        .findFirst();
  }

  @Override
  public void replaceBoard(String gameId, List<BoardScore> scores) {
    boards.remove(gameId);
    scores.forEach(score -> recordBest(gameId, score));
  }

  private List<BoardScore> sorted(String gameId) {
    return boards.getOrDefault(gameId, Map.of()).values().stream()
        .sorted(Comparator.comparingInt(BoardScore::score).reversed())
        .toList();
  }
}

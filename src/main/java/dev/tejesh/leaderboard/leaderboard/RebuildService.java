package dev.tejesh.leaderboard.leaderboard;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RebuildService {

  private final BestScoreQuery bestScores;
  private final LeaderboardStore store;

  public RebuildService(BestScoreQuery bestScores, LeaderboardStore store) {
    this.bestScores = bestScores;
    this.store = store;
  }

  /**
   * An event consumed while the temp board is being built lands on the old key and is lost by the
   * swap. Its score is already committed though, so a second read after the swap picks it up; any
   * event consumed after the swap hits the new board directly.
   */
  public int rebuild(String gameId) {
    List<BoardScore> snapshot = bestScores.forGame(gameId);
    store.replaceBoard(gameId, snapshot);

    List<BoardScore> latest = bestScores.forGame(gameId);
    Set<BoardScore> alreadyWritten = new HashSet<>(snapshot);
    latest.stream()
        .filter(score -> !alreadyWritten.contains(score))
        .forEach(score -> store.recordBest(gameId, score));
    return latest.size();
  }
}

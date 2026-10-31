package dev.tejesh.leaderboard.score;

import java.time.Instant;
import java.util.UUID;

/** Username travels in the event so the consumer never needs the database. */
public record ScoreSubmittedEvent(
    UUID eventId,
    UUID playerId,
    String username,
    String gameId,
    Integer points,
    Instant submittedAt) {

  public static ScoreSubmittedEvent of(Score score, String username) {
    return new ScoreSubmittedEvent(
        score.getId(),
        score.getPlayerId(),
        username,
        score.getGameId(),
        score.getPoints(),
        score.getSubmittedAt());
  }

  /** JSON can be well formed and still miss fields, so the consumer checks before writing. */
  public void requireValid() {
    if (playerId == null || username == null || gameId == null || points == null || points < 0) {
      throw new IllegalArgumentException("Invalid score event " + eventId);
    }
  }
}

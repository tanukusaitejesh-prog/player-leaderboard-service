package dev.tejesh.leaderboard.score.dto;

import dev.tejesh.leaderboard.score.Score;
import java.time.Instant;
import java.util.UUID;

public record ScoreResponse(
    UUID id, UUID playerId, UUID sessionId, String gameId, int points, Instant submittedAt) {

  public static ScoreResponse from(Score score) {
    return new ScoreResponse(
        score.getId(),
        score.getPlayerId(),
        score.getSessionId(),
        score.getGameId(),
        score.getPoints(),
        score.getSubmittedAt());
  }
}

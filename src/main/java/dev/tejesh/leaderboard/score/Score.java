package dev.tejesh.leaderboard.score;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable once written. Rows are inserted through {@link ScoreRepository#insertIfAbsent}. */
@Entity
@Table(name = "scores")
public class Score {

  @Id private UUID id;

  @Column(name = "player_id", nullable = false, updatable = false)
  private UUID playerId;

  @Column(name = "session_id", nullable = false, updatable = false)
  private UUID sessionId;

  @Column(name = "game_id", nullable = false, updatable = false, length = 64)
  private String gameId;

  @Column(nullable = false, updatable = false)
  private int points;

  @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
  private String idempotencyKey;

  @Column(name = "submitted_at", nullable = false, updatable = false)
  private Instant submittedAt;

  protected Score() {}

  public Score(
      UUID id,
      UUID playerId,
      UUID sessionId,
      String gameId,
      int points,
      String idempotencyKey,
      Instant submittedAt) {
    this.id = id;
    this.playerId = playerId;
    this.sessionId = sessionId;
    this.gameId = gameId;
    this.points = points;
    this.idempotencyKey = idempotencyKey;
    this.submittedAt = submittedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public UUID getSessionId() {
    return sessionId;
  }

  public String getGameId() {
    return gameId;
  }

  public int getPoints() {
    return points;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }
}

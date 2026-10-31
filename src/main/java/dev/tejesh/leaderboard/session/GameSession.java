package dev.tejesh.leaderboard.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "game_sessions")
public class GameSession implements Persistable<UUID> {

  @Id private UUID id;

  @Column(name = "player_id", nullable = false, updatable = false)
  private UUID playerId;

  @Column(name = "game_id", nullable = false, updatable = false, length = 64)
  private String gameId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SessionStatus status;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Transient private boolean isNew = true;

  protected GameSession() {}

  public GameSession(UUID id, UUID playerId, String gameId, Instant startedAt) {
    this.id = id;
    this.playerId = playerId;
    this.gameId = gameId;
    this.status = SessionStatus.ACTIVE;
    this.startedAt = startedAt;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  /** Ending an already ended session keeps the original end time. */
  public void end(Instant at) {
    if (status == SessionStatus.ACTIVE) {
      status = SessionStatus.ENDED;
      endedAt = at;
    }
  }

  public boolean isActive() {
    return status == SessionStatus.ACTIVE;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getPlayerId() {
    return playerId;
  }

  public String getGameId() {
    return gameId;
  }

  public SessionStatus getStatus() {
    return status;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }
}

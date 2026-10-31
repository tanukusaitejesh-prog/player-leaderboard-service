package dev.tejesh.leaderboard.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "players")
public class Player implements Persistable<UUID> {

  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String username;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Ids are assigned by the application, so Spring Data must be told when a row is new. */
  @Transient private boolean isNew = true;

  protected Player() {}

  public Player(UUID id, String username, Instant createdAt) {
    this.id = id;
    this.username = username;
    this.createdAt = createdAt;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }
}

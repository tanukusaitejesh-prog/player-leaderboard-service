package dev.tejesh.leaderboard.player.dto;

import dev.tejesh.leaderboard.player.Player;
import java.time.Instant;
import java.util.UUID;

public record PlayerResponse(UUID id, String username, Instant createdAt) {

  public static PlayerResponse from(Player player) {
    return new PlayerResponse(player.getId(), player.getUsername(), player.getCreatedAt());
  }
}

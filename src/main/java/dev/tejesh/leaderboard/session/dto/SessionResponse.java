package dev.tejesh.leaderboard.session.dto;

import dev.tejesh.leaderboard.session.GameSession;
import dev.tejesh.leaderboard.session.SessionStatus;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
    UUID id,
    UUID playerId,
    String gameId,
    SessionStatus status,
    Instant startedAt,
    Instant endedAt) {

  public static SessionResponse from(GameSession session) {
    return new SessionResponse(
        session.getId(),
        session.getPlayerId(),
        session.getGameId(),
        session.getStatus(),
        session.getStartedAt(),
        session.getEndedAt());
  }
}

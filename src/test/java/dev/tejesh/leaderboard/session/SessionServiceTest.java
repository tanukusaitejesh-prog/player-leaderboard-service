package dev.tejesh.leaderboard.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.tejesh.leaderboard.player.PlayerRepository;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final GameSessionRepository sessions = mock(GameSessionRepository.class);
  private final PlayerRepository players = mock(PlayerRepository.class);
  private final SessionService service =
      new SessionService(sessions, players, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void startCreatesActiveSessionForKnownPlayer() {
    UUID playerId = UUID.randomUUID();
    when(players.existsById(playerId)).thenReturn(true);
    when(sessions.save(any(GameSession.class))).thenAnswer(call -> call.getArgument(0));

    GameSession session = service.start(playerId, "tetris");

    assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
    assertThat(session.getPlayerId()).isEqualTo(playerId);
    assertThat(session.getGameId()).isEqualTo("tetris");
    assertThat(session.getStartedAt()).isEqualTo(NOW);
  }

  @Test
  void startRejectsUnknownPlayer() {
    UUID playerId = UUID.randomUUID();
    when(players.existsById(playerId)).thenReturn(false);

    assertThatThrownBy(() -> service.start(playerId, "tetris"))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void endingTwiceKeepsTheFirstEndTime() {
    Instant firstEnd = Instant.parse("2025-12-31T00:00:00Z");
    GameSession session = new GameSession(UUID.randomUUID(), UUID.randomUUID(), "tetris", firstEnd);
    session.end(firstEnd);
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    GameSession ended = service.end(session.getId());

    assertThat(ended.getStatus()).isEqualTo(SessionStatus.ENDED);
    assertThat(ended.getEndedAt()).isEqualTo(firstEnd);
  }

  @Test
  void endRejectsUnknownSession() {
    UUID id = UUID.randomUUID();
    when(sessions.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.end(id)).isInstanceOf(NotFoundException.class);
  }
}

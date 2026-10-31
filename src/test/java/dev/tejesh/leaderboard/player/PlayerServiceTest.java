package dev.tejesh.leaderboard.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.tejesh.leaderboard.web.ConflictException;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PlayerServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  private final PlayerRepository players = mock(PlayerRepository.class);
  private final PlayerService service =
      new PlayerService(players, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createStoresPlayerWithGeneratedIdAndClockTime() {
    when(players.saveAndFlush(any(Player.class))).thenAnswer(call -> call.getArgument(0));

    Player player = service.create("neo_1");

    assertThat(player.getId()).isNotNull();
    assertThat(player.getUsername()).isEqualTo("neo_1");
    assertThat(player.getCreatedAt()).isEqualTo(NOW);
  }

  @Test
  void createMapsUniqueViolationToConflict() {
    when(players.saveAndFlush(any(Player.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key"));

    assertThatThrownBy(() -> service.create("taken"))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("taken");
  }

  @Test
  void getThrowsNotFoundForUnknownId() {
    UUID id = UUID.randomUUID();
    when(players.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class);
  }
}

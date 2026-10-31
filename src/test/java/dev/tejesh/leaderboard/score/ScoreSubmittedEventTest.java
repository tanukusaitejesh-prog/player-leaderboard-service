package dev.tejesh.leaderboard.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreSubmittedEventTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void mapsEveryFieldFromTheScore() {
    Score score =
        new Score(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "tetris", 9, "k", NOW);

    ScoreSubmittedEvent event = ScoreSubmittedEvent.of(score, "neo");

    assertThat(event.eventId()).isEqualTo(score.getId());
    assertThat(event.playerId()).isEqualTo(score.getPlayerId());
    assertThat(event.username()).isEqualTo("neo");
    assertThat(event.gameId()).isEqualTo("tetris");
    assertThat(event.points()).isEqualTo(9);
    assertThat(event.submittedAt()).isEqualTo(NOW);
  }

  @Test
  void validEventPasses() {
    assertThatCode(() -> event(UUID.randomUUID(), "neo", "g", 0).requireValid())
        .doesNotThrowAnyException();
  }

  @Test
  void missingOrNegativeFieldsAreRejected() {
    UUID id = UUID.randomUUID();
    assertInvalid(event(null, "neo", "g", 1));
    assertInvalid(event(id, null, "g", 1));
    assertInvalid(event(id, "neo", null, 1));
    assertInvalid(event(id, "neo", "g", null));
    assertInvalid(event(id, "neo", "g", -1));
  }

  private static ScoreSubmittedEvent event(
      UUID playerId, String username, String gameId, Integer points) {
    return new ScoreSubmittedEvent(UUID.randomUUID(), playerId, username, gameId, points, NOW);
  }

  private static void assertInvalid(ScoreSubmittedEvent event) {
    assertThatThrownBy(event::requireValid).isInstanceOf(IllegalArgumentException.class);
  }
}

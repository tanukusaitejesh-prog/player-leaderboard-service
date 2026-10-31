package dev.tejesh.leaderboard.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import dev.tejesh.leaderboard.web.NotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LeaderboardServiceTest {

  private final FakeLeaderboardStore store = new FakeLeaderboardStore();
  private final LeaderboardService service = new LeaderboardService(store);

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  @Test
  void topIsOrderedByBestScoreAndRespectsLimit() {
    store.recordBest("g", new BoardScore(alice, "alice", 10));
    store.recordBest("g", new BoardScore(bob, "bob", 20));
    store.recordBest("g", new BoardScore(alice, "alice", 5));

    List<LeaderboardEntry> top = service.top("g", 10);

    assertThat(top)
        .containsExactly(
            new LeaderboardEntry(1, bob, "bob", 20), new LeaderboardEntry(2, alice, "alice", 10));
    assertThat(service.top("g", 1)).hasSize(1);
  }

  @Test
  void standingReturnsRankAndScore() {
    store.recordBest("g", new BoardScore(alice, "alice", 10));
    store.recordBest("g", new BoardScore(bob, "bob", 20));

    assertThat(service.standing("g", alice)).isEqualTo(new PlayerStanding(2, 10));
  }

  @Test
  void standingThrowsWhenPlayerIsNotOnBoard() {
    assertThatThrownBy(() -> service.standing("g", alice)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void consumerWritesValidEventAndCountsIt() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LeaderboardEventConsumer consumer = new LeaderboardEventConsumer(store, registry);

    consumer.onScoreSubmitted(
        new dev.tejesh.leaderboard.score.ScoreSubmittedEvent(
            UUID.randomUUID(), alice, "alice", "g", 7, java.time.Instant.EPOCH));

    assertThat(service.standing("g", alice).score()).isEqualTo(7);
    assertThat(registry.counter("leaderboard.consumer.processed").count()).isEqualTo(1.0);
  }

  @Test
  void consumerRejectsEventWithMissingFields() {
    LeaderboardEventConsumer consumer =
        new LeaderboardEventConsumer(store, new SimpleMeterRegistry());

    assertThatThrownBy(
            () ->
                consumer.onScoreSubmitted(
                    new dev.tejesh.leaderboard.score.ScoreSubmittedEvent(
                        UUID.randomUUID(), alice, null, "g", 7, java.time.Instant.EPOCH)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}

package dev.tejesh.leaderboard.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RebuildServiceTest {

  private final BestScoreQuery query = mock(BestScoreQuery.class);
  private final FakeLeaderboardStore store = new FakeLeaderboardStore();
  private final RebuildService service = new RebuildService(query, store);

  private final UUID alice = UUID.randomUUID();
  private final UUID bob = UUID.randomUUID();

  @Test
  void replacesStaleBoardWithDatabaseScores() {
    store.recordBest("g", new BoardScore(UUID.randomUUID(), "stale", 999));
    List<BoardScore> rows = List.of(new BoardScore(alice, "alice", 10));
    when(query.forGame("g")).thenReturn(rows);

    assertThat(service.rebuild("g")).isEqualTo(1);

    assertThat(store.top("g", 10)).hasSize(1);
    assertThat(store.top("g", 10).get(0).playerId()).isEqualTo(alice);
  }

  @Test
  void scoreCommittedDuringRebuildIsNotLost() {
    BoardScore first = new BoardScore(alice, "alice", 10);
    BoardScore arrivedDuringSwap = new BoardScore(bob, "bob", 20);
    when(query.forGame("g"))
        .thenReturn(List.of(first))
        .thenReturn(List.of(first, arrivedDuringSwap));

    assertThat(service.rebuild("g")).isEqualTo(2);

    assertThat(store.top("g", 10).get(0).playerId()).isEqualTo(bob);
  }
}

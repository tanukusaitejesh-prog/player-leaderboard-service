package dev.tejesh.leaderboard.score;

import static org.assertj.core.api.Assertions.assertThat;

import dev.tejesh.leaderboard.player.Player;
import dev.tejesh.leaderboard.player.PlayerRepository;
import dev.tejesh.leaderboard.session.GameSession;
import dev.tejesh.leaderboard.session.GameSessionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs the Flyway schema and the idempotent insert against real PostgreSQL only. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ScoreRepositoryIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.15-alpine");

  @Autowired private ScoreRepository scores;
  @Autowired private PlayerRepository players;
  @Autowired private GameSessionRepository sessions;

  @Test
  void insertIfAbsentWritesOnceAndReadsBackExactly() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    Player player = players.saveAndFlush(new Player(UUID.randomUUID(), "neo_it", now));
    GameSession session =
        sessions.saveAndFlush(new GameSession(UUID.randomUUID(), player.getId(), "tetris", now));
    UUID scoreId = UUID.randomUUID();

    int first =
        scores.insertIfAbsent(
            scoreId, player.getId(), session.getId(), "tetris", 42, "repo-key", now);
    int second =
        scores.insertIfAbsent(
            UUID.randomUUID(), player.getId(), session.getId(), "tetris", 99, "repo-key", now);

    assertThat(first).isEqualTo(1);
    assertThat(second).isZero();
    Score stored = scores.findByIdempotencyKey("repo-key").orElseThrow();
    assertThat(stored.getId()).isEqualTo(scoreId);
    assertThat(stored.getPoints()).isEqualTo(42);
    assertThat(stored.getSubmittedAt()).isEqualTo(now);
  }
}

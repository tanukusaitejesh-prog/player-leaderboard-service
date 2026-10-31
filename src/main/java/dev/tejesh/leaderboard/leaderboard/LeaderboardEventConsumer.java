package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.config.KafkaTopics;
import dev.tejesh.leaderboard.score.ScoreSubmittedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Retries and dead-lettering live in the error handler from KafkaConfig. */
@Component
public class LeaderboardEventConsumer {

  private final LeaderboardStore store;
  private final Counter processed;

  public LeaderboardEventConsumer(LeaderboardStore store, MeterRegistry registry) {
    this.store = store;
    this.processed = registry.counter("leaderboard.consumer.processed");
  }

  @KafkaListener(topics = KafkaTopics.SCORE_EVENTS)
  public void onScoreSubmitted(ScoreSubmittedEvent event) {
    event.requireValid();
    store.recordBest(
        event.gameId(), new BoardScore(event.playerId(), event.username(), event.points()));
    processed.increment();
  }
}

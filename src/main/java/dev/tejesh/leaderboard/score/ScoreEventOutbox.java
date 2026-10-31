package dev.tejesh.leaderboard.score;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tejesh.leaderboard.config.KafkaTopics;
import dev.tejesh.leaderboard.outbox.OutboxEvent;
import dev.tejesh.leaderboard.outbox.OutboxRepository;
import dev.tejesh.leaderboard.outbox.TraceParents;
import dev.tejesh.leaderboard.player.PlayerRepository;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Turns a saved score into an outbox row. Called inside the transaction that saved the score. */
@Component
public class ScoreEventOutbox {

  private final OutboxRepository outbox;
  private final PlayerRepository players;
  private final ObjectMapper json;
  private final Tracer tracer;

  public ScoreEventOutbox(
      OutboxRepository outbox, PlayerRepository players, ObjectMapper json, Tracer tracer) {
    this.outbox = outbox;
    this.players = players;
    this.json = json;
    this.tracer = tracer;
  }

  public void append(Score score) {
    String username = players.findById(score.getPlayerId()).orElseThrow().getUsername();
    try {
      outbox.insert(
          OutboxEvent.builder()
              .id(UUID.randomUUID())
              .topic(KafkaTopics.SCORE_EVENTS)
              .eventKey(score.getGameId())
              .payload(json.writeValueAsString(ScoreSubmittedEvent.of(score, username)))
              .traceParent(TraceParents.current(tracer))
              .build());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize event for score " + score.getId(), e);
    }
  }
}

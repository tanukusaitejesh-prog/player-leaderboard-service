package dev.tejesh.leaderboard.score;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tejesh.leaderboard.config.KafkaTopics;
import dev.tejesh.leaderboard.outbox.OutboxEvent;
import dev.tejesh.leaderboard.outbox.OutboxRepository;
import dev.tejesh.leaderboard.player.PlayerRepository;
import dev.tejesh.leaderboard.session.GameSession;
import dev.tejesh.leaderboard.session.GameSessionRepository;
import dev.tejesh.leaderboard.web.ConflictException;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {

  private final ScoreRepository scores;
  private final GameSessionRepository sessions;
  private final PlayerRepository players;
  private final OutboxRepository outbox;
  private final ObjectMapper json;
  private final Clock clock;

  public ScoreService(
      ScoreRepository scores,
      GameSessionRepository sessions,
      PlayerRepository players,
      OutboxRepository outbox,
      ObjectMapper json,
      Clock clock) {
    this.scores = scores;
    this.sessions = sessions;
    this.players = players;
    this.outbox = outbox;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public ScoreSubmission submit(String idempotencyKey, UUID playerId, UUID sessionId, int points) {
    // Checked before the session rules so a retry still gets the original score after the
    // session has ended.
    Optional<Score> existing = scores.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
      return new ScoreSubmission(existing.get(), false);
    }

    GameSession session = requireWritableSession(sessionId, playerId);
    Score score =
        new Score(
            UUID.randomUUID(),
            playerId,
            sessionId,
            session.getGameId(),
            points,
            idempotencyKey,
            clock.instant());

    int inserted =
        scores.insertIfAbsent(
            score.getId(),
            score.getPlayerId(),
            score.getSessionId(),
            score.getGameId(),
            score.getPoints(),
            score.getIdempotencyKey(),
            score.getSubmittedAt());
    if (inserted == 0) {
      // Lost a race with a concurrent request using the same key; that request's row is committed.
      return new ScoreSubmission(scores.findByIdempotencyKey(idempotencyKey).orElseThrow(), false);
    }
    // Same transaction as the score row: both commit or neither does.
    outbox.insert(toOutboxEvent(score));
    return new ScoreSubmission(score, true);
  }

  private OutboxEvent toOutboxEvent(Score score) {
    String username = players.findById(score.getPlayerId()).orElseThrow().getUsername();
    try {
      return OutboxEvent.builder()
          .id(UUID.randomUUID())
          .topic(KafkaTopics.SCORE_EVENTS)
          .eventKey(score.getGameId())
          .payload(json.writeValueAsString(ScoreSubmittedEvent.of(score, username)))
          .build();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Could not serialize event for score " + score.getId(), e);
    }
  }

  private GameSession requireWritableSession(UUID sessionId, UUID playerId) {
    GameSession session =
        sessions
            .findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session " + sessionId + " not found"));
    if (!session.getPlayerId().equals(playerId)) {
      throw new ConflictException(
          "Session " + sessionId + " does not belong to player " + playerId);
    }
    if (!session.isActive()) {
      throw new ConflictException("Session " + sessionId + " is not active");
    }
    return session;
  }
}

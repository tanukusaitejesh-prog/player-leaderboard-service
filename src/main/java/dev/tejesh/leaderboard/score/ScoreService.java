package dev.tejesh.leaderboard.score;

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
  private final Clock clock;

  public ScoreService(ScoreRepository scores, GameSessionRepository sessions, Clock clock) {
    this.scores = scores;
    this.sessions = sessions;
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
    return new ScoreSubmission(score, true);
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

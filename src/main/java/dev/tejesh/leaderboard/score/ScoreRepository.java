package dev.tejesh.leaderboard.score;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreRepository extends JpaRepository<Score, UUID> {

  Optional<Score> findByIdempotencyKey(String idempotencyKey);

  /**
   * Returns 1 if the row was inserted and 0 if the key already existed. A concurrent insert with
   * the same key blocks until the other transaction commits, then returns 0.
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO scores
            (id, player_id, session_id, game_id, points, idempotency_key, submitted_at)
          VALUES
            (:id, :playerId, :sessionId, :gameId, :points, :idempotencyKey, :submittedAt)
          ON CONFLICT (idempotency_key) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("playerId") UUID playerId,
      @Param("sessionId") UUID sessionId,
      @Param("gameId") String gameId,
      @Param("points") int points,
      @Param("idempotencyKey") String idempotencyKey,
      @Param("submittedAt") Instant submittedAt);
}

package dev.tejesh.leaderboard.leaderboard;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BestScoreQuery {

  private final JdbcTemplate jdbc;

  public BestScoreQuery(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<BoardScore> forGame(String gameId) {
    return jdbc.query(
        """
        SELECT s.player_id, p.username, MAX(s.points) AS best
        FROM scores s
        JOIN players p ON p.id = s.player_id
        WHERE s.game_id = ?
        GROUP BY s.player_id, p.username
        """,
        (rs, i) ->
            new BoardScore(
                rs.getObject("player_id", UUID.class), rs.getString("username"), rs.getInt("best")),
        gameId);
  }
}

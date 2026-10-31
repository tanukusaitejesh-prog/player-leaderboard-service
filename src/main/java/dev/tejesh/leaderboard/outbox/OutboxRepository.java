package dev.tejesh.leaderboard.outbox;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plain JDBC because SKIP LOCKED, JSONB casts and array binds don't suit JPA. */
@Repository
public class OutboxRepository {

  private final JdbcTemplate jdbc;

  public OutboxRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(OutboxEvent event) {
    jdbc.update(
        "INSERT INTO outbox_events (id, topic, event_key, payload) VALUES (?, ?, ?, ?::jsonb)",
        event.id(),
        event.topic(),
        event.eventKey(),
        event.payload());
  }

  /** Must run inside a transaction; the row locks are what keep other publishers away. */
  public List<OutboxEvent> lockUnpublished(int limit) {
    return jdbc.query(
        """
        SELECT id, topic, event_key, payload::text AS payload
        FROM outbox_events
        WHERE published_at IS NULL
        ORDER BY created_at
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """,
        (rs, i) ->
            OutboxEvent.builder()
                .id(rs.getObject("id", UUID.class))
                .topic(rs.getString("topic"))
                .eventKey(rs.getString("event_key"))
                .payload(rs.getString("payload"))
                .build(),
        limit);
  }

  public void markPublished(Collection<UUID> ids) {
    jdbc.update(
        connection -> {
          PreparedStatement statement =
              connection.prepareStatement(
                  "UPDATE outbox_events SET published_at = now() WHERE id = ANY (?)");
          statement.setArray(1, connection.createArrayOf("uuid", ids.toArray()));
          return statement;
        });
  }

  public long countPending() {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE published_at IS NULL", Long.class);
    return count == null ? 0 : count;
  }
}

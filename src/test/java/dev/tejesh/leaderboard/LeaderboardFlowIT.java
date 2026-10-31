package dev.tejesh.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tejesh.leaderboard.config.KafkaTopics;
import dev.tejesh.leaderboard.score.ScoreSubmittedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

/** Exercises the real path: HTTP, PostgreSQL, outbox, Kafka, consumer, Redis. */
class LeaderboardFlowIT extends AbstractIntegrationIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;
  @Autowired private KafkaTemplate<String, String> kafka;
  @Autowired private ObjectMapper json;

  private final String gameId = "game-" + UUID.randomUUID();

  private record Player(UUID id, String username, UUID sessionId) {}

  private Player newPlayer() {
    String username = "p_" + UUID.randomUUID().toString().substring(0, 8);
    JsonNode player =
        rest.postForObject("/api/v1/players", Map.of("username", username), JsonNode.class);
    UUID playerId = UUID.fromString(player.get("id").asText());
    JsonNode session =
        rest.postForObject(
            "/api/v1/sessions", Map.of("playerId", playerId, "gameId", gameId), JsonNode.class);
    return new Player(playerId, username, UUID.fromString(session.get("id").asText()));
  }

  private ResponseEntity<JsonNode> submit(Player player, int points, String key) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", key);
    Map<String, Object> body =
        Map.of("playerId", player.id(), "sessionId", player.sessionId(), "points", points);
    return rest.postForEntity("/api/v1/scores", new HttpEntity<>(body, headers), JsonNode.class);
  }

  private ResponseEntity<JsonNode> submit(Player player, int points) {
    return submit(player, points, UUID.randomUUID().toString());
  }

  private JsonNode top() {
    return rest.getForObject("/api/v1/leaderboards/" + gameId + "/top", JsonNode.class);
  }

  private void awaitScoreOnBoard(Player player, int score) {
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              ResponseEntity<JsonNode> standing = standing(player);
              assertThat(standing.getStatusCode()).isEqualTo(HttpStatus.OK);
              assertThat(standing.getBody().get("score").asInt()).isEqualTo(score);
            });
  }

  private ResponseEntity<JsonNode> standing(Player player) {
    return rest.getForEntity(
        "/api/v1/leaderboards/" + gameId + "/players/" + player.id(), JsonNode.class);
  }

  private int scoreRows(String key) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM scores WHERE idempotency_key = ?", Integer.class, key);
  }

  @Test
  void submittedScoreReachesTheBoardThroughOutboxAndKafka() {
    Player player = newPlayer();

    ResponseEntity<JsonNode> response = submit(player, 100);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().get("gameId").asText()).isEqualTo(gameId);
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              JsonNode top = top();
              assertThat(top).hasSize(1);
              assertThat(top.get(0).get("rank").asInt()).isEqualTo(1);
              assertThat(top.get(0).get("playerId").asText()).isEqualTo(player.id().toString());
              assertThat(top.get(0).get("username").asText()).isEqualTo(player.username());
              assertThat(top.get(0).get("score").asInt()).isEqualTo(100);
            });
  }

  @Test
  void sameIdempotencyKeyWritesOneRowAndOneEvent() {
    Player player = newPlayer();
    String key = UUID.randomUUID().toString();

    ResponseEntity<JsonNode> first = submit(player, 100, key);
    ResponseEntity<JsonNode> second = submit(player, 555, key);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(second.getBody().get("id")).isEqualTo(first.getBody().get("id"));
    assertThat(second.getBody().get("points").asInt()).isEqualTo(100);
    assertThat(second.getBody().get("submittedAt")).isEqualTo(first.getBody().get("submittedAt"));
    assertThat(scoreRows(key)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE payload->>'eventId' = ?",
                Integer.class,
                first.getBody().get("id").asText()))
        .isEqualTo(1);
    awaitScoreOnBoard(player, 100);
  }

  @Test
  void lowerScoreAfterHigherLeavesRankAndScoreUnchanged() {
    Player player = newPlayer();
    Player witness = newPlayer();
    submit(player, 100);
    awaitScoreOnBoard(player, 100);

    submit(player, 50);
    // Same game means same partition, so once the witness shows up the 50 has been processed.
    submit(witness, 10);
    awaitScoreOnBoard(witness, 10);

    JsonNode standing = standing(player).getBody();
    assertThat(standing.get("score").asInt()).isEqualTo(100);
    assertThat(standing.get("rank").asInt()).isEqualTo(1);
  }

  @Test
  void redeliveredEventChangesNothing() throws Exception {
    Player player = newPlayer();
    Player witness = newPlayer();
    String event =
        json.writeValueAsString(
            new ScoreSubmittedEvent(
                UUID.randomUUID(), player.id(), player.username(), gameId, 70, Instant.now()));

    kafka.send(KafkaTopics.SCORE_EVENTS, gameId, event);
    kafka.send(KafkaTopics.SCORE_EVENTS, gameId, event);
    kafka.send(
        KafkaTopics.SCORE_EVENTS,
        gameId,
        json.writeValueAsString(
            new ScoreSubmittedEvent(
                UUID.randomUUID(), witness.id(), witness.username(), gameId, 10, Instant.now())));
    awaitScoreOnBoard(witness, 10);

    JsonNode top = top();
    assertThat(top).hasSize(2);
    assertThat(top.get(0).get("playerId").asText()).isEqualTo(player.id().toString());
    assertThat(top.get(0).get("score").asInt()).isEqualTo(70);
  }

  @Test
  void rebuildAfterWipingRedisMatchesTheDatabase() {
    Player alice = newPlayer();
    Player bob = newPlayer();
    submit(alice, 10);
    submit(alice, 30);
    submit(bob, 20);
    awaitScoreOnBoard(alice, 30);
    awaitScoreOnBoard(bob, 20);

    redis.delete("leaderboard:" + gameId);
    redis.opsForHash().delete("leaderboard:names", alice.id().toString(), bob.id().toString());
    assertThat(top()).isEmpty();

    JsonNode rebuilt =
        rest.postForObject(
            "/api/v1/admin/leaderboards/" + gameId + "/rebuild", null, JsonNode.class);

    assertThat(rebuilt.get("rebuilt").asInt()).isEqualTo(2);
    JsonNode top = top();
    assertThat(top).hasSize(2);
    assertThat(top.get(0).get("playerId").asText()).isEqualTo(alice.id().toString());
    assertThat(top.get(0).get("username").asText()).isEqualTo(alice.username());
    assertThat(top.get(0).get("score").asInt()).isEqualTo(30);
    assertThat(top.get(1).get("playerId").asText()).isEqualTo(bob.id().toString());
    assertThat(top.get(1).get("score").asInt()).isEqualTo(20);
  }

  @Test
  void playerNotOnTheBoardGets404() {
    assertThat(standing(newPlayer()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  /** Regression test: each rank lookup used to open a new Redis connection and exhaust ports. */
  @Test
  void rankLookupsReuseTheRedisConnection() {
    Player player = newPlayer();
    submit(player, 1);
    awaitScoreOnBoard(player, 1);

    long before = redisConnectionsReceived();
    for (int i = 0; i < 100; i++) {
      standing(player);
    }

    assertThat(redisConnectionsReceived() - before).isLessThan(5);
  }

  private long redisConnectionsReceived() {
    Properties stats =
        redis.execute((RedisCallback<Properties>) c -> c.serverCommands().info("stats"));
    return Long.parseLong(stats.getProperty("total_connections_received"));
  }

  @Test
  void rebuildOfGameWithNoScoresReturnsZero() {
    JsonNode rebuilt =
        rest.postForObject(
            "/api/v1/admin/leaderboards/" + gameId + "/rebuild", null, JsonNode.class);

    assertThat(rebuilt.get("rebuilt").asInt()).isZero();
  }

  @Test
  void poisonMessageLandsInDltAndConsumerKeepsWorking() {
    String poison = "not json {" + UUID.randomUUID();
    kafka.send(KafkaTopics.SCORE_EVENTS, gameId, poison);

    try (KafkaConsumer<String, byte[]> dlt = dltConsumer()) {
      dlt.subscribe(List.of(KafkaTopics.SCORE_EVENTS_DLT));
      List<String> received = new ArrayList<>();
      await()
          .atMost(TIMEOUT)
          .untilAsserted(
              () -> {
                for (ConsumerRecord<String, byte[]> record : dlt.poll(Duration.ofMillis(500))) {
                  received.add(new String(record.value()));
                }
                assertThat(received).contains(poison);
              });
    }

    Player player = newPlayer();
    submit(player, 5);
    awaitScoreOnBoard(player, 5);
  }

  private static KafkaConsumer<String, byte[]> dltConsumer() {
    return new KafkaConsumer<>(
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            KAFKA.getBootstrapServers(),
            ConsumerConfig.GROUP_ID_CONFIG,
            "dlt-test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            ByteArrayDeserializer.class));
  }

  @Test
  void concurrentSubmitsWithSameKeyCreateExactlyOneRow() throws Exception {
    Player player = newPlayer();
    String key = UUID.randomUUID().toString();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<ResponseEntity<JsonNode>>> calls = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        calls.add(
            pool.submit(
                () -> {
                  start.await();
                  return submit(player, 100, key);
                }));
      }
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      for (Future<ResponseEntity<JsonNode>> call : calls) {
        statuses.add(call.get().getStatusCode().value());
      }

      assertThat(statuses).containsExactlyInAnyOrder(201, 200);
      assertThat(scoreRows(key)).isEqualTo(1);
    } finally {
      pool.shutdownNow();
    }
  }
}

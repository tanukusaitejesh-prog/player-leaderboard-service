package dev.tejesh.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Boot switches metrics export and tracing off in tests unless asked, so this context asks. */
@AutoConfigureObservability
@TestPropertySource(properties = "management.otlp.tracing.export.enabled=false")
class ObservabilityIT extends AbstractIntegrationIT {

  @Autowired private TestRestTemplate rest;
  @Autowired private JdbcTemplate jdbc;

  private UUID submitScore() {
    String username = "o_" + UUID.randomUUID().toString().substring(0, 8);
    JsonNode player =
        rest.postForObject("/api/v1/players", Map.of("username", username), JsonNode.class);
    String playerId = player.get("id").asText();
    JsonNode session =
        rest.postForObject(
            "/api/v1/sessions", Map.of("playerId", playerId, "gameId", "obs"), JsonNode.class);
    HttpHeaders headers = new HttpHeaders();
    headers.set("Idempotency-Key", UUID.randomUUID().toString());
    Map<String, Object> body =
        Map.of("playerId", playerId, "sessionId", session.get("id").asText(), "points", 1);
    JsonNode score =
        rest.postForObject("/api/v1/scores", new HttpEntity<>(body, headers), JsonNode.class);
    return UUID.fromString(score.get("id").asText());
  }

  @Test
  void prometheusEndpointExposesCustomMetrics() {
    submitScore();
    rest.getForObject("/api/v1/leaderboards/obs/top", JsonNode.class);

    String metrics = rest.getForObject("/actuator/prometheus", String.class);

    assertThat(metrics)
        .contains(
            "leaderboard_scores_submitted_total",
            "leaderboard_read_duration_seconds_bucket",
            "outbox_pending",
            "outbox_publish_failures_total",
            "leaderboard_consumer_processed_total",
            "leaderboard_consumer_deadlettered_total",
            "http_server_requests_seconds_bucket",
            "hikaricp_connections",
            "jvm_memory_used_bytes");
  }

  @Test
  void outboxRowCarriesTheRequestTrace() {
    UUID scoreId = submitScore();

    String traceParent =
        jdbc.queryForObject(
            "SELECT trace_parent FROM outbox_events WHERE payload->>'eventId' = ?",
            String.class,
            scoreId.toString());

    assertThat(traceParent).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
  }

  @Test
  void scoreReachesTheBoardWithTracingEnabled() {
    submitScore();

    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(rest.getForObject("/api/v1/leaderboards/obs/top", JsonNode.class))
                    .isNotEmpty());
  }

  @Test
  void healthProbesAreUp() {
    JsonNode readiness = rest.getForObject("/actuator/health/readiness", JsonNode.class);
    JsonNode liveness = rest.getForObject("/actuator/health/liveness", JsonNode.class);

    assertThat(readiness.get("status").asText()).isEqualTo("UP");
    assertThat(liveness.get("status").asText()).isEqualTo("UP");
  }
}

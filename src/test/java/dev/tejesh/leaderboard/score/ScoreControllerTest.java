package dev.tejesh.leaderboard.score;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tejesh.leaderboard.web.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScoreController.class)
class ScoreControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ScoreService scores;

  private final UUID playerId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private String body(int points) {
    return "{\"playerId\":\""
        + playerId
        + "\",\"sessionId\":\""
        + sessionId
        + "\",\"points\":"
        + points
        + "}";
  }

  private Score score() {
    return new Score(UUID.randomUUID(), playerId, sessionId, "tetris", 10, "k", Instant.now());
  }

  @Test
  void firstSubmitReturns201() throws Exception {
    when(scores.submit("k", playerId, sessionId, 10))
        .thenReturn(new ScoreSubmission(score(), true));

    mvc.perform(
            post("/api/v1/scores")
                .header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(10)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gameId").value("tetris"))
        .andExpect(jsonPath("$.points").value(10));
  }

  @Test
  void replayReturns200() throws Exception {
    when(scores.submit("k", playerId, sessionId, 10))
        .thenReturn(new ScoreSubmission(score(), false));

    mvc.perform(
            post("/api/v1/scores")
                .header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(10)))
        .andExpect(status().isOk());
  }

  @Test
  void missingIdempotencyKeyReturns400() throws Exception {
    mvc.perform(post("/api/v1/scores").contentType(MediaType.APPLICATION_JSON).content(body(10)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void tooLongIdempotencyKeyReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/scores")
                .header("Idempotency-Key", "x".repeat(65))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(10)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void negativePointsReturns400() throws Exception {
    mvc.perform(
            post("/api/v1/scores")
                .header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(-1)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void inactiveSessionReturns409() throws Exception {
    when(scores.submit(eq("k"), any(), any(), anyInt()))
        .thenThrow(new ConflictException("Session is not active"));

    mvc.perform(
            post("/api/v1/scores")
                .header("Idempotency-Key", "k")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(10)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Session is not active"));
  }
}

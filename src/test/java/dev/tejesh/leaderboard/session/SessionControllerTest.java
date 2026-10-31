package dev.tejesh.leaderboard.session;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private SessionService sessions;

  private static String body(UUID playerId, String gameId) {
    return "{\"playerId\":\"" + playerId + "\",\"gameId\":\"" + gameId + "\"}";
  }

  @Test
  void startReturns201Active() throws Exception {
    UUID playerId = UUID.randomUUID();
    GameSession session = new GameSession(UUID.randomUUID(), playerId, "tetris", Instant.now());
    when(sessions.start(playerId, "tetris")).thenReturn(session);

    mvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(playerId, "tetris")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.gameId").value("tetris"));
  }

  @Test
  void unknownPlayerReturns404() throws Exception {
    UUID playerId = UUID.randomUUID();
    when(sessions.start(eq(playerId), any())).thenThrow(new NotFoundException("no player"));

    mvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(playerId, "tetris")))
        .andExpect(status().isNotFound());
  }

  @Test
  void reservedGameIdIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "names")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void gameIdWithColonIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID(), "a:b")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void endReturns200Ended() throws Exception {
    GameSession session =
        new GameSession(UUID.randomUUID(), UUID.randomUUID(), "tetris", Instant.now());
    session.end(Instant.now());
    when(sessions.end(session.getId())).thenReturn(session);

    mvc.perform(post("/api/v1/sessions/{id}/end", session.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ENDED"));
  }
}

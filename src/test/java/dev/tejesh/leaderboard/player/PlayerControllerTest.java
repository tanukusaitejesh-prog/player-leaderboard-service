package dev.tejesh.leaderboard.player;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tejesh.leaderboard.web.ConflictException;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlayerController.class)
class PlayerControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private PlayerService players;

  @Test
  void createReturns201WithPlayer() throws Exception {
    Player player = new Player(UUID.randomUUID(), "neo_1", Instant.now());
    when(players.create("neo_1")).thenReturn(player);

    mvc.perform(
            post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"neo_1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(player.getId().toString()))
        .andExpect(jsonPath("$.username").value("neo_1"));
  }

  @Test
  void invalidUsernameReturnsProblemDetail() throws Exception {
    mvc.perform(
            post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"a!\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void takenUsernameReturns409ProblemDetail() throws Exception {
    when(players.create("taken")).thenThrow(new ConflictException("Username 'taken' is taken"));

    mvc.perform(
            post("/api/v1/players")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"taken\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Username 'taken' is taken"));
  }

  @Test
  void unknownPlayerReturns404ProblemDetail() throws Exception {
    UUID id = UUID.randomUUID();
    when(players.get(id)).thenThrow(new NotFoundException("Player not found"));

    mvc.perform(get("/api/v1/players/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void malformedIdReturns400() throws Exception {
    mvc.perform(get("/api/v1/players/not-a-uuid")).andExpect(status().isBadRequest());
  }
}

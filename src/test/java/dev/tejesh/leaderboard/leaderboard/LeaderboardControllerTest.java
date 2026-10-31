package dev.tejesh.leaderboard.leaderboard;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeaderboardController.class)
class LeaderboardControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private LeaderboardService leaderboards;
  @MockitoBean private RebuildService rebuilds;

  @Test
  void topDefaultsToLimitTen() throws Exception {
    UUID player = UUID.randomUUID();
    when(leaderboards.top("tetris", 10))
        .thenReturn(List.of(new LeaderboardEntry(1, player, "neo", 42)));

    mvc.perform(get("/api/v1/leaderboards/tetris/top"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].rank").value(1))
        .andExpect(jsonPath("$[0].username").value("neo"))
        .andExpect(jsonPath("$[0].score").value(42));
  }

  @Test
  void limitBoundsAreEnforced() throws Exception {
    mvc.perform(get("/api/v1/leaderboards/tetris/top").param("limit", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/leaderboards/tetris/top").param("limit", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/leaderboards/tetris/top").param("limit", "100"))
        .andExpect(status().isOk());
  }

  @Test
  void reservedGameIdIsRejectedOnReads() throws Exception {
    mvc.perform(get("/api/v1/leaderboards/names/top")).andExpect(status().isBadRequest());
  }

  @Test
  void standingReturnsRankAndScore() throws Exception {
    UUID player = UUID.randomUUID();
    when(leaderboards.standing("tetris", player)).thenReturn(new PlayerStanding(3, 42));

    mvc.perform(get("/api/v1/leaderboards/tetris/players/{id}", player))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rank").value(3))
        .andExpect(jsonPath("$.score").value(42));
  }

  @Test
  void standingReturns404WhenNotOnBoard() throws Exception {
    UUID player = UUID.randomUUID();
    when(leaderboards.standing("tetris", player)).thenThrow(new NotFoundException("nope"));

    mvc.perform(get("/api/v1/leaderboards/tetris/players/{id}", player))
        .andExpect(status().isNotFound());
  }

  @Test
  void rebuildReturnsCount() throws Exception {
    when(rebuilds.rebuild("tetris")).thenReturn(5);

    mvc.perform(post("/api/v1/admin/leaderboards/tetris/rebuild"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rebuilt").value(5));
  }
}

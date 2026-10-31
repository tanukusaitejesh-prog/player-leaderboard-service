package dev.tejesh.leaderboard.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.tejesh.leaderboard.outbox.OutboxEvent;
import dev.tejesh.leaderboard.outbox.OutboxRepository;
import dev.tejesh.leaderboard.player.Player;
import dev.tejesh.leaderboard.player.PlayerRepository;
import dev.tejesh.leaderboard.session.GameSession;
import dev.tejesh.leaderboard.session.GameSessionRepository;
import dev.tejesh.leaderboard.web.ConflictException;
import dev.tejesh.leaderboard.web.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ScoreServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final String KEY = "key-1";

  private final ScoreRepository scores = mock(ScoreRepository.class);
  private final GameSessionRepository sessions = mock(GameSessionRepository.class);
  private final PlayerRepository players = mock(PlayerRepository.class);
  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final ObjectMapper json =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();
  private final ScoreService service =
      new ScoreService(scores, sessions, players, outbox, json, Clock.fixed(NOW, ZoneOffset.UTC));

  private final UUID playerId = UUID.randomUUID();
  private final GameSession session = new GameSession(UUID.randomUUID(), playerId, "tetris", NOW);

  @Test
  void firstSubmitInsertsAndTakesGameIdFromSession() {
    when(scores.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(scores.insertIfAbsent(any(), any(), any(), anyString(), anyInt(), anyString(), any()))
        .thenReturn(1);
    when(players.findById(playerId)).thenReturn(Optional.of(new Player(playerId, "neo", NOW)));

    ScoreSubmission result = service.submit(KEY, playerId, session.getId(), 100);

    assertThat(result.created()).isTrue();
    assertThat(result.score().getGameId()).isEqualTo("tetris");
    assertThat(result.score().getPoints()).isEqualTo(100);
    assertThat(result.score().getSubmittedAt()).isEqualTo(NOW);

    ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outbox).insert(event.capture());
    assertThat(event.getValue().topic()).isEqualTo("score-events");
    assertThat(event.getValue().eventKey()).isEqualTo("tetris");
    assertThat(event.getValue().payload())
        .contains(result.score().getId().toString(), "\"username\":\"neo\"", "\"points\":100");
  }

  @Test
  void replayReturnsOriginalWithoutWritingEvenAfterSessionEnded() {
    Score original =
        new Score(UUID.randomUUID(), playerId, session.getId(), "tetris", 50, KEY, NOW);
    session.end(NOW);
    when(scores.findByIdempotencyKey(KEY)).thenReturn(Optional.of(original));

    ScoreSubmission result = service.submit(KEY, playerId, session.getId(), 999);

    assertThat(result.created()).isFalse();
    assertThat(result.score()).isSameAs(original);
    verify(scores, never()).insertIfAbsent(any(), any(), any(), any(), anyInt(), any(), any());
    verify(outbox, never()).insert(any());
  }

  @Test
  void losingTheInsertRaceReturnsTheWinningRow() {
    Score winner = new Score(UUID.randomUUID(), playerId, session.getId(), "tetris", 50, KEY, NOW);
    when(scores.findByIdempotencyKey(KEY))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
    when(scores.insertIfAbsent(any(), any(), any(), anyString(), anyInt(), anyString(), any()))
        .thenReturn(0);

    ScoreSubmission result = service.submit(KEY, playerId, session.getId(), 100);

    assertThat(result.created()).isFalse();
    assertThat(result.score()).isSameAs(winner);
  }

  @Test
  void rejectsSessionOwnedByAnotherPlayer() {
    when(scores.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> service.submit(KEY, UUID.randomUUID(), session.getId(), 100))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("does not belong");
  }

  @Test
  void rejectsEndedSession() {
    session.end(NOW);
    when(scores.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(sessions.findById(session.getId())).thenReturn(Optional.of(session));

    assertThatThrownBy(() -> service.submit(KEY, playerId, session.getId(), 100))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void rejectsUnknownSession() {
    UUID unknown = UUID.randomUUID();
    when(scores.findByIdempotencyKey(KEY)).thenReturn(Optional.empty());
    when(sessions.findById(unknown)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.submit(KEY, playerId, unknown, 100))
        .isInstanceOf(NotFoundException.class);
  }
}

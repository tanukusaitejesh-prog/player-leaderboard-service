package dev.tejesh.leaderboard.score;

import dev.tejesh.leaderboard.score.dto.ScoreResponse;
import dev.tejesh.leaderboard.score.dto.SubmitScoreRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/scores")
public class ScoreController {

  private final ScoreService scores;

  public ScoreController(ScoreService scores) {
    this.scores = scores;
  }

  @PostMapping
  public ResponseEntity<ScoreResponse> submit(
      @RequestHeader("Idempotency-Key") @Size(min = 1, max = 64) String idempotencyKey,
      @Valid @RequestBody SubmitScoreRequest request) {
    ScoreSubmission submission =
        scores.submit(idempotencyKey, request.playerId(), request.sessionId(), request.points());
    HttpStatus status = submission.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ScoreResponse.from(submission.score()));
  }
}

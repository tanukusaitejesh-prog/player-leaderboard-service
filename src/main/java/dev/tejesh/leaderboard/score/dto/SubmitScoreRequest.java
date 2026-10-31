package dev.tejesh.leaderboard.score.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitScoreRequest(
    @NotNull UUID playerId, @NotNull UUID sessionId, @NotNull @Min(0) Integer points) {}

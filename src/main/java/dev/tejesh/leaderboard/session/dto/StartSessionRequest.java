package dev.tejesh.leaderboard.session.dto;

import dev.tejesh.leaderboard.web.GameIds;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record StartSessionRequest(
    @NotNull UUID playerId, @NotNull @Pattern(regexp = GameIds.PATTERN) String gameId) {}

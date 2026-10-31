package dev.tejesh.leaderboard.player.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreatePlayerRequest(
    @NotNull @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$") String username) {}

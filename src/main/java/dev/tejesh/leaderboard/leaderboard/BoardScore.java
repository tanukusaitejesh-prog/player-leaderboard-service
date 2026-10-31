package dev.tejesh.leaderboard.leaderboard;

import java.util.UUID;

/** A player's best score in one game, as written to a board. */
public record BoardScore(UUID playerId, String username, int score) {}

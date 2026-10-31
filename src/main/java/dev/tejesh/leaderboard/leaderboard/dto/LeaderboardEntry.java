package dev.tejesh.leaderboard.leaderboard.dto;

import java.util.UUID;

public record LeaderboardEntry(long rank, UUID playerId, String username, long score) {}

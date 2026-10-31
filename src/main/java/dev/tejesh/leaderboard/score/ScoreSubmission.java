package dev.tejesh.leaderboard.score;

/** The stored score, and whether this call created it (false means an idempotent replay). */
public record ScoreSubmission(Score score, boolean created) {}

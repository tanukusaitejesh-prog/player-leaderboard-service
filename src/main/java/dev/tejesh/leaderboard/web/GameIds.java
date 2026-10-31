package dev.tejesh.leaderboard.web;

/**
 * Validation rule for game ids. Game ids become part of a Redis key, so colons are not allowed and
 * "names" is reserved: {@code leaderboard:names} is the username hash, not a board.
 */
public final class GameIds {

  public static final String PATTERN = "^(?!names$)[A-Za-z0-9_-]{1,64}$";

  private GameIds() {}
}

package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLeaderboardStore implements LeaderboardStore {

  static final String NAMES_KEY = "leaderboard:names";

  /** GT keeps the higher score, which is what makes redelivery and reordering harmless. */
  private static final RedisScript<Long> RECORD_BEST =
      RedisScript.of(
          """
          redis.call('ZADD', KEYS[1], 'GT', ARGV[1], ARGV[2])
          redis.call('HSET', KEYS[2], ARGV[2], ARGV[3])
          return 1
          """,
          Long.class);

  /**
   * A script rather than a pipeline. Spring pipelines need a dedicated connection, and without a
   * pool that means a new TCP connection per call, which ran out of ports under load.
   */
  @SuppressWarnings("rawtypes")
  private static final RedisScript<List> STANDING =
      RedisScript.of(
          """
          local rank = redis.call('ZREVRANK', KEYS[1], ARGV[1])
          if not rank then
            return {}
          end
          return {rank, redis.call('ZSCORE', KEYS[1], ARGV[1])}
          """,
          List.class);

  private final StringRedisTemplate redis;

  public RedisLeaderboardStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  static String boardKey(String gameId) {
    return "leaderboard:" + gameId;
  }

  @Override
  public void recordBest(String gameId, BoardScore score) {
    redis.execute(
        RECORD_BEST,
        List.of(boardKey(gameId), NAMES_KEY),
        String.valueOf(score.score()),
        score.playerId().toString(),
        score.username());
  }

  @Override
  public List<LeaderboardEntry> top(String gameId, int limit) {
    Set<TypedTuple<String>> rows =
        redis.opsForZSet().reverseRangeWithScores(boardKey(gameId), 0, limit - 1L);
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }

    List<String> playerIds = rows.stream().map(TypedTuple::getValue).toList();
    List<String> usernames = redis.<String, String>opsForHash().multiGet(NAMES_KEY, playerIds);

    List<LeaderboardEntry> entries = new ArrayList<>(rows.size());
    long rank = 1;
    for (TypedTuple<String> row : rows) {
      int i = (int) rank - 1;
      entries.add(
          new LeaderboardEntry(
              rank++,
              UUID.fromString(row.getValue()),
              usernames.get(i),
              row.getScore().longValue()));
    }
    return entries;
  }

  @Override
  public Optional<PlayerStanding> standing(String gameId, UUID playerId) {
    List<?> reply = redis.execute(STANDING, List.of(boardKey(gameId)), playerId.toString());
    if (reply == null || reply.isEmpty()) {
      return Optional.empty();
    }
    long zeroBasedRank = Long.parseLong(reply.get(0).toString());
    long score = (long) Double.parseDouble(reply.get(1).toString());
    return Optional.of(new PlayerStanding(zeroBasedRank + 1, score));
  }

  @Override
  public void replaceBoard(String gameId, List<BoardScore> scores) {
    String board = boardKey(gameId);
    if (scores.isEmpty()) {
      redis.delete(board);
      return;
    }

    Set<TypedTuple<String>> rows = new HashSet<>();
    Map<String, String> usernames = new LinkedHashMap<>();
    for (BoardScore score : scores) {
      String member = score.playerId().toString();
      rows.add(TypedTuple.of(member, (double) score.score()));
      usernames.put(member, score.username());
    }

    String temp = board + ":rebuild:" + UUID.randomUUID();
    redis.opsForZSet().add(temp, rows);
    redis.opsForHash().putAll(NAMES_KEY, usernames);
    redis.rename(temp, board);
  }
}

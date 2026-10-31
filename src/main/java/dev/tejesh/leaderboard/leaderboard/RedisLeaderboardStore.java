package dev.tejesh.leaderboard.leaderboard;

import dev.tejesh.leaderboard.leaderboard.dto.LeaderboardEntry;
import dev.tejesh.leaderboard.leaderboard.dto.PlayerStanding;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
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
    byte[] key = boardKey(gameId).getBytes(StandardCharsets.UTF_8);
    byte[] member = playerId.toString().getBytes(StandardCharsets.UTF_8);
    List<Object> replies =
        redis.executePipelined(
            (RedisCallback<Object>)
                connection -> {
                  pipelineRankAndScore(connection, key, member);
                  return null;
                });

    if (replies.size() < 2 || replies.get(0) == null || replies.get(1) == null) {
      return Optional.empty();
    }
    long zeroBasedRank = ((Number) replies.get(0)).longValue();
    long score = ((Number) replies.get(1)).longValue();
    return Optional.of(new PlayerStanding(zeroBasedRank + 1, score));
  }

  private static void pipelineRankAndScore(RedisConnection connection, byte[] key, byte[] member) {
    connection.zSetCommands().zRevRank(key, member);
    connection.zSetCommands().zScore(key, member);
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

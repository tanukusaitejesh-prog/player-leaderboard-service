# Design notes

## The shape of it

PostgreSQL is the source of truth. A score write puts the score row and an outbox row in one transaction. A scheduled publisher sends outbox rows to Kafka, a consumer applies them to a Redis sorted set, and reads come only from Redis.

Patterns I used on purpose: ports and adapters (`LeaderboardStore`, with `RedisLeaderboardStore` in production and `FakeLeaderboardStore` in unit tests, which is also the Strategy pattern), Repository, Builder (`OutboxEvent`), and Transactional Outbox.

## Why an outbox instead of calling Kafka directly in the request?

Calling Kafka from the request creates a dual write. If the DB commits and the Kafka send fails, the score exists but the board never hears about it. If I send first and the commit fails, the board shows a score that doesn't exist. No ordering of two separate writes fixes that.

With an outbox there is only one write. The event row commits with the score or not at all, and publishing becomes a retryable background job. It also means a Kafka outage doesn't fail score submissions. The cost is extra latency before the board updates, and a table that needs a poller.

## At-least-once or exactly-once?

At-least-once. A crash after the broker acks but before `published_at` commits sends those rows again, and consumer retries can repeat work too.

Exactly-once would need Kafka transactions, and they still wouldn't cover the Redis write, so I'd pay for them and still have the problem at the last hop. It's simpler to make the consumer idempotent, because then a duplicate is harmless.

## Why does `ZADD GT` make processing idempotent and order-tolerant?

`GT` only updates a member if the new score is greater than the current one. The operation is effectively `max(current, new)`.

- Applying the same event twice leaves the same result, so redelivery is safe.
- Applying events in any order ends at the same maximum, so reordering is safe.

So I don't need a store of processed event ids. This only works because the board means best score. If it meant latest or total score, I'd need real dedupe. The username `HSET` runs in the same Lua script, so the two writes are atomic.

## What if Redis is wiped?

`POST /api/v1/admin/leaderboards/{gameId}/rebuild` recomputes `MAX(points)` per player from PostgreSQL, builds a temp key and swaps it in with `RENAME`, so readers never see a half-built board.

An event consumed during the rebuild lands on the old key and would be lost by the swap. Its score is already committed, though, so I read the scores again after the swap and apply anything new with `GT`. An event consumed after the swap hits the new key directly, so nothing falls through.

Rebuild works one game at a time, so after a full wipe you call it for each game.

## What if Kafka is down for an hour?

Score submissions keep working, because they only touch PostgreSQL. Outbox rows pile up, `outbox.pending` climbs and `outbox.publish.failures` counts the failed attempts. The board goes stale but still serves reads. When Kafka returns, the publisher drains the backlog at up to 100 rows per poll.

`max.block.ms` is 5 seconds and a batch stops at the first synchronous send failure, so the poller doesn't hold row locks for long during an outage.

## What if Redis is down?

Reads fail, since there's no fallback (a circuit breaker with a PostgreSQL fallback is the first thing on my next-steps list). The consumer retries 3 times with exponential backoff and then sends the event to `score-events.DLT`, so a long Redis outage fills the DLT. No data is lost, because PostgreSQL has every score, and rebuild repairs the board. Replaying the DLT would also work, but I haven't built a replay tool.

## Why partition by `gameId`, and when is that a hot-key problem?

It gives per-game ordering and keeps each game's events together. It becomes a problem when one game dominates: all of its events go to one partition, handled by one consumer thread, so adding consumers doesn't help that game.

Because `GT` is order-tolerant, I don't actually depend on per-game ordering for correctness. If a hot game appeared, I could key by `playerId` and spread the load across partitions. I'd keep ordering per player and lose nothing the board relies on.

## How would I scale consumers and the publisher?

Consumers: add instances or threads, up to the partition count (6 today). Increasing partitions later is possible but reshuffles keys, which is safe here because of `GT`.

Publisher: run more app instances. `FOR UPDATE SKIP LOCKED` means each instance locks a different batch without blocking the others. That loses strict global ordering between batches, which again `GT` tolerates. The real limit is PostgreSQL, because each publish batch holds a transaction open while it waits for acks.

## What is the consistency model, and where can a user see stale data?

Score writes are strongly consistent: once you get a 201, the score is committed. The board is eventually consistent. Right after a 201, `GET top` may not include the score yet. The delay is the outbox poll interval (200 ms by default) plus Kafka and consumer time, and it grows during an outage. `docs/PERFORMANCE.md` records the backlog measured under load.

A replay with the same `Idempotency-Key` always returns the original score, because it reads from PostgreSQL.

## Leaderboard semantics

Rank is by best score per player per game. Ties follow Redis's `ZREVRANGE` order, which is reverse lexicographic by member, and the member is the player's UUID. So ties are stable but arbitrary. A real product would probably break ties by who got there first.

## What I left out, and why

- **Authentication.** Out of scope for this project, but the admin rebuild endpoint is open, and that has to change before anything like production.
- **Rate limiting.** Same reasoning. It belongs at a gateway or alongside auth.
- **Schema registry.** One producer and one consumer in the same repo don't need it. With several teams consuming these events I'd add Avro or Protobuf and a registry.
- **Outbox cleanup.** Published rows stay forever. A real system needs a retention job or partitioned table.
- **Idempotency key scoping.** Keys are global, and a replay returns the original score even if the body differs. The IETF draft suggests rejecting a mismatched body, which I'd add alongside auth so keys can be scoped per client.
- **Redis eviction and persistence tuning.** It's a rebuildable read model, so I kept the defaults plus a memory cap.

## Deviations from the spec

1. **Nothing heavy ran on the development laptop.** Docker Desktop's install is broken on that machine (its registry keys are missing) and the machine was at its memory limit, so Testcontainers, Compose, the smoke test and k6 all ran on GitHub Actions runners. The numbers in `PERFORMANCE.md` come from a runner, not the laptop. `docs/evidence/` has the raw files.
2. **CI arrived at M0, not M5**, because it was the only place the tests could run. A throwaway branch was used for test iterations, and clean commits went to `main`.
3. **Spring Boot 3.5.16 was pinned by hand.** The Initializr now only offers 4.x, but the spec asks for the 3.5 line and 3.5.16 is on Maven Central, so I wrote the POM by hand and took only the wrapper files from the Initializr.
4. **`mvnw.cmd` is patched.** The generated script reads `.Target[0]` on the Maven home folder, which throws in Windows PowerShell 5.1 when the folder isn't a symlink. That broke the wrapper with `MAVEN_USER_HOME=X:\.m2`.
5. **The idempotency lookup runs before the session rules**, the reverse of section 8. Otherwise a retry after the session ended would get a 409 instead of the original score, which contradicts section 7. `ON CONFLICT DO NOTHING` still handles concurrent duplicates.
6. **Reads don't use pipelines.** `top` is two round trips, because the `HMGET` needs the player ids that `ZREVRANGE` returns, and a pipeline can't feed one command's reply into the next. The rank lookup started as a pipeline of `ZREVRANK` and `ZSCORE`, but load testing showed Spring opening a new TCP connection per pipeline, so it's now one Lua script. See `docs/PERFORMANCE.md`.
7. **Game ids are validated.** They must match `[A-Za-z0-9_-]{1,64}` and can't be `names`. Without this, a game called `names` would collide with the `leaderboard:names` hash and break with `WRONGTYPE`.
8. **Added migration `V2__outbox_trace_context.sql`** with a `trace_parent` column. The publisher runs later on another thread, so without this the Kafka send wouldn't appear in the request's trace.
9. **The DB span is one manual `score.persist` observation** around the insert and outbox write, not per-statement JDBC spans. A JDBC proxy would also trace the poller's queries, five times a second, and the trace only needed to show the write.
10. **No `logback-spring.xml`.** Boot's `logging.pattern.correlation` adds trace and span ids to the log pattern, and `logging.structured.format.console: ecs` gives JSON logs under the `docker` profile, so a custom file wasn't needed.
11. **`IllegalArgumentException` isn't retried.** A malformed event won't get better on retry, so it goes straight to the DLT. Other failures get the 3 retries.
12. **Added `OTLP_EXPORT_ENABLED`.** It defaults to off in Compose, because without the `observability` profile there's no Jaeger and the exporter logs an error on every batch.

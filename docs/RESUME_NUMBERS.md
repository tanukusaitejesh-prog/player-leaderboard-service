# Resume numbers

Every number here is measured. The sources are in `docs/evidence/`.

| Claim | Value | Source |
|---|---|---|
| Line coverage | 98.18% (378/385) | `evidence/jacoco-summary.txt`, `evidence/jacoco.csv` |
| Branch coverage | 91.94% (57/62) | same |
| Coverage gate | At least 80% line and 70% branch, enforced in `./mvnw verify` | `pom.xml` |
| Runtime | Java 25 (Temurin 25.0.5 LTS) in the container | `evidence/java25-2000/java-version.txt` |
| Throughput (Java 25) | 2000 req/s, median of 3 runs (2000 / 2000 / 2000) | `evidence/java25-2000/k6-run-*.json` |
| p99 latency (Java 25) | 6.86 ms, median of 3 runs (6.67–7.07) | same |
| Failed requests (Java 25) | 0% in steady state | same |
| Environment | One 4-vCPU GitHub Actions runner (EPYC 9V45), everything in Docker | `evidence/java25-2000/machine.txt` |
| Rank lookup fix | p99 357.57 ms → 4.14 ms at 1000 iterations/s (Java 17). On Java 25 the fixed code gives 13.49 ms at the same rate on a different runner CPU | `evidence/before-fix-1000`, `evidence/after-fix-1000`, `evidence/java25-1000` |
| Earlier Java 17 run | 1998 req/s, p99 18.99 ms (15.12–30.85), 0% failed | `evidence/final-2000` |

Coverage excludes the main class, `config/**` and `**/dto/**`. The total covers the 28 classes that remain.

## Final resume text

> **Player Session and Leaderboard Microservice** — Java 25, Spring Boot, Kafka, PostgreSQL, Redis, Docker, GitHub Actions · [GitHub](https://github.com/tanukusaitejesh-prog/player-leaderboard-service)
> - Built a containerised Java microservice for player sessions and a real-time leaderboard: PostgreSQL (SQL) as source of truth, score events published to Kafka through a transactional outbox, and an idempotent consumer with dead-letter handling maintaining a Redis (NoSQL) sorted-set read model for low-latency reads.
> - Kept code quality and testability high: unit and Testcontainers integration tests with an automated coverage gate (98% line, 92% branch) in a GitHub Actions CI pipeline; containerised with Docker and smoke-tested from a cold start on every push.
> - Implemented observability (Micrometer/Prometheus metrics, Grafana dashboard, OpenTelemetry traces across the outbox) and analysed performance with k6 load tests: about 2,000 req/s at p99 7 ms with 0% errors on a single 4-vCPU machine; found a Redis connection-per-request bottleneck (port exhaustion) and fixed it with a single Lua script, cutting p99 at 1,000 req/s from 358 ms to under 15 ms.

## Words I changed from the original bullets, and why

- **"Built and deployed" → "Built a containerised".** Nothing is deployed anywhere. It runs under Docker Compose locally and on CI runners.
- **"CI/CD" → "CI".** The pipeline builds and tests, but it doesn't push an image or deploy. If you add those, you can put CD back.

## Skills line check

All of these are true of the repo, so you can keep them: Microservices, Kafka, Spring Boot, design patterns (ports and adapters, Repository, Strategy, Builder, Transactional Outbox), observability, automated test coverage. Java is on 25 (LTS).

## Be ready to say

- **The numbers are from a CI runner, not your laptop.** Docker wasn't usable on your machine, so say so if asked. It's still one machine, with the load generator competing for its CPU.
- **What the coverage number leaves out.** The 98% excludes configuration and DTO classes, and it's that high because the integration tests run the real flow against real containers.

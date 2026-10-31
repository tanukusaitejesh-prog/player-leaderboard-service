#!/usr/bin/env bash
# Brings the stack up, drives the API end to end, and exits non-zero on any mismatch.
# Needs curl and jq. Set SKIP_UP=1 if the stack is already running.
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-240}"

fail() { echo "SMOKE FAILED: $1"; exit 1; }

post() { # path body [idempotency key]
  curl -fsS -X POST "$BASE_URL$1" -H 'Content-Type: application/json' \
    ${3:+-H "Idempotency-Key: $3"} -d "$2"
}

wait_until() { # description command
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  until eval "$2" >/dev/null 2>&1; do
    [ "$SECONDS" -lt "$deadline" ] || fail "timed out waiting for $1"
    sleep 2
  done
}

if [ -z "${SKIP_UP:-}" ]; then
  [ -f .env ] || cp .env.example .env
  docker compose --profile app up -d --build
fi

wait_until readiness "curl -fsS $BASE_URL/actuator/health/readiness"
echo 'Service is ready'

suffix="$(date +%s)$RANDOM"
game="smoke-$suffix"

alice=$(post /api/v1/players "{\"username\":\"alice_$suffix\"}" | jq -r .id)
bob=$(post /api/v1/players "{\"username\":\"bob_$suffix\"}" | jq -r .id)
alice_session=$(post /api/v1/sessions "{\"playerId\":\"$alice\",\"gameId\":\"$game\"}" | jq -r .id)
bob_session=$(post /api/v1/sessions "{\"playerId\":\"$bob\",\"gameId\":\"$game\"}" | jq -r .id)

first=$(post /api/v1/scores "{\"playerId\":\"$alice\",\"sessionId\":\"$alice_session\",\"points\":100}" "a1-$suffix" | jq -r .id)
replay=$(post /api/v1/scores "{\"playerId\":\"$alice\",\"sessionId\":\"$alice_session\",\"points\":999}" "a1-$suffix")
[ "$(jq -r .id <<<"$replay")" = "$first" ] || fail 'replay returned a different score'
[ "$(jq -r .points <<<"$replay")" = 100 ] || fail 'replay returned different points'
post /api/v1/scores "{\"playerId\":\"$bob\",\"sessionId\":\"$bob_session\",\"points\":250}" "b1-$suffix" >/dev/null
post /api/v1/scores "{\"playerId\":\"$alice\",\"sessionId\":\"$alice_session\",\"points\":40}" "a2-$suffix" >/dev/null

top_url="$BASE_URL/api/v1/leaderboards/$game/top?limit=10"
wait_until 'scores to reach the leaderboard' "[ \"\$(curl -fsS '$top_url' | jq length)\" = 2 ]"

top=$(curl -fsS "$top_url")
[ "$(jq -r '.[0].username' <<<"$top")" = "bob_$suffix" ] || fail "unexpected first place: $top"
[ "$(jq -r '.[0].score' <<<"$top")" = 250 ] || fail "unexpected first place: $top"
[ "$(jq -r '.[1].username' <<<"$top")" = "alice_$suffix" ] || fail "unexpected second place: $top"
[ "$(jq -r '.[1].score' <<<"$top")" = 100 ] || fail "unexpected second place: $top"

standing=$(curl -fsS "$BASE_URL/api/v1/leaderboards/$game/players/$alice")
[ "$(jq -r .rank <<<"$standing")" = 2 ] || fail "unexpected standing: $standing"

rebuilt=$(post "/api/v1/admin/leaderboards/$game/rebuild" '{}' | jq -r .rebuilt)
[ "$rebuilt" = 2 ] || fail "rebuild returned $rebuilt"

ended=$(post "/api/v1/sessions/$alice_session/end" '{}' | jq -r .status)
[ "$ended" = ENDED ] || fail "end returned $ended"

echo 'SMOKE PASSED'

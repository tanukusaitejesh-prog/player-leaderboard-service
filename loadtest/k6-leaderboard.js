// 90% reads, 10% writes at a fixed arrival rate. Tune with env vars:
//   BASE_URL, RATE (iterations/s at steady state), PLAYERS, STEADY_SECONDS, P99_TARGET_MS
//
// Requests after the 60 s ramp are tagged steady:yes. Report those submetrics: they exclude the
// setup phase and the ramp. Steady req/s = http_reqs{steady:yes}.count / STEADY_SECONDS.
import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RATE = Number(__ENV.RATE || 500);
const PLAYERS = Number(__ENV.PLAYERS || 500);
const STEADY_SECONDS = Number(__ENV.STEADY_SECONDS || 180);
const RAMP_MS = 60000;
const GAME_ID = __ENV.GAME_ID || `load-${Date.now()}`;
const P99_TARGET_MS = __ENV.P99_TARGET_MS || 600000;

export const options = {
  setupTimeout: '5m',
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
  scenarios: {
    mixed: {
      executor: 'ramping-arrival-rate',
      startRate: Math.ceil(RATE / 10),
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 500,
      stages: [
        { duration: '30s', target: Math.ceil(RATE / 2) },
        { duration: '30s', target: RATE },
        { duration: `${STEADY_SECONDS}s`, target: RATE },
      ],
    },
  },
  thresholds: {
    'http_req_failed{steady:yes}': ['rate<0.01'],
    'http_req_duration{steady:yes}': [`p(99)<${P99_TARGET_MS}`],
    'http_reqs{steady:yes}': ['count>0'],
  },
};

const JSON_HEADERS = { 'Content-Type': 'application/json' };

function post(path, body, headers, tags) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: { ...JSON_HEADERS, ...headers },
    tags,
  });
}

export function setup() {
  const stamp = Date.now() % 1000000;
  const players = [];
  for (let i = 0; i < PLAYERS; i++) {
    const player = post('/api/v1/players', { username: `k${stamp}_${i}` });
    if (player.status !== 201) {
      exec.test.abort(`could not create player: ${player.status} ${player.body}`);
    }
    const playerId = player.json('id');
    const session = post('/api/v1/sessions', { playerId, gameId: GAME_ID });
    if (session.status !== 201) {
      exec.test.abort(`could not create session: ${session.status} ${session.body}`);
    }
    players.push({ playerId, sessionId: session.json('id') });
  }

  // Put every player on the board so rank lookups measure hits rather than 404s.
  players.forEach((p, i) => {
    post(
      '/api/v1/scores',
      { playerId: p.playerId, sessionId: p.sessionId, points: i },
      { 'Idempotency-Key': `seed-${p.sessionId}` },
    );
  });
  const last = players[players.length - 1];
  for (let waited = 0; waited < 120; waited++) {
    const standing = http.get(`${BASE_URL}/api/v1/leaderboards/${GAME_ID}/players/${last.playerId}`);
    if (standing.status === 200) {
      return { players };
    }
    sleep(1);
  }
  exec.test.abort('seed scores did not reach the board');
}

export default function (data) {
  const steady = Date.now() - exec.scenario.startTime >= RAMP_MS ? 'yes' : 'no';
  const roll = Math.random();
  const player = data.players[Math.floor(Math.random() * data.players.length)];

  if (roll < 0.45) {
    const res = http.get(`${BASE_URL}/api/v1/leaderboards/${GAME_ID}/top?limit=10`, {
      tags: { name: 'GET top', steady },
    });
    check(res, { 'top 200': (r) => r.status === 200 });
  } else if (roll < 0.9) {
    const res = http.get(`${BASE_URL}/api/v1/leaderboards/${GAME_ID}/players/${player.playerId}`, {
      tags: { name: 'GET rank', steady },
    });
    check(res, { 'rank 200': (r) => r.status === 200 });
  } else {
    const res = post(
      '/api/v1/scores',
      {
        playerId: player.playerId,
        sessionId: player.sessionId,
        points: Math.floor(Math.random() * 1000000),
      },
      { 'Idempotency-Key': `${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}` },
      { name: 'POST score', steady },
    );
    check(res, { 'score 201': (r) => r.status === 201 });
  }
}

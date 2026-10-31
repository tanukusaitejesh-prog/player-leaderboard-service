CREATE TABLE players (
  id          UUID PRIMARY KEY,
  username    VARCHAR(32) NOT NULL UNIQUE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE game_sessions (
  id          UUID PRIMARY KEY,
  player_id   UUID NOT NULL REFERENCES players(id),
  game_id     VARCHAR(64) NOT NULL,
  status      VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','ENDED')),
  started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at    TIMESTAMPTZ
);
CREATE INDEX idx_sessions_player ON game_sessions(player_id);

CREATE TABLE scores (
  id               UUID PRIMARY KEY,                 -- also the event id
  player_id        UUID NOT NULL REFERENCES players(id),
  session_id       UUID NOT NULL REFERENCES game_sessions(id),
  game_id          VARCHAR(64) NOT NULL,
  points           INTEGER NOT NULL CHECK (points >= 0),
  idempotency_key  VARCHAR(64) NOT NULL UNIQUE,
  submitted_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_scores_game_player ON scores(game_id, player_id);

CREATE TABLE outbox_events (
  id            UUID PRIMARY KEY,
  topic         VARCHAR(128) NOT NULL,
  event_key     VARCHAR(128) NOT NULL,
  payload       JSONB NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(created_at) WHERE published_at IS NULL;

-- SMPPlatform gameplay modules. Each requested durable entity is created exactly once.
CREATE TABLE IF NOT EXISTS smp_guilds (
  guild_id UUID PRIMARY KEY, name VARCHAR(32) NOT NULL UNIQUE, tag VARCHAR(8) NOT NULL UNIQUE,
  description VARCHAR(512) NOT NULL DEFAULT '', owner_id UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  points BIGINT NOT NULL DEFAULT 0 CHECK(points>=0), version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS smp_guild_members (
  guild_id UUID NOT NULL REFERENCES smp_guilds(guild_id) ON DELETE CASCADE, player_id UUID NOT NULL UNIQUE,
  rank VARCHAR(16) NOT NULL CHECK(rank IN ('LEADER','OFFICER','MEMBER','RECRUIT')), joined_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(guild_id,player_id)
);
CREATE TABLE IF NOT EXISTS smp_guild_invites (
  invite_id UUID PRIMARY KEY, guild_id UUID NOT NULL REFERENCES smp_guilds(guild_id) ON DELETE CASCADE,
  target_player_id UUID NOT NULL, invited_by UUID NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL, UNIQUE(guild_id,target_player_id)
);
CREATE INDEX IF NOT EXISTS smp_guild_invites_target_idx ON smp_guild_invites(target_player_id,expires_at);
CREATE TABLE IF NOT EXISTS smp_point_accounts (
  account_id UUID PRIMARY KEY, owner_type VARCHAR(12) NOT NULL CHECK(owner_type IN ('PLAYER','GUILD')),
  owner_id UUID NOT NULL, currency_id VARCHAR(48) NOT NULL, balance BIGINT NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  UNIQUE(owner_type,owner_id,currency_id)
);
CREATE INDEX IF NOT EXISTS smp_point_accounts_leaderboard_idx ON smp_point_accounts(currency_id,balance DESC,owner_type);
CREATE TABLE IF NOT EXISTS smp_point_transactions (
  transaction_id UUID PRIMARY KEY, account_id UUID NOT NULL REFERENCES smp_point_accounts(account_id),
  currency_id VARCHAR(48) NOT NULL, amount BIGINT NOT NULL, balance_before BIGINT NOT NULL,
  balance_after BIGINT NOT NULL CHECK(balance_after=balance_before+amount), source VARCHAR(16) NOT NULL,
  reason VARCHAR(256) NOT NULL, metadata JSONB NOT NULL DEFAULT '{}'::jsonb, world_id VARCHAR(64),
  actor_id UUID, correlation_id UUID NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS smp_point_transactions_history_idx ON smp_point_transactions(account_id,occurred_at DESC);
CREATE INDEX IF NOT EXISTS smp_point_transactions_correlation_idx ON smp_point_transactions(correlation_id);
CREATE TABLE IF NOT EXISTS smp_hardcore_players (
  player_id UUID NOT NULL, season TEXT NOT NULL, state TEXT NOT NULL CHECK(state IN ('ALIVE','DEAD','SPECTATING','RESET_ELIGIBLE','LOCKED')),
  state_changed_at TIMESTAMPTZ NOT NULL, life_started_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(player_id,season)
);
CREATE TABLE IF NOT EXISTS smp_hardcore_deaths (
  id UUID PRIMARY KEY, player_id UUID NOT NULL, season TEXT NOT NULL, cause TEXT NOT NULL,
  killer_id UUID, killer_name TEXT, world_id TEXT NOT NULL, x DOUBLE PRECISION NOT NULL,
  y DOUBLE PRECISION NOT NULL, z DOUBLE PRECISION NOT NULL, survival_seconds BIGINT NOT NULL,
  statistics JSONB NOT NULL, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX IF NOT EXISTS smp_hardcore_deaths_season_idx ON smp_hardcore_deaths(season,occurred_at DESC);
CREATE TABLE IF NOT EXISTS smp_player_hardcore_statistics (
  player_id UUID NOT NULL, season TEXT NOT NULL, statistics JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(player_id,season)
);
CREATE TABLE IF NOT EXISTS smp_world_resets (
  correlation_id VARCHAR(128) NOT NULL, world_id TEXT NOT NULL, state TEXT NOT NULL,
  detail TEXT NOT NULL, changed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(correlation_id,changed_at)
);
CREATE INDEX IF NOT EXISTS smp_world_resets_latest_idx ON smp_world_resets(world_id,changed_at DESC);
CREATE TABLE IF NOT EXISTS smp_world_backups (
  id UUID PRIMARY KEY, correlation_id VARCHAR(128), world_id TEXT NOT NULL, path TEXT NOT NULL,
  checksum TEXT, verified BOOLEAN NOT NULL, detail TEXT, created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE IF NOT EXISTS smp_event_arenas (
  id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, world_id VARCHAR(64) NOT NULL CHECK(world_id='colosseum'),
  game_type VARCHAR(40) NOT NULL, spawn_points JSONB NOT NULL, spectator_spawn JSONB NOT NULL,
  minimum_players INTEGER NOT NULL CHECK(minimum_players>0), maximum_players INTEGER NOT NULL CHECK(maximum_players>=minimum_players),
  bounds JSONB NOT NULL, reset_policy VARCHAR(60) NOT NULL, instanced BOOLEAN NOT NULL DEFAULT true
);
CREATE TABLE IF NOT EXISTS smp_events (
  id UUID PRIMARY KEY, slug VARCHAR(100) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
  type VARCHAR(40) NOT NULL, arena_id UUID NOT NULL REFERENCES smp_event_arenas(id), created_by UUID NOT NULL,
  state VARCHAR(40) NOT NULL, scheduled_at TIMESTAMPTZ, check_in_opens_at TIMESTAMPTZ,
  registration_limit INTEGER NOT NULL CHECK(registration_limit>0), revision BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS smp_event_registrations (
  event_id UUID NOT NULL REFERENCES smp_events(id) ON DELETE CASCADE, player_id UUID NOT NULL,
  state VARCHAR(40) NOT NULL, queue_position INTEGER, team_id UUID, registered_at TIMESTAMPTZ NOT NULL,
  checked_in_at TIMESTAMPTZ, PRIMARY KEY(event_id,player_id)
);
CREATE TABLE IF NOT EXISTS smp_event_teams (
  id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES smp_events(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL, captain_id UUID NOT NULL, members JSONB NOT NULL
);
CREATE TABLE IF NOT EXISTS smp_event_rounds (
  id UUID PRIMARY KEY, event_id UUID NOT NULL REFERENCES smp_events(id) ON DELETE CASCADE,
  round_number INTEGER NOT NULL, state VARCHAR(40) NOT NULL, started_at TIMESTAMPTZ, ended_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS smp_event_matches (
  id UUID PRIMARY KEY, round_id UUID NOT NULL REFERENCES smp_event_rounds(id) ON DELETE CASCADE,
  participant_a UUID NOT NULL, participant_b UUID, winner_id UUID, score JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE TABLE IF NOT EXISTS smp_event_results (
  event_id UUID NOT NULL REFERENCES smp_events(id) ON DELETE CASCADE, participant_id UUID NOT NULL,
  placement INTEGER, score INTEGER NOT NULL, details JSONB NOT NULL, PRIMARY KEY(event_id,participant_id)
);
CREATE TABLE IF NOT EXISTS smp_event_rewards (
  id BIGSERIAL PRIMARY KEY, event_id UUID NOT NULL REFERENCES smp_events(id) ON DELETE CASCADE,
  placement INTEGER, currency_id VARCHAR(48) NOT NULL, amount BIGINT NOT NULL CHECK(amount>=0), description TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS smp_creative_submissions (
  id UUID PRIMARY KEY, submitter_id UUID NOT NULL, world_id VARCHAR(64) NOT NULL CHECK(world_id='atrium'),
  plot_id VARCHAR(160) NOT NULL, title VARCHAR(160) NOT NULL, description TEXT NOT NULL,
  image_references JSONB NOT NULL, state VARCHAR(40) NOT NULL, reviewer_id UUID, review_note TEXT,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(), reviewed_at TIMESTAMPTZ, revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS smp_creative_review_queue_idx ON smp_creative_submissions(state,submitted_at);
CREATE TABLE IF NOT EXISTS smp_featured_builds (
  id UUID PRIMARY KEY, submission_id UUID NOT NULL UNIQUE REFERENCES smp_creative_submissions(id),
  world_id VARCHAR(64) NOT NULL CHECK(world_id='atrium'), plot_id VARCHAR(160) NOT NULL,
  featured_at TIMESTAMPTZ NOT NULL, featured_by UUID NOT NULL, showcase_order INTEGER NOT NULL DEFAULT 0,
  archived_at TIMESTAMPTZ
);

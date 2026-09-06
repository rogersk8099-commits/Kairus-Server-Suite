CREATE TABLE IF NOT EXISTS schema_migrations (
  id TEXT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS server_heartbeats (
  server_id TEXT PRIMARY KEY,
  online BOOLEAN NOT NULL,
  tps DOUBLE PRECISION NOT NULL,
  player_count INTEGER NOT NULL CHECK (player_count >= 0),
  worlds JSONB NOT NULL DEFAULT '[]'::jsonb,
  players JSONB NOT NULL DEFAULT '[]'::jsonb,
  version TEXT NOT NULL,
  uptime_seconds BIGINT NOT NULL CHECK (uptime_seconds >= 0),
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS player_snapshots (
  minecraft_uuid UUID PRIMARY KEY,
  name TEXT NOT NULL,
  playtime_seconds BIGINT NOT NULL DEFAULT 0 CHECK (playtime_seconds >= 0),
  blocks_broken BIGINT NOT NULL DEFAULT 0 CHECK (blocks_broken >= 0),
  kills BIGINT NOT NULL DEFAULT 0 CHECK (kills >= 0),
  deaths BIGINT NOT NULL DEFAULT 0 CHECK (deaths >= 0),
  distance_meters DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (distance_meters >= 0),
  balance DOUBLE PRECISION NOT NULL DEFAULT 0,
  rank_name TEXT NOT NULL DEFAULT 'Member',
  world_name TEXT,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS link_codes (
  code_hash TEXT PRIMARY KEY,
  discord_user_id TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS link_codes_expires_at_idx ON link_codes (expires_at);

CREATE TABLE IF NOT EXISTS player_links (
  discord_user_id TEXT PRIMARY KEY,
  minecraft_uuid UUID NOT NULL UNIQUE,
  java_username TEXT NOT NULL,
  bedrock_xuid TEXT UNIQUE,
  linked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS plugin_commands (
  id TEXT PRIMARY KEY,
  server_id TEXT NOT NULL,
  command_type TEXT NOT NULL CHECK (command_type IN ('whitelist', 'notification')),
  payload JSONB NOT NULL DEFAULT '{}'::jsonb,
  status TEXT NOT NULL DEFAULT 'queued' CHECK (status IN ('queued', 'completed', 'failed')),
  error_message TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  acknowledged_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS plugin_commands_pending_idx ON plugin_commands (server_id, status, created_at);

CREATE TABLE IF NOT EXISTS events (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  starts_at TIMESTAMPTZ NOT NULL,
  ends_at TIMESTAMPTZ,
  location TEXT,
  status TEXT NOT NULL DEFAULT 'scheduled'
);
CREATE INDEX IF NOT EXISTS events_starts_at_idx ON events (starts_at);

CREATE TABLE IF NOT EXISTS streams (
  id TEXT PRIMARY KEY,
  channel_name TEXT NOT NULL,
  url TEXT NOT NULL,
  platform TEXT NOT NULL,
  title TEXT NOT NULL,
  viewer_count INTEGER NOT NULL DEFAULT 0 CHECK (viewer_count >= 0),
  live BOOLEAN NOT NULL DEFAULT TRUE,
  started_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS membership_tiers (
  slug TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  price_monthly_cents INTEGER NOT NULL CHECK (price_monthly_cents >= 0),
  benefits JSONB NOT NULL DEFAULT '[]'::jsonb
);

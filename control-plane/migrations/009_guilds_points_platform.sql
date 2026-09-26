CREATE TABLE IF NOT EXISTS platform_point_accounts (
  minecraft_uuid UUID NOT NULL,
  currency_id VARCHAR(48) NOT NULL,
  balance BIGINT NOT NULL DEFAULT 0 CHECK(balance >= 0),
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(minecraft_uuid,currency_id)
);
CREATE INDEX IF NOT EXISTS idx_platform_points_leaderboard ON platform_point_accounts(currency_id,balance DESC);

CREATE TABLE IF NOT EXISTS platform_point_transactions (
  id UUID PRIMARY KEY,
  minecraft_uuid UUID NOT NULL,
  currency_id VARCHAR(48) NOT NULL,
  amount BIGINT NOT NULL,
  balance_before BIGINT NOT NULL,
  balance_after BIGINT NOT NULL CHECK(balance_after=balance_before+amount),
  source VARCHAR(32) NOT NULL,
  reason VARCHAR(256) NOT NULL,
  actor_minecraft_uuid UUID,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_platform_points_history ON platform_point_transactions(minecraft_uuid,currency_id,occurred_at DESC);

CREATE TABLE IF NOT EXISTS platform_guilds (
  id UUID PRIMARY KEY,
  name VARCHAR(32) NOT NULL UNIQUE,
  tag VARCHAR(8) NOT NULL UNIQUE,
  description VARCHAR(512) NOT NULL DEFAULT '',
  owner_minecraft_uuid UUID NOT NULL,
  points BIGINT NOT NULL DEFAULT 0 CHECK(points>=0),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS platform_guild_members (
  guild_id UUID NOT NULL REFERENCES platform_guilds(id) ON DELETE CASCADE,
  minecraft_uuid UUID NOT NULL UNIQUE,
  rank VARCHAR(16) NOT NULL CHECK(rank IN ('LEADER','OFFICER','MEMBER','RECRUIT')),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY(guild_id,minecraft_uuid)
);
CREATE TABLE IF NOT EXISTS platform_guild_invites (
  id UUID PRIMARY KEY,
  guild_id UUID NOT NULL REFERENCES platform_guilds(id) ON DELETE CASCADE,
  target_minecraft_uuid UUID NOT NULL,
  invited_by_minecraft_uuid UUID NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(guild_id,target_minecraft_uuid)
);
CREATE INDEX IF NOT EXISTS idx_platform_guild_invites_target ON platform_guild_invites(target_minecraft_uuid,expires_at);

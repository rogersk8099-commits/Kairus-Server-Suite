-- SMPPlatform 1.0 canonical PostgreSQL foundation. All durable entities are smp_-prefixed.
CREATE TABLE IF NOT EXISTS smp_players (
  id UUID PRIMARY KEY, platform_user_id UUID UNIQUE, username VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NOT NULL, first_join_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_join_at TIMESTAMPTZ, last_seen_at TIMESTAMPTZ, playtime_seconds BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS smp_minecraft_accounts (
  minecraft_uuid UUID PRIMARY KEY, player_id UUID NOT NULL REFERENCES smp_players(id) ON DELETE CASCADE,
  edition VARCHAR(16) NOT NULL CHECK (edition IN ('JAVA','BEDROCK','UNKNOWN')), xuid VARCHAR(32) UNIQUE,
  last_known_name VARCHAR(64) NOT NULL, first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(), last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS smp_minecraft_accounts_player_idx ON smp_minecraft_accounts(player_id);
CREATE TABLE IF NOT EXISTS smp_worlds (
  id VARCHAR(63) PRIMARY KEY, minecraft_world_name VARCHAR(63) NOT NULL UNIQUE, display_name VARCHAR(128) NOT NULL,
  world_type VARCHAR(63) NOT NULL, season VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL,
  registry_revision BIGINT NOT NULL, definition_json JSONB NOT NULL, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS smp_integration_state (
  integration_key VARCHAR(128) PRIMARY KEY, revision BIGINT, last_sync_at TIMESTAMPTZ,
  status VARCHAR(32) NOT NULL, detail TEXT, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS smp_audit_logs (
  id UUID PRIMARY KEY, actor_id UUID, actor_name VARCHAR(128), action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64), target_id VARCHAR(128), world_id VARCHAR(63), before_state JSONB, after_state JSONB,
  outcome VARCHAR(48), detail TEXT, reason TEXT NOT NULL DEFAULT '', correlation_id VARCHAR(128),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS smp_audit_actor_time_idx ON smp_audit_logs(actor_id, created_at DESC);
CREATE TABLE IF NOT EXISTS smp_event_outbox (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(64) NOT NULL, aggregate_id VARCHAR(128) NOT NULL,
  operation VARCHAR(48), event_type VARCHAR(128) NOT NULL, destinations JSONB NOT NULL DEFAULT '["WEBSITE"]'::jsonb,
  idempotency_key VARCHAR(200) NOT NULL UNIQUE, payload JSONB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','LEASED','DELIVERED','DEAD')), attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_until TIMESTAMPTZ, delivered_at TIMESTAMPTZ,
  last_error TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS smp_event_outbox_dispatch_idx ON smp_event_outbox(status, available_at, created_at);
CREATE TABLE IF NOT EXISTS smp_account_links (
  id UUID PRIMARY KEY, minecraft_uuid UUID NOT NULL, code_hash CHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ, revoked_at TIMESTAMPTZ, revoke_reason VARCHAR(200), CHECK (expires_at > created_at)
);
CREATE INDEX IF NOT EXISTS smp_account_links_generation_idx ON smp_account_links(minecraft_uuid, created_at DESC);

CREATE TABLE IF NOT EXISTS platform_guild_admin_audit (
  id UUID PRIMARY KEY,
  actor_minecraft_uuid UUID NOT NULL,
  guild_id UUID,
  target_minecraft_uuid UUID,
  action VARCHAR(48) NOT NULL,
  reason VARCHAR(256) NOT NULL,
  before_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  after_state JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_platform_guild_admin_audit_guild ON platform_guild_admin_audit(guild_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_platform_guild_admin_audit_actor ON platform_guild_admin_audit(actor_minecraft_uuid,created_at DESC);

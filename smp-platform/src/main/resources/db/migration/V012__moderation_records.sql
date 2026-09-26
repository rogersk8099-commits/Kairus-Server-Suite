CREATE TABLE IF NOT EXISTS smp_moderation_records (
 id UUID PRIMARY KEY,
 target_minecraft_uuid UUID NOT NULL,
 actor_minecraft_uuid UUID NOT NULL,
 action TEXT NOT NULL,
 reason TEXT,
 expires_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_smp_moderation_target_created ON smp_moderation_records(target_minecraft_uuid,created_at DESC);

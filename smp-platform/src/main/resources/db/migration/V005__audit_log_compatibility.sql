-- Existing production databases created by early SMPPlatform builds may have
-- an older smp_audit_logs shape.  Phase 3 guild/points writes require this
-- complete, additive schema; no audit history is removed or rewritten.
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS actor_id UUID;
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS actor_name VARCHAR(128);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS target_type VARCHAR(64);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS target_id VARCHAR(128);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS world_id VARCHAR(63);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS before_state JSONB;
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS after_state JSONB;
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS outcome VARCHAR(48);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS detail TEXT;
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS reason TEXT NOT NULL DEFAULT '';
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128);
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE IF EXISTS smp_audit_logs ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE INDEX IF NOT EXISTS smp_audit_actor_time_idx ON smp_audit_logs(actor_id, created_at DESC);

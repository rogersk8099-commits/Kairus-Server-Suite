-- Existing production databases created by early SMPPlatform builds may have
-- an older smp_audit_logs shape.  Phase 3 guild/points writes require this
-- complete, additive schema; no audit history is removed or rewritten.
-- Some early deployments predate the audit table completely, so create the
-- baseline before applying the additive compatibility columns and index.
CREATE TABLE IF NOT EXISTS smp_audit_logs (
  id UUID PRIMARY KEY,
  actor_id UUID,
  actor_name VARCHAR(128),
  action VARCHAR(128) NOT NULL,
  target_type VARCHAR(64),
  target_id VARCHAR(128),
  world_id VARCHAR(63),
  before_state JSONB,
  after_state JSONB,
  outcome VARCHAR(48),
  detail TEXT,
  reason TEXT NOT NULL DEFAULT '',
  correlation_id VARCHAR(128),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
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

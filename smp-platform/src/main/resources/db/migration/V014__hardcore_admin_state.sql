CREATE TABLE IF NOT EXISTS smp_hardcore_player_state (
 minecraft_uuid UUID PRIMARY KEY,
 state TEXT NOT NULL CHECK (state IN ('ALIVE','DEAD','SPECTATING','RESET_ELIGIBLE','LOCKED')),
 season TEXT,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS smp_hardcore_admin_audit (
 id UUID PRIMARY KEY,
 actor_uuid UUID NOT NULL,
 target_uuid UUID NOT NULL,
 action TEXT NOT NULL,
 reason TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hardcore_admin_audit_target ON smp_hardcore_admin_audit(target_uuid,created_at DESC);

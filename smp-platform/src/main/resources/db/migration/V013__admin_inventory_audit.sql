CREATE TABLE IF NOT EXISTS smp_admin_inventory_audit (
 id UUID PRIMARY KEY,
 actor_minecraft_uuid UUID NOT NULL,
 target_minecraft_uuid UUID NOT NULL,
 inventory_type TEXT NOT NULL,
 action TEXT NOT NULL,
 source_slot INTEGER,
 destination_slot INTEGER,
 item_fingerprint TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_smp_admin_inventory_audit_target ON smp_admin_inventory_audit(target_minecraft_uuid,created_at DESC);

-- SMPPlatform owns this table. Other legacy modules used smp_event_outbox with incompatible columns.
CREATE TABLE IF NOT EXISTS smp_platform_event_outbox (
  id UUID PRIMARY KEY, aggregate_type VARCHAR(64) NOT NULL, aggregate_id VARCHAR(128) NOT NULL,
  event_type VARCHAR(128) NOT NULL, idempotency_key VARCHAR(200) NOT NULL UNIQUE,
  payload JSONB NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','LEASED','DELIVERED','DEAD')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
  available_at TIMESTAMPTZ NOT NULL DEFAULT now(), lease_until TIMESTAMPTZ,
  delivered_at TIMESTAMPTZ, last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS smp_platform_event_outbox_dispatch_idx
  ON smp_platform_event_outbox(status, available_at, created_at);

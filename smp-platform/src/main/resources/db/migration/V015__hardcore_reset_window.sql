CREATE TABLE IF NOT EXISTS smp_hardcore_reset_window (
 id INTEGER PRIMARY KEY CHECK (id=1),
 opens_at TIMESTAMPTZ NOT NULL,
 closes_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 CHECK (closes_at > opens_at)
);

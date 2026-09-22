ALTER TABLE server_heartbeats
  ADD COLUMN IF NOT EXISTS world_players JSONB NOT NULL DEFAULT '[]'::jsonb;

-- One Discord/platform identity may own multiple verified Minecraft accounts.
ALTER TABLE player_links DROP CONSTRAINT IF EXISTS player_links_pkey;
ALTER TABLE player_links ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE player_links ADD PRIMARY KEY (discord_user_id, minecraft_uuid);
WITH ranked AS (
  SELECT discord_user_id, minecraft_uuid, ROW_NUMBER() OVER (PARTITION BY discord_user_id ORDER BY linked_at ASC) AS position
  FROM player_links
) UPDATE player_links links SET is_primary = TRUE FROM ranked WHERE links.discord_user_id = ranked.discord_user_id AND links.minecraft_uuid = ranked.minecraft_uuid AND ranked.position = 1;
CREATE UNIQUE INDEX IF NOT EXISTS player_links_one_primary_per_discord_idx ON player_links (discord_user_id) WHERE is_primary;

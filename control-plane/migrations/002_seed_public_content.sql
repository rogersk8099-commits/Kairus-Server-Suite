INSERT INTO membership_tiers (slug, name, description, price_monthly_cents, benefits)
VALUES
  ('initiate', 'Initiate', 'Everything needed to join the season and begin building.', 0, '["Access to all public worlds", "Portal statistics", "Standard event queue", "Discord member role"]'::jsonb),
  ('ronin', 'Ronin', 'Priority access and cosmetic identity for regular players.', 600, '["Priority queue", "Neon name tag", "Extra land claims", "Early event registration", "Monthly cosmetic drop"]'::jsonb),
  ('legend', 'Legend', 'Creator tooling and community leadership access.', 1400, '["All Ronin benefits", "Creator build tools", "Stream promotion", "Roadmap voting", "Custom particle trail"]'::jsonb)
ON CONFLICT (slug) DO UPDATE SET
  name = EXCLUDED.name,
  description = EXCLUDED.description,
  price_monthly_cents = EXCLUDED.price_monthly_cents,
  benefits = EXCLUDED.benefits;

INSERT INTO events (id, title, description, starts_at, ends_at, location, status)
VALUES
  ('gauntlet-round-12', 'The Gauntlet — Round 12', 'A 64-player shrinking-border team tournament.', '2026-11-14T20:00:00Z', '2026-11-14T22:00:00Z', 'Neon Coliseum', 'scheduled'),
  ('neon-districts', 'Build Battle: Neon Districts', 'Three hours, one palette, judged live by the creator council.', '2026-11-15T18:00:00Z', '2026-11-15T21:00:00Z', 'The Atrium', 'scheduled'),
  ('ashfall-summit', 'Ashfall Nation Summit', 'Nation leaders negotiate borders, trade routes and season rules.', '2026-11-16T19:00:00Z', '2026-11-16T21:00:00Z', 'Ashfall', 'scheduled'),
  ('winter-drop', 'Season 7 Winter Drop', 'A biome pass, frost mobs and refreshed seasonal loot tables.', '2026-11-28T17:00:00Z', '2026-11-28T20:00:00Z', 'All Worlds', 'scheduled')
ON CONFLICT (id) DO UPDATE SET
  title = EXCLUDED.title,
  description = EXCLUDED.description,
  starts_at = EXCLUDED.starts_at,
  ends_at = EXCLUDED.ends_at,
  location = EXCLUDED.location,
  status = EXCLUDED.status;

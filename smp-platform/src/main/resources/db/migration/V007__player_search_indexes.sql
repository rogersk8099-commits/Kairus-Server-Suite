DO $$
BEGIN
  IF to_regclass('public.smp_minecraft_accounts') IS NOT NULL THEN
    CREATE INDEX IF NOT EXISTS idx_smp_minecraft_accounts_name_lower ON smp_minecraft_accounts ((lower(last_known_name)));
    CREATE INDEX IF NOT EXISTS idx_smp_minecraft_accounts_last_seen ON smp_minecraft_accounts (last_seen_at DESC);
  END IF;
  IF to_regclass('public.smp_players') IS NOT NULL THEN
    CREATE INDEX IF NOT EXISTS idx_smp_players_last_seen ON smp_players (last_seen_at DESC);
  END IF;
END $$;

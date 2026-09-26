-- One-time compatibility import. Safe when the legacy SMP tables do not exist.
DO $$
BEGIN
  IF to_regclass('public.smp_point_accounts') IS NOT NULL
     AND to_regclass('public.smp_minecraft_accounts') IS NOT NULL THEN
    INSERT INTO platform_point_accounts(minecraft_uuid,currency_id,balance,version,updated_at)
    SELECT ma.minecraft_uuid,pa.currency_id,pa.balance,pa.version,COALESCE(pa.updated_at,NOW())
    FROM smp_point_accounts pa
    JOIN smp_minecraft_accounts ma ON ma.player_id=pa.owner_id
    WHERE pa.owner_type='PLAYER'
    ON CONFLICT(minecraft_uuid,currency_id) DO NOTHING;
  END IF;

  IF to_regclass('public.smp_guilds') IS NOT NULL
     AND to_regclass('public.smp_guild_members') IS NOT NULL
     AND to_regclass('public.smp_minecraft_accounts') IS NOT NULL THEN
    INSERT INTO platform_guilds(id,name,tag,description,owner_minecraft_uuid,points,version,created_at)
    SELECT g.guild_id,g.name,g.tag,g.description,owner_ma.minecraft_uuid,g.points,g.version,g.created_at
    FROM smp_guilds g JOIN smp_minecraft_accounts owner_ma ON owner_ma.player_id=g.owner_id
    ON CONFLICT(id) DO NOTHING;

    INSERT INTO platform_guild_members(guild_id,minecraft_uuid,rank,joined_at)
    SELECT gm.guild_id,ma.minecraft_uuid,gm.rank,gm.joined_at
    FROM smp_guild_members gm JOIN smp_minecraft_accounts ma ON ma.player_id=gm.player_id
    JOIN platform_guilds g ON g.id=gm.guild_id
    ON CONFLICT DO NOTHING;

    IF to_regclass('public.smp_guild_invites') IS NOT NULL THEN
      INSERT INTO platform_guild_invites(id,guild_id,target_minecraft_uuid,invited_by_minecraft_uuid,expires_at,created_at)
      SELECT gi.invite_id,gi.guild_id,target_ma.minecraft_uuid,inviter_ma.minecraft_uuid,gi.expires_at,gi.created_at
      FROM smp_guild_invites gi
      JOIN smp_minecraft_accounts target_ma ON target_ma.player_id=gi.target_player_id
      JOIN smp_minecraft_accounts inviter_ma ON inviter_ma.player_id=gi.invited_by
      JOIN platform_guilds g ON g.id=gi.guild_id
      ON CONFLICT DO NOTHING;
    END IF;
  END IF;
END $$;

import { randomUUID } from "node:crypto";
import pg from "pg";
import type {
  BridgeEventRecord,
  ChatQueueMessage,
  ControlPlaneStore,
  EventRecord,
  LinkCodeResult,
  LinkIdentity,
  MembershipTier,
  NewBridgeEvent,
  NewChatQueueMessage,
  NewPluginCommand,
  PlayerLink,
  PlayerSnapshot,
  PluginCommand,
  ServerHeartbeat,
  StreamRecord
} from "../types.js";

const { Pool } = pg;

type DbRow = Record<string, unknown>;

function timestamp(value: unknown): string {
  return new Date(value as string | Date).toISOString();
}
function heartbeatFrom(row: DbRow): ServerHeartbeat {
  return { serverId: String(row.server_id), online: Boolean(row.online), tps: Number(row.tps), playerCount: Number(row.player_count), worlds: row.worlds as string[], worldPlayers: (row.world_players ?? []) as ServerHeartbeat["worldPlayers"], players: row.players as string[], version: String(row.version), uptimeSeconds: Number(row.uptime_seconds), receivedAt: timestamp(row.received_at) };
}
function snapshotFrom(row: DbRow): PlayerSnapshot {
  return { minecraftUuid: String(row.minecraft_uuid), name: String(row.name), playtimeSeconds: Number(row.playtime_seconds), blocksBroken: Number(row.blocks_broken), kills: Number(row.kills), deaths: Number(row.deaths), distanceMeters: Number(row.distance_meters), balance: Number(row.balance), rankName: String(row.rank_name), worldName: row.world_name ? String(row.world_name) : null, updatedAt: timestamp(row.updated_at) };
}
function linkFrom(row: DbRow): PlayerLink {
  return { discordUserId: String(row.discord_user_id), minecraftUuid: String(row.minecraft_uuid), javaUsername: String(row.java_username), bedrockXuid: row.bedrock_xuid ? String(row.bedrock_xuid) : null, isPrimary: Boolean(row.is_primary), linkedAt: timestamp(row.linked_at) };
}
function commandFrom(row: DbRow): PluginCommand {
  return { id: String(row.id), serverId: String(row.server_id), commandType: row.command_type as PluginCommand["commandType"], payload: row.payload as Record<string, unknown>, status: row.status as PluginCommand["status"], errorMessage: row.error_message ? String(row.error_message) : null, createdAt: timestamp(row.created_at), acknowledgedAt: row.acknowledged_at ? timestamp(row.acknowledged_at) : null };
}
function bridgeEventFrom(row: DbRow): BridgeEventRecord {
  return { id: String(row.id), serverId: String(row.server_id), eventId: String(row.event_id), eventType: row.event_type as BridgeEventRecord["eventType"], occurredAt: timestamp(row.occurred_at), worldName: row.world_name ? String(row.world_name) : null, minecraftUuid: row.minecraft_uuid ? String(row.minecraft_uuid) : null, minecraftName: row.minecraft_name ? String(row.minecraft_name) : null, content: String(row.content), details: row.details as Record<string, string>, receivedAt: timestamp(row.received_at) };
}
function chatMessageFrom(row: DbRow): ChatQueueMessage {
  return { id: String(row.id), serverId: String(row.server_id), idempotencyKey: row.idempotency_key ? String(row.idempotency_key) : null, discordMessageId: row.discord_message_id ? String(row.discord_message_id) : null, discordAuthorId: row.discord_author_id ? String(row.discord_author_id) : null, displayName: row.display_name ? String(row.display_name) : null, targetWorld: row.target_world ? String(row.target_world) : null, content: String(row.content), status: row.status as ChatQueueMessage["status"], deliveryAttempts: Number(row.delivery_attempts), lastAttemptAt: row.last_attempt_at ? timestamp(row.last_attempt_at) : null, acknowledgedAt: row.acknowledged_at ? timestamp(row.acknowledged_at) : null, acknowledgementDetail: row.acknowledgement_detail ? String(row.acknowledgement_detail) : null, deadLetteredAt: row.dead_lettered_at ? timestamp(row.dead_lettered_at) : null, createdAt: timestamp(row.created_at) };
}

export class PostgresStore implements ControlPlaneStore {
  public readonly kind = "postgres" as const;
  private readonly pool: pg.Pool;

  constructor(databaseUrl: string) {
    this.pool = new Pool({ connectionString: databaseUrl, max: 10, ssl: databaseUrl.includes("localhost") ? undefined : { rejectUnauthorized: false } });
  }

  async close(): Promise<void> { await this.pool.end(); }

  async getLatestHeartbeat(): Promise<ServerHeartbeat | null> {
    const { rows } = await this.pool.query("SELECT * FROM server_heartbeats ORDER BY received_at DESC LIMIT 1");
    return rows[0] ? heartbeatFrom(rows[0]) : null;
  }

  async recordHeartbeat(heartbeat: Omit<ServerHeartbeat, "receivedAt">): Promise<ServerHeartbeat> {
    const { rows } = await this.pool.query(
      `INSERT INTO server_heartbeats (server_id, online, tps, player_count, worlds, world_players, players, version, uptime_seconds)
       VALUES ($1,$2,$3,$4,$5::jsonb,$6::jsonb,$7::jsonb,$8,$9)
       ON CONFLICT (server_id) DO UPDATE SET online = EXCLUDED.online, tps = EXCLUDED.tps, player_count = EXCLUDED.player_count, worlds = EXCLUDED.worlds, world_players = EXCLUDED.world_players, players = EXCLUDED.players, version = EXCLUDED.version, uptime_seconds = EXCLUDED.uptime_seconds, received_at = NOW()
       RETURNING *`,
      [heartbeat.serverId, heartbeat.online, heartbeat.tps, heartbeat.playerCount, JSON.stringify(heartbeat.worlds), JSON.stringify(heartbeat.worldPlayers), JSON.stringify(heartbeat.players), heartbeat.version, heartbeat.uptimeSeconds]
    );
    return heartbeatFrom(rows[0]);
  }

  async recordPlayerSnapshot(snapshot: Omit<PlayerSnapshot, "updatedAt">): Promise<PlayerSnapshot> {
    const { rows } = await this.pool.query(
      `INSERT INTO player_snapshots (minecraft_uuid, name, playtime_seconds, blocks_broken, kills, deaths, distance_meters, balance, rank_name, world_name)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       ON CONFLICT (minecraft_uuid) DO UPDATE SET name=EXCLUDED.name, playtime_seconds=EXCLUDED.playtime_seconds, blocks_broken=EXCLUDED.blocks_broken, kills=EXCLUDED.kills, deaths=EXCLUDED.deaths, distance_meters=EXCLUDED.distance_meters, balance=EXCLUDED.balance, rank_name=EXCLUDED.rank_name, world_name=EXCLUDED.world_name, updated_at=NOW()
       RETURNING *`,
      [snapshot.minecraftUuid, snapshot.name, snapshot.playtimeSeconds, snapshot.blocksBroken, snapshot.kills, snapshot.deaths, snapshot.distanceMeters, snapshot.balance, snapshot.rankName, snapshot.worldName]
    );
    return snapshotFrom(rows[0]);
  }

  async getPlayerSnapshot(minecraftUuid: string): Promise<PlayerSnapshot | null> {
    const { rows } = await this.pool.query("SELECT * FROM player_snapshots WHERE minecraft_uuid = $1", [minecraftUuid]);
    return rows[0] ? snapshotFrom(rows[0]) : null;
  }

  async listPlayerSnapshots(limit: number): Promise<PlayerSnapshot[]> {
    const { rows } = await this.pool.query("SELECT * FROM player_snapshots ORDER BY playtime_seconds DESC, name ASC LIMIT $1", [limit]);
    return rows.map(snapshotFrom);
  }

  async createLinkCode(discordUserId: string, codeHash: string, expiresAt: Date): Promise<void> {
    await this.pool.query("INSERT INTO link_codes (code_hash, discord_user_id, expires_at) VALUES ($1,$2,$3)", [codeHash, discordUserId, expiresAt]);
  }

  async completeLinkCode(codeHash: string, identity: LinkIdentity): Promise<LinkCodeResult> {
    const client = await this.pool.connect();
    try {
      await client.query("BEGIN");
      const codeResult = await client.query("SELECT * FROM link_codes WHERE code_hash = $1 FOR UPDATE", [codeHash]);
      const code = codeResult.rows[0];
      if (!code || code.consumed_at || new Date(code.expires_at) <= new Date()) {
        await client.query("ROLLBACK");
        return { ok: false, reason: "invalid_or_expired" };
      }
      const alreadyLinked = await client.query("SELECT 1 FROM player_links WHERE minecraft_uuid = $1 OR ($2::text IS NOT NULL AND bedrock_xuid = $2) LIMIT 1", [identity.minecraftUuid, identity.bedrockXuid ?? null]);
      if (alreadyLinked.rowCount) {
        await client.query("ROLLBACK");
        return { ok: false, reason: "identity_already_linked" };
      }
      const primary = await client.query("SELECT 1 FROM player_links WHERE discord_user_id = $1 LIMIT 1", [code.discord_user_id]);
      const linkResult = await client.query("INSERT INTO player_links (discord_user_id, minecraft_uuid, java_username, bedrock_xuid, is_primary) VALUES ($1,$2,$3,$4,$5) RETURNING *", [code.discord_user_id, identity.minecraftUuid, identity.javaUsername, identity.bedrockXuid ?? null, primary.rowCount === 0]);
      await client.query("UPDATE link_codes SET consumed_at = NOW() WHERE code_hash = $1", [codeHash]);
      await client.query("COMMIT");
      return { ok: true, link: linkFrom(linkResult.rows[0]) };
    } catch (error) {
      await client.query("ROLLBACK").catch(() => undefined);
      if ((error as { code?: string }).code === "23505") return { ok: false, reason: "identity_already_linked" };
      throw error;
    } finally { client.release(); }
  }

  async getLinkByDiscordUser(discordUserId: string): Promise<PlayerLink | null> {
    const { rows } = await this.pool.query("SELECT * FROM player_links WHERE discord_user_id = $1 ORDER BY is_primary DESC, linked_at ASC LIMIT 1", [discordUserId]);
    return rows[0] ? linkFrom(rows[0]) : null;
  }

  async getLinksByDiscordUser(discordUserId: string): Promise<PlayerLink[]> {
    const { rows } = await this.pool.query("SELECT * FROM player_links WHERE discord_user_id = $1 ORDER BY is_primary DESC, linked_at ASC", [discordUserId]);
    return rows.map(linkFrom);
  }

  async setPrimaryMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean> {
    const client = await this.pool.connect();
    try { await client.query("BEGIN"); const owned = await client.query("SELECT 1 FROM player_links WHERE discord_user_id = $1 AND minecraft_uuid = $2 FOR UPDATE", [discordUserId, minecraftUuid]); if (!owned.rowCount) { await client.query("ROLLBACK"); return false; } await client.query("UPDATE player_links SET is_primary = FALSE WHERE discord_user_id = $1", [discordUserId]); await client.query("UPDATE player_links SET is_primary = TRUE WHERE discord_user_id = $1 AND minecraft_uuid = $2", [discordUserId, minecraftUuid]); await client.query("COMMIT"); return true; } catch (error) { await client.query("ROLLBACK").catch(() => undefined); throw error; } finally { client.release(); }
  }

  async unlinkMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean> {
    const client = await this.pool.connect();
    try { await client.query("BEGIN"); const removed = await client.query("DELETE FROM player_links WHERE discord_user_id = $1 AND minecraft_uuid = $2 RETURNING is_primary", [discordUserId, minecraftUuid]); if (!removed.rowCount) { await client.query("ROLLBACK"); return false; } if (removed.rows[0].is_primary) await client.query("UPDATE player_links SET is_primary = TRUE WHERE discord_user_id = $1 AND minecraft_uuid = (SELECT minecraft_uuid FROM player_links WHERE discord_user_id = $1 ORDER BY linked_at ASC LIMIT 1)", [discordUserId]); await client.query("COMMIT"); return true; } catch (error) { await client.query("ROLLBACK").catch(() => undefined); throw error; } finally { client.release(); }
  }

  async unlinkDiscordUser(discordUserId: string): Promise<boolean> {
    const result = await this.pool.query("DELETE FROM player_links WHERE discord_user_id = $1", [discordUserId]);
    return result.rowCount === 1;
  }

  async listEvents(limit: number): Promise<EventRecord[]> {
    const { rows } = await this.pool.query("SELECT * FROM events WHERE starts_at >= NOW() ORDER BY starts_at ASC LIMIT $1", [limit]);
    return rows.map((row) => ({ id: String(row.id), title: String(row.title), description: String(row.description), startsAt: timestamp(row.starts_at), endsAt: row.ends_at ? timestamp(row.ends_at) : null, location: row.location ? String(row.location) : null, status: String(row.status) }));
  }

  async listStreams(): Promise<StreamRecord[]> {
    const { rows } = await this.pool.query("SELECT * FROM streams WHERE live = TRUE ORDER BY viewer_count DESC, channel_name ASC");
    return rows.map((row) => ({ id: String(row.id), channelName: String(row.channel_name), url: String(row.url), platform: String(row.platform), title: String(row.title), viewerCount: Number(row.viewer_count), live: Boolean(row.live), startedAt: row.started_at ? timestamp(row.started_at) : null }));
  }

  async listMembershipTiers(): Promise<MembershipTier[]> {
    const { rows } = await this.pool.query("SELECT * FROM membership_tiers ORDER BY price_monthly_cents ASC, name ASC");
    return rows.map((row) => ({ slug: String(row.slug), name: String(row.name), description: String(row.description), priceMonthlyCents: Number(row.price_monthly_cents), benefits: row.benefits as string[] }));
  }

  async listQueuedPluginCommands(serverId: string): Promise<PluginCommand[]> {
    const { rows } = await this.pool.query("SELECT * FROM plugin_commands WHERE server_id = $1 AND status = 'queued' ORDER BY created_at ASC", [serverId]);
    return rows.map(commandFrom);
  }

  async acknowledgePluginCommand(serverId: string, id: string, status: "completed" | "failed", errorMessage?: string): Promise<PluginCommand | null> {
    const { rows } = await this.pool.query("UPDATE plugin_commands SET status = $3, error_message = $4, acknowledged_at = NOW() WHERE id = $1 AND server_id = $2 AND status = 'queued' RETURNING *", [id, serverId, status, errorMessage ?? null]);
    return rows[0] ? commandFrom(rows[0]) : null;
  }

  async queuePluginCommand(command: NewPluginCommand): Promise<PluginCommand> {
    const { rows } = await this.pool.query("INSERT INTO plugin_commands (id, server_id, command_type, payload) VALUES ($1,$2,$3,$4::jsonb) RETURNING *", [randomUUID(), command.serverId, command.commandType, JSON.stringify(command.payload)]);
    return commandFrom(rows[0]);
  }

  async recordBridgeEvent(event: NewBridgeEvent): Promise<{ event: BridgeEventRecord; created: boolean }> {
    const insert = await this.pool.query(
      `INSERT INTO bridge_events (id, server_id, event_id, event_type, occurred_at, world_name, minecraft_uuid, minecraft_name, content, details)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb)
       ON CONFLICT (server_id, event_id) DO NOTHING RETURNING *`,
      [randomUUID(), event.serverId, event.eventId, event.eventType, event.occurredAt, event.worldName, event.minecraftUuid, event.minecraftName, event.content, JSON.stringify(event.details)]
    );
    if (insert.rows[0]) return { event: bridgeEventFrom(insert.rows[0]), created: true };
    const existing = await this.pool.query("SELECT * FROM bridge_events WHERE server_id = $1 AND event_id = $2", [event.serverId, event.eventId]);
    if (!existing.rows[0]) throw new Error("Bridge event idempotency lookup failed");
    return { event: bridgeEventFrom(existing.rows[0]), created: false };
  }

  async listBridgeEvents(after: string | null, limit: number): Promise<BridgeEventRecord[]> {
    const { rows } = await this.pool.query(
      "SELECT * FROM bridge_events WHERE ($1::timestamptz IS NULL OR received_at > $1::timestamptz) ORDER BY received_at ASC, id ASC LIMIT $2",
      [after, limit]
    );
    return rows.map(bridgeEventFrom);
  }

  async listQueuedChatMessages(serverId: string, limit: number): Promise<ChatQueueMessage[]> {
    const { rows } = await this.pool.query(
      "SELECT * FROM chat_relay_queue WHERE server_id = $1 AND status = 'queued' ORDER BY created_at ASC, id ASC LIMIT $2",
      [serverId, limit]
    );
    return rows.map(chatMessageFrom);
  }

  async acknowledgeChatMessage(serverId: string, id: string, status: "delivered" | "rejected", detail: string): Promise<ChatQueueMessage | null> {
    const { rows } = await this.pool.query(
      `UPDATE chat_relay_queue
       SET status = $3, delivery_attempts = delivery_attempts + 1, last_attempt_at = NOW(), acknowledged_at = NOW(),
           acknowledgement_detail = $4, dead_lettered_at = CASE WHEN $3 = 'rejected' THEN NOW() ELSE NULL END
       WHERE id = $1 AND server_id = $2 AND status = 'queued' RETURNING *`,
      [id, serverId, status, detail]
    );
    return rows[0] ? chatMessageFrom(rows[0]) : null;
  }

  async queueChatMessage(message: NewChatQueueMessage): Promise<{ message: ChatQueueMessage; created: boolean }> {
    const insert = await this.pool.query(
      `INSERT INTO chat_relay_queue (id, server_id, idempotency_key, discord_message_id, discord_author_id, display_name, target_world, content)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
       ON CONFLICT DO NOTHING RETURNING *`,
      [randomUUID(), message.serverId, message.idempotencyKey, message.discordMessageId, message.discordAuthorId, message.displayName, message.targetWorld, message.content]
    );
    if (insert.rows[0]) return { message: chatMessageFrom(insert.rows[0]), created: true };
    const existing = await this.pool.query(
      `SELECT * FROM chat_relay_queue WHERE server_id = $1 AND (($2::text IS NOT NULL AND idempotency_key = $2) OR ($3::text IS NOT NULL AND discord_message_id = $3)) ORDER BY created_at ASC LIMIT 1`,
      [message.serverId, message.idempotencyKey, message.discordMessageId]
    );
    if (!existing.rows[0]) throw new Error("Chat queue idempotency conflict could not be resolved");
    return { message: chatMessageFrom(existing.rows[0]), created: false };
  }

}

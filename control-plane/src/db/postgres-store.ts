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


  async auctionRequest(operation: string, payload: Record<string, unknown>): Promise<Record<string, unknown>> {
    const uuid = (name: string) => {
      const value = String(payload[name] ?? "");
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) throw new Error(`${name} must be a UUID`);
      return value;
    };
    const amount = (name: string) => {
      const value = Number(payload[name]);
      if (!Number.isFinite(value) || value < 0) throw new Error(`${name} must be a non-negative number`);
      return value;
    };
    const requireLinked = async (minecraftUuid: string) => {
      const linked = await this.pool.query("SELECT 1 FROM player_links WHERE minecraft_uuid=$1 LIMIT 1", [minecraftUuid]);
      if (!linked.rowCount) throw new Error("This Minecraft account is not linked to the platform.");
    };
    const listingProjection = (row: DbRow) => ({
      id: String(row.id), itemName: String(row.item_name), quantity: Number(row.quantity),
      type: String(row.listing_type), price: Number(row.buy_now_price ?? row.current_bid ?? row.starting_price),
      status: String(row.status), expiresAt: timestamp(row.expires_at)
    });

    if (operation === "status") {
      const { rows } = await this.pool.query("SELECT to_regclass('public.auction_listings') listings, to_regclass('public.auction_bids') bids, to_regclass('public.auction_deliveries') deliveries");
      const ready = Boolean(rows[0]?.listings && rows[0]?.bids && rows[0]?.deliveries);
      return { connected: true, schemaReady: ready, service: "control-plane", storage: "postgres" };
    }
    if (operation === "browse") {
      const query = String(payload.query ?? "").trim().toLowerCase();
      const limit = Math.max(1, Math.min(Number(payload.limit ?? 25), 100));
      const { rows } = await this.pool.query(
        `SELECT * FROM auction_listings WHERE status='ACTIVE' AND expires_at>NOW()
         AND ($1='' OR lower(item_name) LIKE $2) ORDER BY created_at DESC LIMIT $3`,
        [query, `%${query}%`, limit]);
      return { auctionListings: rows.map(listingProjection) };
    }
    if (operation === "mine") {
      const minecraftUuid=uuid("minecraftUuid"); await requireLinked(minecraftUuid);
      const { rows } = await this.pool.query("SELECT * FROM auction_listings WHERE seller_minecraft_uuid=$1 AND status IN ('ACTIVE','RESERVED') AND expires_at>NOW() ORDER BY created_at DESC LIMIT 100", [minecraftUuid]);
      return { auctionListings: rows.map(listingProjection) };
    }
    if (operation === "my-bids") {
      const minecraftUuid=uuid("minecraftUuid"); await requireLinked(minecraftUuid);
      const { rows } = await this.pool.query(
        `SELECT DISTINCT ON (l.id) l.* FROM auction_listings l
         JOIN auction_bids b ON b.listing_id=l.id
         WHERE b.bidder_minecraft_uuid=$1 AND l.status IN ('ACTIVE','RESERVED') AND l.expires_at>NOW()
         ORDER BY l.id,b.created_at DESC LIMIT 100`, [minecraftUuid]);
      return { auctionListings: rows.map(listingProjection) };
    }
    if (operation === "create-fixed") {
      const minecraftUuid=uuid("minecraftUuid"); await requireLinked(minecraftUuid);
      const id=randomUUID(); const price=amount("price");
      const itemPayload=String(payload.itemPayload ?? ""); const itemName=String(payload.itemName ?? "").slice(0,160);
      const quantity=Math.max(1,Math.min(Number(payload.quantity ?? 1),64));
      const expiresAt=new Date(String(payload.expiresAt ?? ""));
      if (!itemPayload || !itemName || !Number.isFinite(expiresAt.getTime()) || price < 1) throw new Error("Invalid auction listing");
      await this.pool.query(`INSERT INTO auction_listings
        (id,seller_minecraft_uuid,item_payload,item_name,quantity,listing_type,starting_price,buy_now_price,status,expires_at)
        VALUES($1,$2,$3,$4,$5,'FIXED',$6,$6,'ACTIVE',$7)`,
        [id,minecraftUuid,itemPayload,itemName,quantity,price,expiresAt.toISOString()]);
      return { id };
    }
    if (operation === "cancel") {
      const minecraftUuid=uuid("minecraftUuid"), listingId=uuid("listingId"); await requireLinked(minecraftUuid);
      const client=await this.pool.connect();
      try {
        await client.query("BEGIN");
        const found=await client.query("SELECT item_payload,status FROM auction_listings WHERE id=$1 AND seller_minecraft_uuid=$2 FOR UPDATE",[listingId,minecraftUuid]);
        if(!found.rowCount || found.rows[0].status!=="ACTIVE") throw new Error("Listing is not active or does not belong to you.");
        await client.query("UPDATE auction_listings SET status='CANCELLED',version=version+1 WHERE id=$1",[listingId]);
        await client.query("INSERT INTO auction_deliveries(id,minecraft_uuid,listing_id,delivery_type,item_payload,status) VALUES($1,$2,$3,'ITEM',$4,'PENDING')",[randomUUID(),minecraftUuid,listingId,found.rows[0].item_payload]);
        await client.query("COMMIT"); return { message:"Listing cancelled. Use Collect to receive the item." };
      } catch(e){ await client.query("ROLLBACK"); throw e; } finally { client.release(); }
    }
    if (operation === "reserve-purchase") {
      const buyer=uuid("minecraftUuid"), listingId=uuid("listingId"); await requireLinked(buyer);
      const client=await this.pool.connect();
      try {
        await client.query("BEGIN");
        const r=await client.query("SELECT * FROM auction_listings WHERE id=$1 FOR UPDATE",[listingId]);
        const row=r.rows[0]; if(!row || row.status!=="ACTIVE" || row.listing_type!=="FIXED") throw new Error("Listing is not available.");
        if(String(row.seller_minecraft_uuid)===buyer) throw new Error("You cannot buy your own listing.");
        await client.query("UPDATE auction_listings SET status='RESERVED',version=version+1 WHERE id=$1",[listingId]);
        await client.query("COMMIT");
        return { listingId, sellerMinecraftUuid:String(row.seller_minecraft_uuid), itemPayload:String(row.item_payload), price:Number(row.buy_now_price) };
      } catch(e){await client.query("ROLLBACK");throw e;} finally{client.release();}
    }
    if (operation === "release-purchase") {
      const listingId=uuid("listingId");
      await this.pool.query("UPDATE auction_listings SET status='ACTIVE',version=version+1 WHERE id=$1 AND status='RESERVED'",[listingId]);
      return { released:true };
    }
    if (operation === "complete-purchase") {
      const buyer=uuid("minecraftUuid"), listingId=uuid("listingId"); await requireLinked(buyer);
      const client=await this.pool.connect();
      try {
        await client.query("BEGIN");
        const r=await client.query("SELECT seller_minecraft_uuid,item_payload,buy_now_price,status FROM auction_listings WHERE id=$1 FOR UPDATE",[listingId]);
        const row=r.rows[0]; if(!row || row.status!=="RESERVED") throw new Error("Purchase reservation is no longer active.");
        await client.query("UPDATE auction_listings SET status='SOLD',sold_at=NOW(),version=version+1 WHERE id=$1",[listingId]);
        await client.query("INSERT INTO auction_deliveries(id,minecraft_uuid,listing_id,delivery_type,item_payload,status) VALUES($1,$2,$3,'ITEM',$4,'PENDING')",[randomUUID(),buyer,listingId,row.item_payload]);
        await client.query("INSERT INTO auction_deliveries(id,minecraft_uuid,listing_id,delivery_type,money_amount,status) VALUES($1,$2,$3,'MONEY',$4,'PENDING')",[randomUUID(),row.seller_minecraft_uuid,listingId,row.buy_now_price]);
        await client.query("COMMIT"); return { completed:true };
      } catch(e){await client.query("ROLLBACK");throw e;} finally{client.release();}
    }
    if (operation === "place-bid") {
      const bidder=uuid("minecraftUuid"), listingId=uuid("listingId"), bid=amount("amount"); await requireLinked(bidder);
      const client=await this.pool.connect();
      try {
        await client.query("BEGIN");
        const r=await client.query("SELECT * FROM auction_listings WHERE id=$1 FOR UPDATE",[listingId]); const row=r.rows[0];
        if(!row || row.status!=="ACTIVE") throw new Error("Listing is not active.");
        if(String(row.seller_minecraft_uuid)===bidder) throw new Error("You cannot bid on your own listing.");
        const minimum=Math.max(Number(row.starting_price),Number(row.current_bid??0)+1); if(bid<minimum) throw new Error(`Minimum bid is ${minimum}.`);
        const previousBidder=row.current_bidder_minecraft_uuid?String(row.current_bidder_minecraft_uuid):null;
        const previousBid=Number(row.current_bid??0);
        await client.query("UPDATE auction_listings SET current_bid=$2,current_bidder_minecraft_uuid=$3,version=version+1 WHERE id=$1",[listingId,bid,bidder]);
        await client.query("INSERT INTO auction_bids(id,listing_id,bidder_minecraft_uuid,amount,status) VALUES($1,$2,$3,$4,'ACTIVE')",[randomUUID(),listingId,bidder,bid]);
        if(previousBidder && previousBid>0) await client.query("INSERT INTO auction_deliveries(id,minecraft_uuid,listing_id,delivery_type,money_amount,status) VALUES($1,$2,$3,'MONEY',$4,'PENDING')",[randomUUID(),previousBidder,listingId,previousBid]);
        await client.query("COMMIT"); return { previousBidder, previousBid };
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    if (operation === "revert-bid") {
      const bidder=uuid("minecraftUuid"), listingId=uuid("listingId"); const bid=amount("amount");
      const previousBidder=payload.previousBidder ? String(payload.previousBidder) : null; const previousBid=Number(payload.previousBid ?? 0);
      const client=await this.pool.connect();
      try{
        await client.query("BEGIN");
        const current=await client.query("SELECT current_bid,current_bidder_minecraft_uuid FROM auction_listings WHERE id=$1 FOR UPDATE",[listingId]);
        const row=current.rows[0];
        if(row && String(row.current_bidder_minecraft_uuid)===bidder && Number(row.current_bid)===bid){
          await client.query("UPDATE auction_listings SET current_bid=$2,current_bidder_minecraft_uuid=$3,version=version+1 WHERE id=$1",[listingId,previousBid>0?previousBid:null,previousBidder]);
          await client.query("UPDATE auction_bids SET status='REVERTED' WHERE listing_id=$1 AND bidder_minecraft_uuid=$2 AND amount=$3 AND status='ACTIVE'",[listingId,bidder,bid]);
          if(previousBidder && previousBid>0) await client.query("DELETE FROM auction_deliveries WHERE listing_id=$1 AND minecraft_uuid=$2 AND delivery_type='MONEY' AND money_amount=$3 AND status='PENDING'",[listingId,previousBidder,previousBid]);
        }
        await client.query("COMMIT"); return { reverted:true };
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    if (operation === "claim") {
      const minecraftUuid=uuid("minecraftUuid"); await requireLinked(minecraftUuid);
      const client=await this.pool.connect();
      try{
        await client.query("BEGIN");
        const r=await client.query("SELECT id,delivery_type,item_payload,money_amount FROM auction_deliveries WHERE minecraft_uuid=$1 AND status='PENDING' ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 54",[minecraftUuid]);
        const ids=r.rows.map((x)=>x.id); if(ids.length) await client.query("UPDATE auction_deliveries SET status='CLAIMING' WHERE id=ANY($1::uuid[])",[ids]);
        await client.query("COMMIT");
        return { claimIds:ids.map(String), items:r.rows.filter(x=>x.delivery_type==="ITEM").map(x=>String(x.item_payload)), money:r.rows.filter(x=>x.delivery_type==="MONEY").reduce((n,x)=>n+Number(x.money_amount??0),0) };
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    if (operation === "finish-claim") {
      const minecraftUuid=uuid("minecraftUuid"); const delivered=Boolean(payload.delivered);
      await this.pool.query("UPDATE auction_deliveries SET status=$2,claimed_at=CASE WHEN $2='DELIVERED' THEN NOW() ELSE NULL END WHERE minecraft_uuid=$1 AND status='CLAIMING'",[minecraftUuid,delivered?"DELIVERED":"PENDING"]);
      return { finished:true };
    }
    throw new Error(`Unsupported auction operation: ${operation}`);
  }

  async guildPointsRequest(operation: string, payload: Record<string, unknown>): Promise<Record<string, unknown>> {
    const player = String(payload.minecraftUuid ?? "");
    const target = payload.targetMinecraftUuid ? String(payload.targetMinecraftUuid) : null;
    const validUuid=(v:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(v);
    if (operation !== "guild-top" && operation !== "points-top" && !validUuid(player)) throw new Error("minecraftUuid must be a UUID");
    const requireLinked=async(v:string)=>{const r=await this.pool.query("SELECT 1 FROM player_links WHERE minecraft_uuid=$1 LIMIT 1",[v]);if(!r.rowCount)throw new Error("This Minecraft account is not linked to the platform.");};
    if(player) await requireLinked(player);
    const currencies=["KAIRU_POINTS","NEXUS_POINTS","HARDCORE_POINTS","BUILD_POINTS","EVENT_POINTS","SEASON_POINTS"];
    const guildSummary=async(v:string)=>{
      const top=await this.pool.query(`SELECT g.id,g.name,g.tag,g.points,count(m.minecraft_uuid)::int members
        FROM platform_guilds g LEFT JOIN platform_guild_members m ON m.guild_id=g.id
        GROUP BY g.id ORDER BY g.points DESC,g.created_at ASC LIMIT 20`);
      const out:any={creationCurrency:"KAIRU_POINTS",creationCost:500,leaderboard:top.rows.map((r,i)=>({rank:i+1,name:r.name,tag:r.tag,points:Number(r.points),members:Number(r.members)})),inGuild:false};
      const own=await this.pool.query(`SELECT g.*,m.rank FROM platform_guild_members m JOIN platform_guilds g ON g.id=m.guild_id WHERE m.minecraft_uuid=$1`,[v]);
      if(!own.rowCount)return out;
      const g=own.rows[0];out.inGuild=true;out.guildId=String(g.id);out.name=g.name;out.tag=g.tag;out.description=g.description;out.points=Number(g.points);out.rank=g.rank;
      const members=await this.pool.query("SELECT minecraft_uuid,rank FROM platform_guild_members WHERE guild_id=$1 ORDER BY CASE rank WHEN 'LEADER' THEN 1 WHEN 'OFFICER' THEN 2 WHEN 'MEMBER' THEN 3 ELSE 4 END,joined_at",[g.id]);
      out.members=members.rows.map(r=>({id:String(r.minecraft_uuid),rank:r.rank}));return out;
    };
    const pointAdjust=async(client:any,v:string,currency:string,delta:number,source:string,reason:string,actor:string|null)=>{
      const cur=await client.query("SELECT balance FROM platform_point_accounts WHERE minecraft_uuid=$1 AND currency_id=$2 FOR UPDATE",[v,currency]);
      const before=cur.rowCount?Number(cur.rows[0].balance):0;const after=before+delta;if(after<0)throw new Error(`Not enough ${currency}.`);
      await client.query(`INSERT INTO platform_point_accounts(minecraft_uuid,currency_id,balance) VALUES($1,$2,$3)
        ON CONFLICT(minecraft_uuid,currency_id) DO UPDATE SET balance=EXCLUDED.balance,version=platform_point_accounts.version+1,updated_at=NOW()`,[v,currency,after]);
      await client.query(`INSERT INTO platform_point_transactions(id,minecraft_uuid,currency_id,amount,balance_before,balance_after,source,reason,actor_minecraft_uuid)
        VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)`,[randomUUID(),v,currency,delta,before,after,source,reason,actor]);
      return after;
    };

    if(operation==="guild-summary") return guildSummary(player);
    if(operation==="guild-top") return guildSummary(player||"00000000-0000-4000-8000-000000000000");
    if(operation==="guild-invites"){
      const r=await this.pool.query(`SELECT i.guild_id,g.name,g.tag,i.expires_at FROM platform_guild_invites i JOIN platform_guilds g ON g.id=i.guild_id
        WHERE i.target_minecraft_uuid=$1 AND i.expires_at>NOW() ORDER BY i.created_at DESC`,[player]);
      return {invites:r.rows.map(x=>({guildId:String(x.guild_id),name:x.name,tag:x.tag,expiresAt:timestamp(x.expires_at) }))};
    }
    if(operation==="guild-create"){
      const name=String(payload.name??"").trim(),tag=String(payload.tag??"").trim().toUpperCase(),description=String(payload.description??"").trim();
      if(name.length<3||name.length>32||tag.length<2||tag.length>8)throw new Error("Guild name must be 3-32 characters and tag 2-8 characters.");
      const client=await this.pool.connect();try{await client.query("BEGIN");
        if((await client.query("SELECT 1 FROM platform_guild_members WHERE minecraft_uuid=$1",[player])).rowCount)throw new Error("You are already in a guild.");
        await pointAdjust(client,player,"KAIRU_POINTS",-500,"GUILD","Guild creation",player);
        const id=randomUUID();await client.query("INSERT INTO platform_guilds(id,name,tag,description,owner_minecraft_uuid) VALUES($1,$2,$3,$4,$5)",[id,name,tag,description,player]);
        await client.query("INSERT INTO platform_guild_members(guild_id,minecraft_uuid,rank) VALUES($1,$2,'LEADER')",[id,player]);
        await client.query("COMMIT");return {...await guildSummary(player),message:`Created ${name} [${tag}] for 500 KAIRU_POINTS.`};
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    if(operation==="guild-action"){
      const action=String(payload.action??""); const client=await this.pool.connect();try{await client.query("BEGIN");
        const self=await client.query(`SELECT m.guild_id,m.rank,g.owner_minecraft_uuid FROM platform_guild_members m JOIN platform_guilds g ON g.id=m.guild_id WHERE m.minecraft_uuid=$1 FOR UPDATE OF g`,[player]);
        if(action==="guild-accept"){
          if(!target||!validUuid(target))throw new Error("Guild id is required.");
          if(self.rowCount)throw new Error("Leave your current guild first.");
          const inv=await client.query("SELECT 1 FROM platform_guild_invites WHERE guild_id=$1 AND target_minecraft_uuid=$2 AND expires_at>NOW()",[target,player]);
          if(!inv.rowCount)throw new Error("That invitation is unavailable or expired.");
          await client.query("INSERT INTO platform_guild_members(guild_id,minecraft_uuid,rank) VALUES($1,$2,'RECRUIT')",[target,player]);
          await client.query("DELETE FROM platform_guild_invites WHERE guild_id=$1 AND target_minecraft_uuid=$2",[target,player]);
        } else {
          if(!self.rowCount)throw new Error("You are not in a guild."); const guildId=self.rows[0].guild_id,rank=String(self.rows[0].rank);
          if(action==="guild-leave"){if(rank==="LEADER")throw new Error("Transfer ownership or disband the guild before leaving.");await client.query("DELETE FROM platform_guild_members WHERE minecraft_uuid=$1",[player]);}
          else {
            if(!target||!validUuid(target))throw new Error("Select a player."); await requireLinked(target);
            if(action==="guild-invite"){if(!["LEADER","OFFICER"].includes(rank))throw new Error("Only leaders and officers can invite.");await client.query(`INSERT INTO platform_guild_invites(id,guild_id,target_minecraft_uuid,invited_by_minecraft_uuid,expires_at)
              VALUES($1,$2,$3,$4,NOW()+INTERVAL '24 hours') ON CONFLICT(guild_id,target_minecraft_uuid) DO UPDATE SET invited_by_minecraft_uuid=EXCLUDED.invited_by_minecraft_uuid,expires_at=EXCLUDED.expires_at`,[randomUUID(),guildId,target,player]);}
            else if(action==="guild-kick"){if(!["LEADER","OFFICER"].includes(rank))throw new Error("Insufficient guild rank.");await client.query("DELETE FROM platform_guild_members WHERE guild_id=$1 AND minecraft_uuid=$2 AND rank<>'LEADER'",[guildId,target]);}
            else if(action==="guild-promote"||action==="guild-demote"){if(rank!=="LEADER")throw new Error("Only the leader can change ranks.");const row=await client.query("SELECT rank FROM platform_guild_members WHERE guild_id=$1 AND minecraft_uuid=$2",[guildId,target]);if(!row.rowCount)throw new Error("Player is not in your guild.");const ranks=["RECRUIT","MEMBER","OFFICER"];let i=ranks.indexOf(row.rows[0].rank);i+=action==="guild-promote"?1:-1;if(i<0||i>=ranks.length)throw new Error("That rank cannot be changed further.");await client.query("UPDATE platform_guild_members SET rank=$3 WHERE guild_id=$1 AND minecraft_uuid=$2",[guildId,target,ranks[i]]);}
            else if(action==="guild-transfer-confirm"){if(rank!=="LEADER")throw new Error("Only the leader can transfer ownership.");const member=await client.query("SELECT 1 FROM platform_guild_members WHERE guild_id=$1 AND minecraft_uuid=$2",[guildId,target]);if(!member.rowCount)throw new Error("Successor is not in your guild.");await client.query("UPDATE platform_guild_members SET rank='OFFICER' WHERE guild_id=$1 AND minecraft_uuid=$2",[guildId,player]);await client.query("UPDATE platform_guild_members SET rank='LEADER' WHERE guild_id=$1 AND minecraft_uuid=$2",[guildId,target]);await client.query("UPDATE platform_guilds SET owner_minecraft_uuid=$2,version=version+1 WHERE id=$1",[guildId,target]);}
            else if(action==="guild-transfer-arm"){/* client confirmation remains server-side; no mutation */}
            else throw new Error("Unknown guild action.");
          }
        }
        await client.query("COMMIT");return {...await guildSummary(player),message:action==="guild-transfer-arm"?"Ownership transfer is armed. Confirm within 30 seconds.":"Guild updated."};
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    if(operation==="points-summary"){
      const r=await this.pool.query("SELECT currency_id,balance FROM platform_point_accounts WHERE minecraft_uuid=$1",[player]);const balances:Record<string,number>={};for(const x of r.rows)balances[String(x.currency_id)]=Number(x.balance);
      return {balances:currencies.map(currency=>({currency,balance:balances[currency]??0}))};
    }
    if(operation==="points-history"){
      const currency=String(payload.currency??"KAIRU_POINTS");const limit=Math.max(1,Math.min(Number(payload.limit??20),100));
      const r=await this.pool.query("SELECT amount,balance_after,source,reason,occurred_at FROM platform_point_transactions WHERE minecraft_uuid=$1 AND currency_id=$2 ORDER BY occurred_at DESC LIMIT $3",[player,currency,limit]);
      return {currency,history:r.rows.map(x=>({amount:Number(x.amount),balance:Number(x.balance_after),source:x.source,reason:x.reason,occurredAt:timestamp(x.occurred_at)}))};
    }
    if(operation==="points-top"){
      const currency=String(payload.currency??"KAIRU_POINTS"),limit=Math.max(1,Math.min(Number(payload.limit??20),100));
      const r=await this.pool.query("SELECT minecraft_uuid,balance FROM platform_point_accounts WHERE currency_id=$1 ORDER BY balance DESC,minecraft_uuid LIMIT $2",[currency,limit]);
      return {currency,leaderboard:r.rows.map((x,i)=>({rank:i+1,playerId:String(x.minecraft_uuid),balance:Number(x.balance)}))};
    }
    if(operation==="points-adjust"){
      const actor=player,who=String(payload.targetMinecraftUuid??player),currency=String(payload.currency??"KAIRU_POINTS"),mode=String(payload.mode??"add"),value=Math.max(0,Number(payload.amount??0)),reason=String(payload.reason??"Administrative adjustment").slice(0,256);
      if(!validUuid(who))throw new Error("Target must be a UUID.");await requireLinked(who);const client=await this.pool.connect();try{await client.query("BEGIN");
        let delta=value;if(mode==="remove")delta=-value;if(mode==="set"){const cur=await client.query("SELECT balance FROM platform_point_accounts WHERE minecraft_uuid=$1 AND currency_id=$2 FOR UPDATE",[who,currency]);delta=value-(cur.rowCount?Number(cur.rows[0].balance):0);}
        const balance=await pointAdjust(client,who,currency,delta,"ADMIN",reason,actor);await client.query("COMMIT");return {message:`${currency} balance is now ${balance}.`,balance};
      }catch(e){await client.query("ROLLBACK");throw e;}finally{client.release();}
    }
    throw new Error(`Unsupported guild/points operation: ${operation}`);
  }
}

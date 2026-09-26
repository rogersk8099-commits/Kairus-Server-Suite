import { randomUUID } from "node:crypto";
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

type StoredLinkCode = { discordUserId: string; expiresAt: Date; consumedAt: Date | null };
type MemorySeed = { events?: EventRecord[]; streams?: StreamRecord[]; tiers?: MembershipTier[] };

export class MemoryStore implements ControlPlaneStore {
  public readonly kind = "memory" as const;
  private heartbeat: ServerHeartbeat | null = null;
  private readonly snapshots = new Map<string, PlayerSnapshot>();
  private readonly codes = new Map<string, StoredLinkCode>();
  private readonly linksByDiscord = new Map<string, PlayerLink[]>();
  private readonly linksByMinecraft = new Map<string, PlayerLink>();
  private readonly linksByXuid = new Map<string, PlayerLink>();
  private readonly commands = new Map<string, PluginCommand>();
  private readonly bridgeEvents = new Map<string, BridgeEventRecord>();
  private readonly chatMessages = new Map<string, ChatQueueMessage>();
  private readonly chatIdempotencyKeys = new Map<string, string>();
  private readonly chatDiscordMessageIds = new Map<string, string>();
  private readonly events: EventRecord[];
  private readonly streams: StreamRecord[];
  private readonly tiers: MembershipTier[];

  constructor(seed?: MemorySeed) {
    this.events = seed?.events ?? [];
    this.streams = seed?.streams ?? [];
    this.tiers = seed?.tiers ?? [];
  }

  async close(): Promise<void> {}

  async getLatestHeartbeat(): Promise<ServerHeartbeat | null> {
    return this.heartbeat ? { ...this.heartbeat, worlds: [...this.heartbeat.worlds], worldPlayers: this.heartbeat.worldPlayers.map((world) => ({ ...world })), players: [...this.heartbeat.players] } : null;
  }

  async recordHeartbeat(heartbeat: Omit<ServerHeartbeat, "receivedAt">): Promise<ServerHeartbeat> {
    this.heartbeat = { ...heartbeat, worlds: [...heartbeat.worlds], worldPlayers: heartbeat.worldPlayers.map((world) => ({ ...world })), players: [...heartbeat.players], receivedAt: new Date().toISOString() };
    return { ...this.heartbeat, worlds: [...this.heartbeat.worlds], worldPlayers: this.heartbeat.worldPlayers.map((world) => ({ ...world })), players: [...this.heartbeat.players] };
  }

  async recordPlayerSnapshot(snapshot: Omit<PlayerSnapshot, "updatedAt">): Promise<PlayerSnapshot> {
    const record = { ...snapshot, updatedAt: new Date().toISOString() };
    this.snapshots.set(record.minecraftUuid, record);
    return { ...record };
  }

  async getPlayerSnapshot(minecraftUuid: string): Promise<PlayerSnapshot | null> {
    const snapshot = this.snapshots.get(minecraftUuid);
    return snapshot ? { ...snapshot } : null;
  }

  async listPlayerSnapshots(limit: number): Promise<PlayerSnapshot[]> {
    return [...this.snapshots.values()].sort((a, b) => b.playtimeSeconds - a.playtimeSeconds).slice(0, limit).map((snapshot) => ({ ...snapshot }));
  }

  async createLinkCode(discordUserId: string, codeHash: string, expiresAt: Date): Promise<void> {
    this.codes.set(codeHash, { discordUserId, expiresAt, consumedAt: null });
  }

  async completeLinkCode(codeHash: string, identity: LinkIdentity): Promise<LinkCodeResult> {
    const code = this.codes.get(codeHash);
    if (!code || code.consumedAt || code.expiresAt <= new Date()) return { ok: false, reason: "invalid_or_expired" };
    if (this.linksByMinecraft.has(identity.minecraftUuid) || (identity.bedrockXuid && this.linksByXuid.has(identity.bedrockXuid))) {
      return { ok: false, reason: "identity_already_linked" };
    }
    const link: PlayerLink = {
      discordUserId: code.discordUserId,
      minecraftUuid: identity.minecraftUuid,
      javaUsername: identity.javaUsername,
      bedrockXuid: identity.bedrockXuid ?? null,
      isPrimary: (this.linksByDiscord.get(code.discordUserId)?.length ?? 0) === 0,
      linkedAt: new Date().toISOString()
    };
    code.consumedAt = new Date();
    this.linksByDiscord.set(link.discordUserId, [...(this.linksByDiscord.get(link.discordUserId) ?? []), link]);
    this.linksByMinecraft.set(link.minecraftUuid, link);
    if (link.bedrockXuid) this.linksByXuid.set(link.bedrockXuid, link);
    return { ok: true, link: { ...link } };
  }

  async getLinkByDiscordUser(discordUserId: string): Promise<PlayerLink | null> {
    const link = (this.linksByDiscord.get(discordUserId) ?? []).find((item) => item.isPrimary) ?? this.linksByDiscord.get(discordUserId)?.[0];
    return link ? { ...link } : null;
  }

  async getLinksByDiscordUser(discordUserId: string): Promise<PlayerLink[]> { return (this.linksByDiscord.get(discordUserId) ?? []).map((link) => ({ ...link })); }
  async setPrimaryMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean> { const links = this.linksByDiscord.get(discordUserId) ?? []; const selected = links.find((link) => link.minecraftUuid === minecraftUuid); if (!selected) return false; links.forEach((link) => { link.isPrimary = link === selected; }); return true; }
  async unlinkMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean> { const links = this.linksByDiscord.get(discordUserId) ?? []; const index = links.findIndex((link) => link.minecraftUuid === minecraftUuid); if (index < 0) return false; const [removed] = links.splice(index, 1); if (!links.some((link) => link.isPrimary) && links[0]) links[0].isPrimary = true; if (links.length) this.linksByDiscord.set(discordUserId, links); else this.linksByDiscord.delete(discordUserId); this.linksByMinecraft.delete(removed.minecraftUuid); if (removed.bedrockXuid) this.linksByXuid.delete(removed.bedrockXuid); return true; }

  async unlinkDiscordUser(discordUserId: string): Promise<boolean> {
    const links = this.linksByDiscord.get(discordUserId) ?? [];
    const removed = await Promise.all(links.map((link) => this.unlinkMinecraftAccount(discordUserId, link.minecraftUuid)));
    return removed.some(Boolean);
  }

  async listEvents(limit: number): Promise<EventRecord[]> {
    return this.events.filter((event) => new Date(event.startsAt) >= new Date()).sort((a, b) => a.startsAt.localeCompare(b.startsAt)).slice(0, limit).map((event) => ({ ...event }));
  }

  async listStreams(): Promise<StreamRecord[]> {
    return this.streams.filter((stream) => stream.live).map((stream) => ({ ...stream }));
  }

  async listMembershipTiers(): Promise<MembershipTier[]> {
    return this.tiers.map((tier) => ({ ...tier, benefits: [...tier.benefits] }));
  }

  async listQueuedPluginCommands(serverId: string): Promise<PluginCommand[]> {
    return [...this.commands.values()].filter((command) => command.serverId === serverId && command.status === "queued").sort((a, b) => a.createdAt.localeCompare(b.createdAt)).map((command) => ({ ...command, payload: { ...command.payload } }));
  }

  async acknowledgePluginCommand(serverId: string, id: string, status: "completed" | "failed", errorMessage?: string): Promise<PluginCommand | null> {
    const command = this.commands.get(id);
    if (!command || command.serverId !== serverId || command.status !== "queued") return null;
    command.status = status;
    command.errorMessage = errorMessage ?? null;
    command.acknowledgedAt = new Date().toISOString();
    return { ...command, payload: { ...command.payload } };
  }

  async queuePluginCommand(command: NewPluginCommand): Promise<PluginCommand> {
    const record: PluginCommand = { id: randomUUID(), ...command, payload: { ...command.payload }, status: "queued", errorMessage: null, createdAt: new Date().toISOString(), acknowledgedAt: null };
    this.commands.set(record.id, record);
    return { ...record, payload: { ...record.payload } };
  }

  async recordBridgeEvent(event: NewBridgeEvent): Promise<{ event: BridgeEventRecord; created: boolean }> {
    const key = `${event.serverId}\0${event.eventId}`;
    const existing = this.bridgeEvents.get(key);
    if (existing) return { event: { ...existing, details: { ...existing.details } }, created: false };
    const record: BridgeEventRecord = { id: randomUUID(), ...event, details: { ...event.details }, receivedAt: new Date().toISOString() };
    this.bridgeEvents.set(key, record);
    return { event: { ...record, details: { ...record.details } }, created: true };
  }

  async listBridgeEvents(after: string | null, limit: number): Promise<BridgeEventRecord[]> {
    return [...this.bridgeEvents.values()]
      .filter((event) => after === null || event.receivedAt > after)
      .sort((a, b) => a.receivedAt.localeCompare(b.receivedAt) || a.id.localeCompare(b.id))
      .slice(0, limit)
      .map((event) => ({ ...event, details: { ...event.details } }));
  }

  async listQueuedChatMessages(serverId: string, limit: number): Promise<ChatQueueMessage[]> {
    return [...this.chatMessages.values()]
      .filter((message) => message.serverId === serverId && message.status === "queued")
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id.localeCompare(b.id))
      .slice(0, limit)
      .map((message) => ({ ...message }));
  }

  async acknowledgeChatMessage(serverId: string, id: string, status: "delivered" | "rejected", detail: string): Promise<ChatQueueMessage | null> {
    const message = this.chatMessages.get(id);
    if (!message || message.serverId !== serverId || message.status !== "queued") return null;
    const now = new Date().toISOString();
    message.status = status;
    message.deliveryAttempts += 1;
    message.lastAttemptAt = now;
    message.acknowledgedAt = now;
    message.acknowledgementDetail = detail;
    message.deadLetteredAt = status === "rejected" ? now : null;
    return { ...message };
  }

  async queueChatMessage(message: NewChatQueueMessage): Promise<{ message: ChatQueueMessage; created: boolean }> {
    const idempotencyLookup = message.idempotencyKey ? `${message.serverId}\0${message.idempotencyKey}` : null;
    const discordLookup = message.discordMessageId ? `${message.serverId}\0${message.discordMessageId}` : null;
    const existingId = (idempotencyLookup && this.chatIdempotencyKeys.get(idempotencyLookup))
      || (discordLookup && this.chatDiscordMessageIds.get(discordLookup));
    if (existingId) return { message: { ...this.chatMessages.get(existingId)! }, created: false };
    const record: ChatQueueMessage = {
      id: randomUUID(),
      ...message,
      status: "queued",
      deliveryAttempts: 0,
      lastAttemptAt: null,
      acknowledgedAt: null,
      acknowledgementDetail: null,
      deadLetteredAt: null,
      createdAt: new Date().toISOString()
    };
    this.chatMessages.set(record.id, record);
    if (idempotencyLookup) this.chatIdempotencyKeys.set(idempotencyLookup, record.id);
    if (discordLookup) this.chatDiscordMessageIds.set(discordLookup, record.id);
    return { message: { ...record }, created: true };
  }


  async auctionRequest(operation: string, _payload: Record<string, unknown>): Promise<Record<string, unknown>> {
    if (operation === "status") return { connected: false, schemaReady: false, service: "control-plane", storage: "memory", error: "Auction requires PostgreSQL storage." };
    if (operation === "browse" || operation === "mine") return { auctionListings: [], error: "Auction requires PostgreSQL storage." };
    throw new Error("Auction mutations require PostgreSQL storage");
  }
}

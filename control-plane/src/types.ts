export type ServerHeartbeat = {
  serverId: string;
  online: boolean;
  tps: number;
  playerCount: number;
  worlds: string[];
  /** Registered worlds and their current online population. */
  worldPlayers: Array<{ id: string; name: string; playerCount: number; status: string }>;
  players: string[];
  version: string;
  uptimeSeconds: number;
  receivedAt: string;
};

export type PlayerSnapshot = {
  minecraftUuid: string;
  name: string;
  playtimeSeconds: number;
  blocksBroken: number;
  kills: number;
  deaths: number;
  distanceMeters: number;
  balance: number;
  rankName: string;
  worldName: string | null;
  updatedAt: string;
};

export type PlayerLink = {
  discordUserId: string;
  minecraftUuid: string;
  javaUsername: string;
  bedrockXuid: string | null;
  isPrimary: boolean;
  linkedAt: string;
};

export type PluginCommand = {
  id: string;
  serverId: string;
  commandType: "whitelist" | "notification";
  payload: Record<string, unknown>;
  status: "queued" | "completed" | "failed";
  errorMessage: string | null;
  createdAt: string;
  acknowledgedAt: string | null;
};

export const bridgeEventTypes = [
  "CHAT",
  "PLAYER_JOIN",
  "PLAYER_LEAVE",
  "PLAYER_DEATH",
  "PLAYER_ADVANCEMENT",
  "SERVER_STARTED",
  "SERVER_STOPPING",
  "MAINTENANCE"
] as const;

export type BridgeEventType = (typeof bridgeEventTypes)[number];

export type BridgeEventRecord = {
  id: string;
  serverId: string;
  eventId: string;
  eventType: BridgeEventType;
  occurredAt: string;
  worldName: string | null;
  minecraftUuid: string | null;
  minecraftName: string | null;
  content: string;
  details: Record<string, string>;
  receivedAt: string;
};

export type NewBridgeEvent = Omit<BridgeEventRecord, "id" | "serverId" | "receivedAt"> & { serverId: string };

export type ChatQueueStatus = "queued" | "delivered" | "rejected";

export type ChatQueueMessage = {
  id: string;
  serverId: string;
  idempotencyKey: string | null;
  discordMessageId: string | null;
  discordAuthorId: string | null;
  displayName: string | null;
  targetWorld: string | null;
  content: string;
  status: ChatQueueStatus;
  deliveryAttempts: number;
  lastAttemptAt: string | null;
  acknowledgedAt: string | null;
  acknowledgementDetail: string | null;
  deadLetteredAt: string | null;
  createdAt: string;
};

export type NewChatQueueMessage = Pick<ChatQueueMessage,
  "serverId" | "idempotencyKey" | "discordMessageId" | "discordAuthorId" | "displayName" | "targetWorld" | "content">;

export type EventRecord = {
  id: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string | null;
  location: string | null;
  status: string;
};

export type StreamRecord = {
  id: string;
  channelName: string;
  url: string;
  platform: string;
  title: string;
  viewerCount: number;
  live: boolean;
  startedAt: string | null;
};

export type MembershipTier = {
  slug: string;
  name: string;
  description: string;
  priceMonthlyCents: number;
  benefits: string[];
};

export type LinkCodeResult =
  | { ok: true; link: PlayerLink }
  | { ok: false; reason: "invalid_or_expired" | "identity_already_linked" };

export type LinkCode = {
  code: string;
  expiresAt: Date;
};

export type AppConfig = {
  nodeEnv: "development" | "test" | "production";
  host: string;
  port: number;
  logLevel: string;
  corsOrigins: string[];
  databaseUrl?: string;
  pluginApiKey?: string;
  adminApiKey?: string;
  discordBotToken?: string;
  discordApplicationId?: string;
  discordGuildId?: string;
  membershipRoleMap: Record<string, string>;
  websiteApiSecret?: string;
  sessionSecret?: string;
  websiteUrl?: string;
  discordOAuthClientId?: string;
  discordOAuthClientSecret?: string;
  discordOAuthRedirectUri?: string;
};

export type LinkIdentity = {
  minecraftUuid: string;
  javaUsername: string;
  bedrockXuid?: string;
};

export type NewPluginCommand = {
  serverId: string;
  commandType: "whitelist" | "notification";
  payload: Record<string, unknown>;
};

export interface ControlPlaneStore {
  readonly kind: "memory" | "postgres";
  close(): Promise<void>;
  getLatestHeartbeat(): Promise<ServerHeartbeat | null>;
  recordHeartbeat(heartbeat: Omit<ServerHeartbeat, "receivedAt">): Promise<ServerHeartbeat>;
  recordPlayerSnapshot(snapshot: Omit<PlayerSnapshot, "updatedAt">): Promise<PlayerSnapshot>;
  getPlayerSnapshot(minecraftUuid: string): Promise<PlayerSnapshot | null>;
  listPlayerSnapshots(limit: number): Promise<PlayerSnapshot[]>;
  createLinkCode(discordUserId: string, codeHash: string, expiresAt: Date): Promise<void>;
  completeLinkCode(codeHash: string, identity: LinkIdentity): Promise<LinkCodeResult>;
  getLinkByDiscordUser(discordUserId: string): Promise<PlayerLink | null>;
  getLinksByDiscordUser(discordUserId: string): Promise<PlayerLink[]>;
  setPrimaryMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean>;
  unlinkMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean>;
  unlinkDiscordUser(discordUserId: string): Promise<boolean>;
  listEvents(limit: number): Promise<EventRecord[]>;
  listStreams(): Promise<StreamRecord[]>;
  listMembershipTiers(): Promise<MembershipTier[]>;
  listQueuedPluginCommands(serverId: string): Promise<PluginCommand[]>;
  acknowledgePluginCommand(serverId: string, id: string, status: "completed" | "failed", errorMessage?: string): Promise<PluginCommand | null>;
  queuePluginCommand(command: NewPluginCommand): Promise<PluginCommand>;
  recordBridgeEvent(event: NewBridgeEvent): Promise<{ event: BridgeEventRecord; created: boolean }>;
  listBridgeEvents(after: string | null, limit: number): Promise<BridgeEventRecord[]>;
  listQueuedChatMessages(serverId: string, limit: number): Promise<ChatQueueMessage[]>;
  acknowledgeChatMessage(serverId: string, id: string, status: "delivered" | "rejected", detail: string): Promise<ChatQueueMessage | null>;
  queueChatMessage(message: NewChatQueueMessage): Promise<{ message: ChatQueueMessage; created: boolean }>;
}

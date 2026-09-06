export type ServerHeartbeat = {
  serverId: string;
  online: boolean;
  tps: number;
  playerCount: number;
  worlds: string[];
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
  unlinkDiscordUser(discordUserId: string): Promise<boolean>;
  listEvents(limit: number): Promise<EventRecord[]>;
  listStreams(): Promise<StreamRecord[]>;
  listMembershipTiers(): Promise<MembershipTier[]>;
  listQueuedPluginCommands(serverId: string): Promise<PluginCommand[]>;
  acknowledgePluginCommand(serverId: string, id: string, status: "completed" | "failed", errorMessage?: string): Promise<PluginCommand | null>;
  queuePluginCommand(command: NewPluginCommand): Promise<PluginCommand>;
}

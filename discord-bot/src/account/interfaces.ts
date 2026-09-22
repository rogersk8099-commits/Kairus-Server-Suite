/**
 * Discord-independent contracts used by every command module.
 *
 * The interaction shapes are deliberately compatible with the corresponding
 * discord.js interaction methods. An application can pass discord.js
 * interactions directly or provide an adapter with these methods.
 */

export type DiscordPermission = "Administrator" | "ManageGuild" | "ManageRoles";

export interface PermissionSet {
  has(permission: DiscordPermission): boolean;
}

export interface DiscordUser {
  id: string;
  username: string;
  bot?: boolean;
}

export interface CommandOptions {
  getString(name: string, required?: boolean): string | null;
  getBoolean(name: string, required?: boolean): boolean | null;
  getInteger(name: string, required?: boolean): number | null;
  getUser(name: string, required?: boolean): DiscordUser | null;
}

export interface EmbedField {
  name: string;
  value: string;
  inline?: boolean;
}

export interface EmbedPayload {
  color?: number;
  title?: string;
  description?: string;
  thumbnail?: { url: string };
  url?: string;
  fields?: EmbedField[];
  footer?: { text: string };
  timestamp?: string;
}

export interface ButtonComponent {
  type: 2;
  style: 1 | 2 | 3 | 4 | 5;
  label: string;
  custom_id?: string;
  url?: string;
  disabled?: boolean;
}

export interface ActionRowComponent {
  type: 1;
  components: ButtonComponent[];
}

export interface ReplyPayload {
  content?: string;
  ephemeral?: boolean;
  embeds?: EmbedPayload[];
  components?: ActionRowComponent[];
  /** Explicitly disable mention parsing for every generated command response. */
  allowedMentions?: { parse: [] };
}

export interface CommandInteraction {
  commandName: string;
  user: DiscordUser;
  guildId: string | null;
  memberPermissions: PermissionSet | null;
  options: CommandOptions;
  deferred: boolean;
  replied: boolean;
  inGuild(): boolean;
  deferReply(options: { ephemeral: boolean }): Promise<unknown>;
  reply(payload: ReplyPayload): Promise<unknown>;
  editReply(payload: Omit<ReplyPayload, "ephemeral">): Promise<unknown>;
  followUp?(payload: ReplyPayload): Promise<unknown>;
}

export interface ComponentInteraction {
  customId: string;
  user: DiscordUser;
  guildId: string | null;
  memberPermissions: PermissionSet | null;
  deferred: boolean;
  replied: boolean;
  inGuild(): boolean;
  deferUpdate(): Promise<unknown>;
  reply(payload: ReplyPayload): Promise<unknown>;
  update(payload: Omit<ReplyPayload, "ephemeral">): Promise<unknown>;
  editReply(payload: Omit<ReplyPayload, "ephemeral">): Promise<unknown>;
}

/** Discord API application-command option types. */
export type CommandOptionDefinition = {
  type: 3 | 4 | 5 | 6;
  name: string;
  description: string;
  required?: boolean;
  min_length?: number;
  max_length?: number;
  min_value?: number;
  max_value?: number;
  choices?: Array<{ name: string; value: string | number }>;
};

/** A JSON payload accepted by Discord's application-command registration API. */
export interface SlashCommandDefinition {
  type: 1;
  name: string;
  description: string;
  dm_permission: false;
  default_member_permissions?: string;
  options?: CommandOptionDefinition[];
}

export interface MinecraftLink {
  discordUserId: string;
  minecraftUuid: string;
  minecraftUsername: string;
  linkedAt: string;
  isPrimary?: boolean;
}

export interface LinkCode {
  /** A one-time pairing code; it must only ever be returned in an ephemeral response. */
  code: string;
  expiresAt: Date | string;
}

export interface PlayerProfile {
  minecraftUuid: string;
  username: string;
  rankName?: string | null;
  worldName?: string | null;
  playtimeSeconds?: number | null;
  blocksBroken?: number | null;
  kills?: number | null;
  deaths?: number | null;
  distanceMeters?: number | null;
  balance?: number | null;
  updatedAt?: string | null;
}

export interface ServerStatus {
  online: boolean;
  playerCount: number;
  tps?: number | null;
  version?: string | null;
  uptimeSeconds?: number | null;
  receivedAt?: string | null;
}

export interface OnlinePlayer {
  username: string;
  worldName: string | null;
}

export interface LinkService {
  getLinkByDiscordUser(discordUserId: string): Promise<MinecraftLink | null>;
  getLinksByDiscordUser(discordUserId: string): Promise<MinecraftLink[]>;
  /** Creates a server-verified, expiring pairing code; never accepts a Minecraft password. */
  createLinkCode(discordUserId: string): Promise<LinkCode>;
  unlinkDiscordUser(discordUserId: string): Promise<boolean>;
  unlinkMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean>;
  setPrimaryMinecraftAccount(discordUserId: string, minecraftUuid: string): Promise<boolean>;
}

export interface PlayerDirectoryService {
  getPlayerByMinecraftUuid(minecraftUuid: string): Promise<PlayerProfile | null>;
  getPlayerByUsername(username: string): Promise<PlayerProfile | null>;
}

export interface ServerStatusService {
  getServerStatus(guildId: string): Promise<ServerStatus | null>;
  getOnlinePlayers(guildId: string): Promise<OnlinePlayer[]>;
}

export interface RoleSyncService {
  syncRoles(input: { guildId: string; discordUserId: string; actorDiscordUserId: string }): Promise<{
    addedRoleNames: string[];
    removedRoleNames: string[];
    unchangedRoleNames?: string[];
  }>;
}

export interface MembershipSyncService {
  syncMemberships(input: { guildId: string; actorDiscordUserId: string; dryRun: boolean; limit: number }): Promise<{
    examined: number;
    updated: number;
    skipped: number;
    failed: number;
  }>;
}

export interface ServerControlService {
  setMaintenance(input: { guildId: string; enabled: boolean; reason: string | null; actorDiscordUserId: string }): Promise<{
    enabled: boolean;
    changedAt: string;
  }>;
  /** Implementations must use a safe API/message transport, never shell or console command concatenation. */
  announce(input: { guildId: string; message: string; actorDiscordUserId: string }): Promise<{
    delivered: number;
  }>;
}

export type ConfigSetting = "profile_visibility" | "announcement_channel" | "maintenance_message";

/** This deliberately contains only user-facing values; credentials and API keys are not representable. */
export interface PublicGuildConfig {
  profileVisibility: "public" | "members";
  announcementChannelId: string | null;
  maintenanceMessage: string | null;
}

export interface PublicConfigService {
  getPublicGuildConfig(guildId: string): Promise<PublicGuildConfig>;
  updatePublicGuildConfig(input: {
    guildId: string;
    actorDiscordUserId: string;
    setting: ConfigSetting;
    value: string;
  }): Promise<PublicGuildConfig>;
}

export interface CommandServices {
  links: LinkService;
  players: PlayerDirectoryService;
  status: ServerStatusService;
  roles: RoleSyncService;
  memberships: MembershipSyncService;
  control: ServerControlService;
  config: PublicConfigService;
  /** Public HTTPS base URL, e.g. https://kairu.example/. Never place tokens in this value. */
  websiteBaseUrl: string;
  logger?: Pick<Console, "error" | "warn">;
}

export interface CommandModule {
  definition: SlashCommandDefinition;
  execute(interaction: CommandInteraction, services: CommandServices): Promise<void>;
  handleComponent?(interaction: ComponentInteraction, services: CommandServices): Promise<boolean>;
}

export type CommandRegistry = ReadonlyMap<string, CommandModule>;

export const DISCORD_PERMISSIONS = {
  ADMINISTRATOR: "8",
  MANAGE_GUILD: "32",
  MANAGE_ROLES: "268435456"
} as const;

export const COLORS = {
  BRAND: 0x5865f2,
  SUCCESS: 0x57f287,
  WARNING: 0xfee75c,
  DANGER: 0xed4245,
  MUTED: 0x99aab5
} as const;

export const MINECRAFT_USERNAME_PATTERN = /^[A-Za-z0-9_]{3,16}$/;
export const DISCORD_SNOWFLAKE_PATTERN = /^\d{5,25}$/;

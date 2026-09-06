import {
  ChannelType,
  type Guild,
  OverwriteType,
  PermissionFlagsBits,
  type CategoryChannel,
  type NonThreadGuildBasedChannel,
  type Role
} from "discord.js";
import {
  SERVER_BLUEPRINT,
  type CategoryBlueprint,
  type ChannelBlueprint,
  type RoleBlueprint,
  type RoleKey,
  type ServerBlueprint,
  type SetupResourceKind
} from "../config/server-blueprint.js";
import {
  PREMIUM_ROLE_KEYS,
  STAFF_ROLE_KEYS,
  createChannelPermissionPlan,
  type PermissionOverwrite,
  type ResolvedRoleIds
} from "../permissions/policy.js";
import type { SetupResourceRepository } from "./setup-resource.repository.js";

export interface SetupLogger {
  warn(message: string, metadata?: Record<string, unknown>): void;
}

const noopLogger: SetupLogger = { warn: () => undefined };

export interface RoleSnapshot {
  readonly id: string;
  readonly name: string;
  readonly color: string;
  readonly hoist: boolean;
  readonly mentionable: boolean;
  readonly position: number;
  readonly managed: boolean;
}

export interface ChannelSnapshot {
  readonly id: string;
  readonly type: "category" | "text-channel" | "voice-channel";
  readonly name: string;
  readonly parentId: string | null;
  readonly topic?: string | null;
  readonly nsfw?: boolean;
  readonly bitrate?: number;
  readonly userLimit?: number;
}

export interface GuildSetupAdapter {
  readonly guildId: string;
  getBotPermissions(): Promise<bigint>;
  getBotHighestRolePosition(): Promise<number | null>;
  listRoles(): Promise<readonly RoleSnapshot[]>;
  listChannels(): Promise<readonly ChannelSnapshot[]>;
  createRole(spec: RoleBlueprint): Promise<RoleSnapshot>;
  editRole(id: string, spec: RoleBlueprint): Promise<RoleSnapshot>;
  createCategory(spec: CategoryBlueprint): Promise<ChannelSnapshot>;
  editCategory(id: string, spec: CategoryBlueprint): Promise<ChannelSnapshot>;
  createChannel(spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot>;
  editChannel(id: string, spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot>;
  /** Replaces only Kairu-controlled overwrites and preserves every other overwrite. */
  reconcilePermissionOverwrites(
    channelId: string,
    desired: readonly PermissionOverwrite[],
    controlledRoleIds: ReadonlySet<string>
  ): Promise<boolean>;
}

export type SetupAction = "created" | "reused" | "updated" | "failed";

export interface SetupItemResult {
  readonly key: string;
  readonly kind: SetupResourceKind;
  readonly name: string;
  readonly action: SetupAction;
  readonly detail?: string;
}

export interface SetupCounts {
  readonly created: number;
  readonly reused: number;
  readonly updated: number;
  readonly failed: number;
}

export interface ServerSetupResult {
  readonly guildId: string;
  readonly counts: SetupCounts;
  readonly items: readonly SetupItemResult[];
  readonly completedAt: Date;
}

export class SetupPreflightError extends Error {
  public constructor(message: string) {
    super(message);
    this.name = "SetupPreflightError";
  }
}

/**
 * Reconciles the declarative layout without assuming persisted Discord IDs are
 * accurate. A stored ID is used only if it still identifies the expected type;
 * otherwise a unique exact-name lookup recovers it before any new resource is
 * created. Ambiguous exact-name matches fail closed rather than duplicating or
 * touching an arbitrary community resource.
 */
export class ServerSetupService {
  public constructor(
    private readonly resources: SetupResourceRepository,
    private readonly blueprint: ServerBlueprint = SERVER_BLUEPRINT,
    private readonly logger: SetupLogger = noopLogger
  ) {}

  public async setup(guild: GuildSetupAdapter): Promise<ServerSetupResult> {
    await this.assertBotPermissions(guild);
    const results: SetupItemResult[] = [];
    const roleIds: Partial<Record<RoleKey, string>> = {};

    // Discord adds newly created roles directly below the bot. Reversing the
    // high-to-low blueprint preserves its intended hierarchy on a clean guild.
    for (const spec of [...this.blueprint.roles].reverse()) {
      const result = await this.ensureRole(guild, spec);
      results.push(result.item);
      if (result.id) roleIds[spec.key] = result.id;
    }

    // Private resources are never configured when an isolation role failed.
    const categories = new Map<string, string>();
    for (const spec of this.blueprint.categories) {
      const result = await this.ensureCategory(guild, spec, roleIds);
      results.push(result.item);
      if (result.id) categories.set(spec.key, result.id);
    }

    for (const spec of this.blueprint.channels) {
      const parentId = categories.get(spec.parentKey);
      if (!parentId) {
        results.push(this.failed(spec.resourceKey, spec.type, spec.name, `Parent category ${spec.parentKey} is unavailable.`));
        continue;
      }
      results.push(await this.ensureChannel(guild, spec, parentId, roleIds));
    }

    return {
      guildId: guild.guildId,
      counts: countResults(results),
      items: results,
      completedAt: new Date()
    };
  }

  private async assertBotPermissions(guild: GuildSetupAdapter): Promise<void> {
    const permissions = await guild.getBotPermissions();
    const missing = [PermissionFlagsBits.ManageRoles, PermissionFlagsBits.ManageChannels]
      .filter((permission) => (permissions & permission) !== permission);
    if (missing.length > 0) {
      throw new SetupPreflightError("The bot requires both Manage Roles and Manage Channels before server setup can start.");
    }
    const highestPosition = await guild.getBotHighestRolePosition();
    if (highestPosition === null) {
      throw new SetupPreflightError("The bot member could not be resolved in this guild; role hierarchy cannot be verified.");
    }
  }

  private async ensureRole(guild: GuildSetupAdapter, spec: RoleBlueprint): Promise<{ id?: string; item: SetupItemResult }> {
    try {
      const existing = await this.resolveRole(guild, spec);
      if (!existing) {
        const created = await guild.createRole(spec);
        await this.persist(guild, spec.resourceKey, "role", created.id);
        return { id: created.id, item: this.success(spec.resourceKey, "role", spec.name, "created") };
      }

      await this.assertRoleEditable(guild, existing, spec.name);
      const changed = !sameRole(existing, spec);
      const resolved = changed ? await guild.editRole(existing.id, spec) : existing;
      await this.persist(guild, spec.resourceKey, "role", resolved.id);
      return { id: resolved.id, item: this.success(spec.resourceKey, "role", spec.name, changed ? "updated" : "reused") };
    } catch (error) {
      return { item: this.failed(spec.resourceKey, "role", spec.name, errorMessage(error)) };
    }
  }

  private async ensureCategory(
    guild: GuildSetupAdapter,
    spec: CategoryBlueprint,
    roleIds: ResolvedRoleIds
  ): Promise<{ id?: string; item: SetupItemResult }> {
    try {
      const existing = await this.resolveChannel(guild, spec.resourceKey, "category", spec.name);
      const changedDefinition = Boolean(existing && existing.name !== spec.name);
      const category = existing ? (changedDefinition ? await guild.editCategory(existing.id, spec) : existing) : await guild.createCategory(spec);
      await this.persist(guild, spec.resourceKey, "category", category.id);

      const isolationChannel = asPolicyChannel(spec);
      const desired = createChannelPermissionPlan(guild.guildId, isolationChannel, roleIds);
      const changedPermissions = await guild.reconcilePermissionOverwrites(
        category.id,
        desired,
        controlledPolicyRoleIds(guild.guildId, roleIds)
      );
      return {
        id: category.id,
        item: this.success(spec.resourceKey, "category", spec.name, existing ? (changedDefinition || changedPermissions ? "updated" : "reused") : "created")
      };
    } catch (error) {
      return { item: this.failed(spec.resourceKey, "category", spec.name, errorMessage(error)) };
    }
  }

  private async ensureChannel(
    guild: GuildSetupAdapter,
    spec: ChannelBlueprint,
    parentId: string,
    roleIds: ResolvedRoleIds
  ): Promise<SetupItemResult> {
    try {
      const existing = await this.resolveChannel(guild, spec.resourceKey, spec.type, spec.name);
      const changedDefinition = existing ? !sameChannel(existing, spec, parentId) : false;
      const channel = existing
        ? (changedDefinition ? await guild.editChannel(existing.id, spec, parentId) : existing)
        : await guild.createChannel(spec, parentId);
      await this.persist(guild, spec.resourceKey, spec.type, channel.id);

      const changedPermissions = await guild.reconcilePermissionOverwrites(
        channel.id,
        createChannelPermissionPlan(guild.guildId, spec, roleIds),
        controlledPolicyRoleIds(guild.guildId, roleIds)
      );
      return this.success(spec.resourceKey, spec.type, spec.name, existing ? (changedDefinition || changedPermissions ? "updated" : "reused") : "created");
    } catch (error) {
      return this.failed(spec.resourceKey, spec.type, spec.name, errorMessage(error));
    }
  }

  private async resolveRole(guild: GuildSetupAdapter, spec: RoleBlueprint): Promise<RoleSnapshot | null> {
    const roles = await guild.listRoles();
    const persisted = await this.resources.findByGuildAndKey(guild.guildId, spec.resourceKey);
    const byId = persisted ? roles.find((role) => role.id === persisted.discordId) : undefined;
    // The stable key owns the recorded ID. A renamed role is repaired in place;
    // only a missing stored ID falls back to a unique exact-name recovery.
    if (byId) return byId;
    return uniqueExactName(roles, spec.name, "role");
  }

  private async resolveChannel(
    guild: GuildSetupAdapter,
    resourceKey: string,
    type: ChannelSnapshot["type"],
    name: string
  ): Promise<ChannelSnapshot | null> {
    const channels = await guild.listChannels();
    const persisted = await this.resources.findByGuildAndKey(guild.guildId, resourceKey);
    const byId = persisted ? channels.find((channel) => channel.id === persisted.discordId) : undefined;
    // A stored channel ID is safe to reconcile when its Discord type still
    // matches; renamed or moved resources are updated instead of duplicated.
    if (byId?.type === type) return byId;
    return uniqueExactName(channels.filter((channel) => channel.type === type), name, type);
  }

  private async assertRoleEditable(guild: GuildSetupAdapter, role: RoleSnapshot, expectedName: string): Promise<void> {
    const highest = await guild.getBotHighestRolePosition();
    if (highest === null || role.managed || role.position >= highest) {
      throw new SetupPreflightError(`The role ${expectedName} is managed or at/above the bot's highest role. Move the bot role above it before running setup.`);
    }
  }

  private async persist(guild: GuildSetupAdapter, resourceKey: string, resourceType: SetupResourceKind, discordId: string): Promise<void> {
    await this.resources.upsert({ guildId: guild.guildId, resourceKey, resourceType, discordId });
  }

  private success(key: string, kind: SetupResourceKind, name: string, action: Exclude<SetupAction, "failed">): SetupItemResult {
    return { key, kind, name, action };
  }

  private failed(key: string, kind: SetupResourceKind, name: string, detail: string): SetupItemResult {
    this.logger.warn("Discord server setup item failed", { key, kind, name, detail });
    return { key, kind, name, action: "failed", detail };
  }
}

function uniqueExactName<T extends { name: string }>(items: readonly T[], name: string, kind: string): T | null {
  const matches = items.filter((item) => item.name === name);
  if (matches.length > 1) throw new Error(`Refusing to recover ${kind} ${name}: ${matches.length} exact-name matches are ambiguous.`);
  return matches[0] ?? null;
}

function sameRole(role: RoleSnapshot, spec: RoleBlueprint): boolean {
  return role.name === spec.name && role.color.toUpperCase() === spec.color.toUpperCase() && role.hoist === spec.hoist && role.mentionable === spec.mentionable;
}

function sameChannel(channel: ChannelSnapshot, spec: ChannelBlueprint, parentId: string): boolean {
  if (channel.name !== spec.name || channel.parentId !== parentId) return false;
  if (spec.type === "text-channel") return channel.topic === spec.topic && channel.nsfw === spec.nsfw;
  return channel.bitrate === spec.bitrate && channel.userLimit === spec.userLimit;
}

function asPolicyChannel(category: CategoryBlueprint): ChannelBlueprint {
  return {
    key: category.key,
    resourceKey: `text-channel.category-${category.key}`,
    type: "text-channel",
    name: category.name,
    parentKey: "",
    audience: category.key === "staff" ? "staff" : category.key === "premium" ? "premium" : "public",
    topic: "",
    nsfw: false
  };
}

/** Every setup-owned access role is controlled, so audience changes cannot leave a stale staff/premium grant. */
function controlledPolicyRoleIds(guildId: string, roleIds: ResolvedRoleIds): ReadonlySet<string> {
  return new Set([guildId, ...STAFF_ROLE_KEYS, ...PREMIUM_ROLE_KEYS].map((key) => key === guildId ? key : roleIds[key as RoleKey]).filter((id): id is string => Boolean(id)));
}

function countResults(items: readonly SetupItemResult[]): SetupCounts {
  return items.reduce<SetupCounts>((counts, item) => ({ ...counts, [item.action]: counts[item.action] + 1 }), {
    created: 0,
    reused: 0,
    updated: 0,
    failed: 0
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

/** Production discord.js v14 adapter. Unit tests should use GuildSetupAdapter fakes instead. */
export class DiscordJsGuildSetupAdapter implements GuildSetupAdapter {
  public constructor(private readonly guild: Guild, private readonly reason = "Kairu declarative server setup") {}

  public get guildId(): string {
    return this.guild.id;
  }

  public async getBotPermissions(): Promise<bigint> {
    const me = this.guild.members.me ?? await this.guild.members.fetchMe();
    return me.permissions.bitfield;
  }

  public async getBotHighestRolePosition(): Promise<number | null> {
    const me = this.guild.members.me ?? await this.guild.members.fetchMe();
    return me.roles.highest?.position ?? null;
  }

  public async listRoles(): Promise<readonly RoleSnapshot[]> {
    const roles = await this.guild.roles.fetch();
    return [...roles.values()].filter((role): role is Role => role !== null).map(toRoleSnapshot);
  }

  public async listChannels(): Promise<readonly ChannelSnapshot[]> {
    const channels = await this.guild.channels.fetch();
    return [...channels.values()].filter((channel): channel is NonThreadGuildBasedChannel => channel !== null).flatMap(toChannelSnapshot);
  }

  public async createRole(spec: RoleBlueprint): Promise<RoleSnapshot> {
    return toRoleSnapshot(await this.guild.roles.create({ name: spec.name, color: spec.color, hoist: spec.hoist, mentionable: spec.mentionable, reason: this.reason }));
  }

  public async editRole(id: string, spec: RoleBlueprint): Promise<RoleSnapshot> {
    const role = await this.guild.roles.fetch(id);
    if (!role) throw new Error(`Role ${id} disappeared before it could be updated.`);
    return toRoleSnapshot(await role.edit({ name: spec.name, color: spec.color, hoist: spec.hoist, mentionable: spec.mentionable, reason: this.reason }));
  }

  public async createCategory(spec: CategoryBlueprint): Promise<ChannelSnapshot> {
    return requireChannelSnapshot(await this.guild.channels.create({ name: spec.name, type: ChannelType.GuildCategory, reason: this.reason }));
  }

  public async editCategory(id: string, spec: CategoryBlueprint): Promise<ChannelSnapshot> {
    const channel = await this.fetchCategory(id);
    return requireChannelSnapshot(await channel.edit({ name: spec.name, reason: this.reason }));
  }

  public async createChannel(spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot> {
    if (spec.type === "text-channel") {
      return requireChannelSnapshot(await this.guild.channels.create({
        name: spec.name, type: ChannelType.GuildText, parent: parentId, topic: spec.topic, nsfw: spec.nsfw, reason: this.reason
      }));
    }
    return requireChannelSnapshot(await this.guild.channels.create({
      name: spec.name, type: ChannelType.GuildVoice, parent: parentId, bitrate: spec.bitrate, userLimit: spec.userLimit, reason: this.reason
    }));
  }

  public async editChannel(id: string, spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot> {
    const channel = await this.guild.channels.fetch(id);
    if (!channel || (channel.type !== ChannelType.GuildText && channel.type !== ChannelType.GuildVoice)) {
      throw new Error(`Channel ${id} disappeared or changed type before update.`);
    }
    if (spec.type === "text-channel") {
      if (channel.type !== ChannelType.GuildText) throw new Error(`Channel ${id} is not a text channel.`);
      return requireChannelSnapshot(await channel.edit({
        name: spec.name, parent: parentId, topic: spec.topic, nsfw: spec.nsfw, reason: this.reason
      }));
    }
    if (channel.type !== ChannelType.GuildVoice) throw new Error(`Channel ${id} is not a voice channel.`);
    return requireChannelSnapshot(await channel.edit({
      name: spec.name, parent: parentId, bitrate: spec.bitrate, userLimit: spec.userLimit, reason: this.reason
    }));
  }

  public async reconcilePermissionOverwrites(channelId: string, desired: readonly PermissionOverwrite[], controlledRoleIds: ReadonlySet<string>): Promise<boolean> {
    const channel = await this.guild.channels.fetch(channelId);
    if (!channel || (channel.type !== ChannelType.GuildText && channel.type !== ChannelType.GuildVoice && channel.type !== ChannelType.GuildCategory)) {
      throw new Error(`Channel ${channelId} cannot hold permission overwrites.`);
    }
    const current = [...channel.permissionOverwrites.cache.values()].map((overwrite) => ({
      id: overwrite.id,
      type: overwrite.type,
      allow: overwrite.allow.bitfield,
      deny: overwrite.deny.bitfield
    }));
    const preserved = current.filter((overwrite) => !controlledRoleIds.has(overwrite.id));
    const merged = [...preserved, ...desired.map((overwrite) => ({ ...overwrite, type: OverwriteType.Role }))];
    if (sameOverwrites(current, merged)) return false;
    await channel.permissionOverwrites.set(merged, this.reason);
    return true;
  }

  private async fetchCategory(id: string): Promise<CategoryChannel> {
    const channel = await this.guild.channels.fetch(id);
    if (!channel || channel.type !== ChannelType.GuildCategory) throw new Error(`Category ${id} disappeared or changed type before update.`);
    return channel;
  }
}

function toRoleSnapshot(role: Role): RoleSnapshot {
  return { id: role.id, name: role.name, color: role.hexColor, hoist: role.hoist, mentionable: role.mentionable, position: role.position, managed: role.managed };
}

function toChannelSnapshot(channel: NonThreadGuildBasedChannel): ChannelSnapshot[] {
  if (channel.type === ChannelType.GuildCategory) return [{ id: channel.id, type: "category", name: channel.name, parentId: channel.parentId }];
  if (channel.type === ChannelType.GuildText) return [{ id: channel.id, type: "text-channel", name: channel.name, parentId: channel.parentId, topic: channel.topic, nsfw: channel.nsfw }];
  if (channel.type === ChannelType.GuildVoice) return [{ id: channel.id, type: "voice-channel", name: channel.name, parentId: channel.parentId, bitrate: channel.bitrate, userLimit: channel.userLimit }];
  return [];
}

function requireChannelSnapshot(channel: NonThreadGuildBasedChannel): ChannelSnapshot {
  const snapshot = toChannelSnapshot(channel)[0];
  if (!snapshot) throw new Error(`Discord returned an unexpected channel type (${channel.type}).`);
  return snapshot;
}

function sameOverwrites(
  current: readonly { id: string; type: OverwriteType; allow: bigint; deny: bigint }[],
  expected: readonly { id: string; type: OverwriteType; allow: bigint; deny: bigint }[]
): boolean {
  const normalize = (entries: readonly { id: string; type: OverwriteType; allow: bigint; deny: bigint }[]) => [...entries]
    .map((entry) => `${entry.id}:${entry.type}:${entry.allow}:${entry.deny}`)
    .sort();
  const left = normalize(current);
  const right = normalize(expected);
  return left.length === right.length && left.every((entry, index) => entry === right[index]);
}

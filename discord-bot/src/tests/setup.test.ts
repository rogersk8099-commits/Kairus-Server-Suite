import { PermissionFlagsBits } from "discord.js";
import { describe, expect, it } from "vitest";
import { SERVER_BLUEPRINT, expectedResourceCount, type CategoryBlueprint, type ChannelBlueprint, type RoleBlueprint } from "../setup/config/server-blueprint.js";
import { SETUP_SERVER_COMMAND, buildSetupConfirmationSummary, formatSetupResult } from "../setup/commands/admin/setup-server.command.js";
import {
  PREMIUM_ROLE_KEYS,
  STAFF_ROLE_KEYS,
  createChannelPermissionPlan,
  isStaffMember
} from "../setup/permissions/policy.js";
import { MemorySetupResourceRepository } from "../setup/services/setup-resource.repository.js";
import {
  ServerSetupService,
  type ChannelSnapshot,
  type GuildSetupAdapter,
  type RoleSnapshot
} from "../setup/services/server-setup.service.js";

/** Pure in-memory adapter. Its methods never instantiate a discord.js Guild or issue HTTP calls. */
class FakeGuildSetupAdapter implements GuildSetupAdapter {
  public readonly guildId = "test-guild";
  public createRoleCalls = 0;
  public createCategoryCalls = 0;
  public createChannelCalls = 0;
  public editCalls = 0;
  public overwriteChanges = 0;
  private nextId = 1;
  private readonly roles: RoleSnapshot[] = [];
  private readonly channels: ChannelSnapshot[] = [];
  private readonly overwrites = new Map<string, readonly unknown[]>();

  public async getBotPermissions(): Promise<bigint> {
    return PermissionFlagsBits.ManageRoles | PermissionFlagsBits.ManageChannels;
  }

  public async getBotHighestRolePosition(): Promise<number | null> {
    return 100;
  }

  public async listRoles(): Promise<readonly RoleSnapshot[]> {
    return [...this.roles];
  }

  public async listChannels(): Promise<readonly ChannelSnapshot[]> {
    return [...this.channels];
  }

  public async createRole(spec: RoleBlueprint): Promise<RoleSnapshot> {
    this.createRoleCalls += 1;
    const role = { id: this.id(), name: spec.name, color: spec.color, hoist: spec.hoist, mentionable: spec.mentionable, position: 1, managed: false };
    this.roles.push(role);
    return role;
  }

  public async editRole(id: string, spec: RoleBlueprint): Promise<RoleSnapshot> {
    this.editCalls += 1;
    const index = this.roles.findIndex((role) => role.id === id);
    const changed = { ...this.roles[index]!, name: spec.name, color: spec.color, hoist: spec.hoist, mentionable: spec.mentionable };
    this.roles[index] = changed;
    return changed;
  }

  public async createCategory(spec: CategoryBlueprint): Promise<ChannelSnapshot> {
    this.createCategoryCalls += 1;
    const channel: ChannelSnapshot = { id: this.id(), type: "category", name: spec.name, parentId: null };
    this.channels.push(channel);
    return channel;
  }

  public async editCategory(id: string, spec: CategoryBlueprint): Promise<ChannelSnapshot> {
    this.editCalls += 1;
    const index = this.channels.findIndex((channel) => channel.id === id);
    const changed: ChannelSnapshot = { ...this.channels[index]!, name: spec.name };
    this.channels[index] = changed;
    return changed;
  }

  public async createChannel(spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot> {
    this.createChannelCalls += 1;
    const channel = snapshot(this.id(), spec, parentId);
    this.channels.push(channel);
    return channel;
  }

  public async editChannel(id: string, spec: ChannelBlueprint, parentId: string): Promise<ChannelSnapshot> {
    this.editCalls += 1;
    const index = this.channels.findIndex((channel) => channel.id === id);
    const changed = snapshot(id, spec, parentId);
    this.channels[index] = changed;
    return changed;
  }

  public async reconcilePermissionOverwrites(channelId: string, desired: readonly unknown[]): Promise<boolean> {
    const current = this.overwrites.get(channelId);
    const serialized = JSON.stringify(desired, (_, value) => typeof value === "bigint" ? value.toString() : value);
    const prior = JSON.stringify(current, (_, value) => typeof value === "bigint" ? value.toString() : value);
    if (serialized === prior) return false;
    this.overwrites.set(channelId, desired);
    this.overwriteChanges += 1;
    return true;
  }

  public deleteRole(id: string): void {
    const index = this.roles.findIndex((role) => role.id === id);
    if (index >= 0) this.roles.splice(index, 1);
  }

  public roleIdByName(name: string): string {
    return this.roles.find((role) => role.name === name)!.id;
  }

  public renameRole(id: string, name: string): void {
    const index = this.roles.findIndex((role) => role.id === id);
    this.roles[index] = { ...this.roles[index]!, name };
  }

  private id(): string {
    return `fake-${this.nextId++}`;
  }
}

function snapshot(id: string, spec: ChannelBlueprint, parentId: string): ChannelSnapshot {
  if (spec.type === "text-channel") return { id, type: "text-channel", name: spec.name, parentId, topic: spec.topic, nsfw: spec.nsfw };
  return { id, type: "voice-channel", name: spec.name, parentId, bitrate: spec.bitrate, userLimit: spec.userLimit };
}

describe("ServerSetupService", () => {
  it("creates the blueprint once then reuses every resource on the next run without Discord network calls", async () => {
    const repository = new MemorySetupResourceRepository();
    const guild = new FakeGuildSetupAdapter();
    const service = new ServerSetupService(repository);

    const first = await service.setup(guild);
    expect(first.counts).toEqual({ created: expectedResourceCount(), reused: 0, updated: 0, failed: 0 });
    expect(guild.createRoleCalls).toBe(15);

    const second = await service.setup(guild);
    expect(second.counts).toEqual({ created: 0, reused: expectedResourceCount(), updated: 0, failed: 0 });
    expect(guild.createRoleCalls).toBe(15);
    expect(guild.createCategoryCalls).toBe(SERVER_BLUEPRINT.categories.length);
    expect(guild.createChannelCalls).toBe(SERVER_BLUEPRINT.channels.length);
    expect(guild.editCalls).toBe(0);
  });

  it("recovers a stale stored ID through a unique exact-name match instead of creating a duplicate", async () => {
    const repository = new MemorySetupResourceRepository();
    const guild = new FakeGuildSetupAdapter();
    const service = new ServerSetupService(repository);
    await service.setup(guild);
    const memberId = guild.roleIdByName("Member");
    await repository.upsert({ guildId: guild.guildId, resourceKey: "role.member", resourceType: "role", discordId: "deleted-resource" });

    const result = await service.setup(guild);
    expect(result.items.find((item) => item.key === "role.member")?.action).toBe("reused");
    expect(guild.roleIdByName("Member")).toBe(memberId);
    expect(guild.createRoleCalls).toBe(15);
  });

  it("repairs a stored role whose display name drifted without creating a duplicate", async () => {
    const repository = new MemorySetupResourceRepository();
    const guild = new FakeGuildSetupAdapter();
    const service = new ServerSetupService(repository);
    await service.setup(guild);
    const memberId = guild.roleIdByName("Member");
    guild.renameRole(memberId, "Member (legacy)");

    const result = await service.setup(guild);
    expect(result.items.find((item) => item.key === "role.member")?.action).toBe("updated");
    expect(guild.roleIdByName("Member")).toBe(memberId);
    expect(guild.createRoleCalls).toBe(15);
  });
});

describe("permission isolation", () => {
  const roleIds = Object.fromEntries(SERVER_BLUEPRINT.roles.map((role, index) => [role.key, `role-${index}`]));
  const staffChannel = SERVER_BLUEPRINT.channels.find((channel) => channel.resourceKey === "text-channel.staff-chat")!;
  const premiumChannel = SERVER_BLUEPRINT.channels.find((channel) => channel.resourceKey === "text-channel.premium-lounge")!;

  it("grants staff only to staff channels and premium roles only to premium channels", () => {
    const staffPlan = createChannelPermissionPlan("guild", staffChannel, roleIds);
    const premiumPlan = createChannelPermissionPlan("guild", premiumChannel, roleIds);
    const staffIds = new Set(staffPlan.map((overwrite) => overwrite.id));
    const premiumIds = new Set(premiumPlan.map((overwrite) => overwrite.id));

    expect(staffPlan[0]).toMatchObject({ id: "guild", deny: PermissionFlagsBits.ViewChannel });
    expect(premiumPlan[0]).toMatchObject({ id: "guild", deny: PermissionFlagsBits.ViewChannel });
    for (const key of STAFF_ROLE_KEYS) expect(staffIds.has(roleIds[key]!)).toBe(true);
    for (const key of PREMIUM_ROLE_KEYS) expect(staffIds.has(roleIds[key]!)).toBe(false);
    for (const key of PREMIUM_ROLE_KEYS) expect(premiumIds.has(roleIds[key]!)).toBe(true);
    for (const key of STAFF_ROLE_KEYS) expect(premiumIds.has(roleIds[key]!)).toBe(false);
  });

  it("recognizes staff membership only from an explicit staff role", () => {
    expect(isStaffMember([roleIds.premium!], roleIds)).toBe(false);
    expect(isStaffMember([roleIds.moderator!], roleIds)).toBe(true);
  });
});

describe("setup command", () => {
  it("is staff-only by default and exposes a non-mutating confirmation summary", () => {
    const json = SETUP_SERVER_COMMAND.toJSON();
    expect(json.default_member_permissions).toBe(PermissionFlagsBits.ManageGuild.toString());
    expect(buildSetupConfirmationSummary()).toContain("confirm:true");
    expect(formatSetupResult({ guildId: "g", counts: { created: 1, reused: 2, updated: 3, failed: 0 }, items: [], completedAt: new Date() }))
      .toContain("Created: **1** | Reused: **2** | Updated: **3** | Failed: **0**");
  });
});

import { describe, expect, it, vi } from "vitest";
import { dispatchCommand, dispatchComponent, slashCommandDefinitions } from "./index.js";
import type {
  CommandInteraction,
  CommandOptions,
  CommandServices,
  ComponentInteraction,
  DiscordPermission,
  DiscordUser,
  ReplyPayload
} from "./interfaces.js";

const actor: DiscordUser = { id: "123456789012345678", username: "Admin" };
const member: DiscordUser = { id: "223456789012345678", username: "LinkedMember" };

function options(values: Record<string, string | boolean | number | DiscordUser | null> = {}): CommandOptions {
  return {
    getString: (name) => typeof values[name] === "string" ? values[name] as string : null,
    getBoolean: (name) => typeof values[name] === "boolean" ? values[name] as boolean : null,
    getInteger: (name) => typeof values[name] === "number" ? values[name] as number : null,
    getUser: (name) => typeof values[name] === "object" && values[name] !== null && "id" in (values[name] as object) ? values[name] as DiscordUser : null
  };
}

function interaction(commandName: string, values: Record<string, string | boolean | number | DiscordUser | null> = {}, permissions: DiscordPermission[] = ["Administrator"]): CommandInteraction & { replies: ReplyPayload[]; defers: boolean[] } {
  const replies: ReplyPayload[] = [];
  const defers: boolean[] = [];
  return {
    commandName,
    user: actor,
    guildId: "923456789012345678",
    memberPermissions: { has: (permission) => permissions.includes(permission) },
    options: options(values),
    deferred: false,
    replied: false,
    inGuild: () => true,
    deferReply: async ({ ephemeral }) => { defers.push(ephemeral); },
    reply: async (payload) => { replies.push(payload); },
    editReply: async (payload) => { replies.push(payload); },
    followUp: async (payload) => { replies.push(payload); },
    replies,
    defers
  };
}

function component(customId: string, user = actor): ComponentInteraction & { replies: ReplyPayload[]; updates: Omit<ReplyPayload, "ephemeral">[]; deferredCalls: number } {
  const replies: ReplyPayload[] = [];
  const updates: Omit<ReplyPayload, "ephemeral">[] = [];
  let deferredCalls = 0;
  return {
    customId,
    user,
    guildId: "923456789012345678",
    memberPermissions: { has: () => false },
    deferred: false,
    replied: false,
    inGuild: () => true,
    deferUpdate: async () => { deferredCalls += 1; },
    reply: async (payload) => { replies.push(payload); },
    update: async (payload) => { updates.push(payload); },
    editReply: async (payload) => { updates.push(payload); },
    replies,
    updates,
    get deferredCalls() { return deferredCalls; }
  };
}

function services(): CommandServices {
  return {
    websiteBaseUrl: "https://kairu.example/",
    logger: { error: vi.fn(), warn: vi.fn() },
    links: {
      getLinkByDiscordUser: vi.fn().mockResolvedValue(null),
      getLinksByDiscordUser: vi.fn().mockResolvedValue([]),
      createLinkCode: vi.fn().mockResolvedValue({ code: "SAFE_LINK_CODE_123456", expiresAt: "2030-01-01T00:00:00.000Z" }),
      unlinkDiscordUser: vi.fn().mockResolvedValue(true),
      unlinkMinecraftAccount: vi.fn().mockResolvedValue(true),
      setPrimaryMinecraftAccount: vi.fn().mockResolvedValue(true)
    },
    players: {
      getPlayerByMinecraftUuid: vi.fn().mockResolvedValue(null),
      getPlayerByUsername: vi.fn().mockResolvedValue(null)
    },
    status: {
      getServerStatus: vi.fn().mockResolvedValue(null),
      getOnlinePlayers: vi.fn().mockResolvedValue([])
    },
    roles: {
      syncRoles: vi.fn().mockResolvedValue({ addedRoleNames: [], removedRoleNames: [], unchangedRoleNames: [] })
    },
    memberships: {
      syncMemberships: vi.fn().mockResolvedValue({ examined: 0, updated: 0, skipped: 0, failed: 0 })
    },
    control: {
      setMaintenance: vi.fn().mockResolvedValue({ enabled: true, changedAt: "2030-01-01T00:00:00.000Z" }),
      announce: vi.fn().mockResolvedValue({ delivered: 0 })
    },
    config: {
      getPublicGuildConfig: vi.fn().mockResolvedValue({ profileVisibility: "members", announcementChannelId: null, maintenanceMessage: null }),
      updatePublicGuildConfig: vi.fn().mockResolvedValue({ profileVisibility: "members", announcementChannelId: null, maintenanceMessage: null })
    }
  };
}

describe("modular Discord commands", () => {
  it("exports all requested individually registerable command definitions", () => {
    expect(slashCommandDefinitions.map((command) => command.name)).toEqual([
      "link-minecraft", "unlink-minecraft", "minecraft-accounts", "primary-minecraft", "profile", "server-status", "online", "player",
      "sync-roles", "sync-memberships", "maintenance", "announce", "config"
    ]);
    expect(slashCommandDefinitions.every((command) => command.dm_permission === false)).toBe(true);
  });

  it("denies staff role synchronization without the required permission before any service call", async () => {
    const input = interaction("sync-roles", { member }, []);
    const api = services();
    await dispatchCommand(input, api);
    expect(api.links.getLinkByDiscordUser).not.toHaveBeenCalled();
    expect(input.replies[0]).toMatchObject({ ephemeral: true, content: expect.stringContaining("Manage Roles") });
  });

  it("reports a missing link when a member is selected for role synchronization", async () => {
    const input = interaction("sync-roles", { member }, ["ManageRoles"]);
    const api = services();
    await dispatchCommand(input, api);
    expect(api.roles.syncRoles).not.toHaveBeenCalled();
    expect(input.replies[0].content).toContain("does not have a linked Minecraft account");
  });

  it("returns a safe generic message when a status API fails", async () => {
    const input = interaction("server-status");
    const api = services();
    vi.mocked(api.status.getServerStatus).mockRejectedValue(new Error("database password=should-not-leak"));
    await dispatchCommand(input, api);
    expect(input.replies[0].content).toBe("I could not complete that request right now. Please try again later.");
    expect(JSON.stringify(input.replies)).not.toContain("password=should-not-leak");
  });

  it("creates an ephemeral one-time link response without ever requesting a password", async () => {
    const input = interaction("link-minecraft");
    const api = services();
    await dispatchCommand(input, api);
    expect(input.defers).toEqual([true]);
    expect(api.links.createLinkCode).toHaveBeenCalledWith(actor.id);
    expect(input.replies[0]).toMatchObject({ ephemeral: true, allowedMentions: { parse: [] } });
    const body = JSON.stringify(input.replies[0]);
    expect(body).toContain("/kairu link SAFE_LINK_CODE_123456");
    expect(body).toContain("never asks for or stores Minecraft passwords");
  });

  it("presents unlink confirmation and rejects a different user pressing the button", async () => {
    const input = interaction("unlink-minecraft");
    const api = services();
    vi.mocked(api.links.getLinksByDiscordUser).mockResolvedValue([{ discordUserId: actor.id, minecraftUuid: "123e4567-e89b-42d3-a456-426614174000", minecraftUsername: "Alex", linkedAt: "2030-01-01T00:00:00.000Z", isPrimary: true }]);
    await dispatchCommand(input, api);
    const customId = input.replies[0].components?.[0].components[0].custom_id!;
    expect(customId).toBe(`kairu:unlink:confirm:${actor.id}:123e4567-e89b-42d3-a456-426614174000`);
    const intruder = component(customId, member);
    await dispatchComponent(intruder, api);
    expect(api.links.unlinkMinecraftAccount).not.toHaveBeenCalled();
    expect(intruder.replies[0].content).toContain("Only the member");
  });

  it("confirms an unlink only for its owner and calls the unlink service", async () => {
    const api = services();
    const confirmation = component(`kairu:unlink:confirm:${actor.id}:123e4567-e89b-42d3-a456-426614174000`);
    await dispatchComponent(confirmation, api);
    expect(api.links.unlinkMinecraftAccount).toHaveBeenCalledWith(actor.id, "123e4567-e89b-42d3-a456-426614174000");
    expect(confirmation.updates[0].content).toContain("has been removed");
  });

  it("rejects malformed player usernames before directory access", async () => {
    const input = interaction("player", { username: "bad name" });
    const api = services();
    await dispatchCommand(input, api);
    expect(api.players.getPlayerByUsername).not.toHaveBeenCalled();
    expect(input.replies[0].content).toContain("valid Minecraft Java username");
  });

  it("renders a safe player response with avatar and HTTPS profile button", async () => {
    const input = interaction("player", { username: "Alex" });
    const api = services();
    vi.mocked(api.players.getPlayerByUsername).mockResolvedValue({
      minecraftUuid: "123e4567-e89b-42d3-a456-426614174000",
      username: "Alex",
      rankName: "<@everyone>",
      worldName: "world",
      kills: 10,
      deaths: 2,
      blocksBroken: 100,
      playtimeSeconds: 3_600
    });
    await dispatchCommand(input, api);
    const response = input.replies[0];
    expect(response.embeds?.[0].thumbnail?.url).toBe("https://mc-heads.net/avatar/Alex/128");
    expect(response.components?.[0].components[0]).toMatchObject({ style: 5, url: "https://kairu.example/players/123e4567-e89b-42d3-a456-426614174000" });
    expect(JSON.stringify(response)).toContain("@​everyone");
  });

  it("groups online players by escaped world and never permits mentions", async () => {
    const input = interaction("online");
    const api = services();
    vi.mocked(api.status.getOnlinePlayers).mockResolvedValue([
      { username: "Alex", worldName: "world" },
      { username: "Steve", worldName: "world" },
      { username: "Builder", worldName: "@everyone" }
    ]);
    await dispatchCommand(input, api);
    const response = input.replies[0];
    expect(response.embeds?.[0].description).toContain("world");
    expect(response.embeds?.[0].description).toContain("@​everyone");
    expect(response.allowedMentions).toEqual({ parse: [] });
  });

  it("validates maintenance and announcement parameters and returns successful staff responses", async () => {
    const maintenance = interaction("maintenance", { enabled: true, reason: "Deploying patch" }, ["Administrator"]);
    const announcement = interaction("announce", { message: "Welcome, @everyone" }, ["ManageGuild"]);
    const api = services();
    await dispatchCommand(maintenance, api);
    await dispatchCommand(announcement, api);
    expect(api.control.setMaintenance).toHaveBeenCalledWith(expect.objectContaining({ enabled: true, reason: "Deploying patch" }));
    expect(api.control.announce).toHaveBeenCalledWith(expect.objectContaining({ message: "Welcome, @everyone" }));
    expect(announcement.replies[0].embeds?.[0].title).toBe("Announcement queued");
  });
});
